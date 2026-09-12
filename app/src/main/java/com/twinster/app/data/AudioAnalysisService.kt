package com.twinster.app.data

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import com.twinster.app.domain.AudioGenreTaxonomy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the actual audio decode ([AudioContentDecoder]) + YAMNet classification
 * ([YamnetClassifier]) work in an isolated process (see the `android:process=":audio_analysis"`
 * entry in AndroidManifest.xml) instead of the main app process. TFLite's C++ runtime can fault on
 * a malformed/adversarial input in a way no JVM `try`/`catch` — however broad — can intercept,
 * since a native crash kills the whole process immediately. Isolating that work here means such a
 * crash takes down only this process; [LocalLibraryScanner]/[AudioAnalysisClient] observe it as
 * `onServiceDisconnected` and degrade that one track to "content analysis unavailable", the same
 * way any other per-track decode/inference failure already degrades.
 *
 * Runs under the app's own UID (the `:` prefix makes this a separate *process* within the same
 * app, not a separate app), so it can read the same `content://` MediaStore audio URIs the main
 * process can, with no extra permission grant needed.
 *
 * Only a URI goes over the Binder IPC boundary in the request, and only a small genre list comes
 * back in the reply — never raw decoded PCM, which would be both unnecessarily large for Binder's
 * ~1MB transaction ceiling and pointless to ship across processes when this service can just
 * decode the file itself.
 */
class AudioAnalysisService : Service() {

    private var classifier: YamnetClassifier? = null

    // TFLite's Task Library AudioClassifier is not documented/guaranteed safe for concurrent
    // classify() calls from multiple threads against the same interpreter instance — exactly the
    // kind of native-runtime hazard this whole isolated process exists to contain. Decode (below)
    // is genuinely safe to run concurrently since each request gets its own MediaExtractor/MediaCodec,
    // so only the actual model inference is serialized here; that keeps the concurrency fix from
    // trading "one wedged track blocks everything" for "two concurrent inferences corrupt the model".
    private val classifierLock = Any()

    // Bounded to 2 concurrent analyses. This was briefly raised to 3 for speed, then reverted:
    // MediaCodec hardware decoder instances are a scarce, system-wide-shared resource on Android
    // (often as few as 4-8 total across the whole device, shared with every other app), and running
    // more concurrent MediaExtractor/MediaCodec decodes than necessary is a well-documented real
    // source of instability, not just a performance knob — a plausible contributor to this process's
    // native crashes. Only decode runs in parallel across workers at all — inference itself is still
    // serialized via classifierLock below, since TFLite's AudioClassifier isn't documented safe for
    // concurrent classify() calls.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val workerDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(workerDispatcher + serviceJob)

    private val incomingMessenger = Messenger(Handler(Handler.Callback { msg ->
        // Nothing was guarding this dispatch itself — a trivial exception here (a malformed Bundle,
        // a bad Uri string) would crash this process's main thread outright, which Android reports
        // with its own system "app has stopped" dialog. That's a real, avoidable gap distinct from
        // the (much harder to fully rule out) native TFLite crash path this whole isolated-process
        // design exists to contain — this one is a plain, catchable exception, so catch it.
        try {
            when (msg.what) {
                AudioAnalysisProtocol.MSG_ANALYZE -> handleAnalyzeRequest(msg)
                AudioAnalysisProtocol.MSG_HELLO -> handleHelloRequest(msg)
            }
        } catch (_: Throwable) {
        }
        true
    }))

    override fun onCreate() {
        super.onCreate()
        // Belt-and-suspenders: if some other still-uncaught exception reaches this process's default
        // handler anyway, swallow it quietly instead of letting Android's system crash-report UI
        // surface an "app has stopped" dialog for what's really just an internal, already-contained
        // failure in a background analysis process the user never directly interacts with. This can
        // only intercept ordinary JVM exceptions — an actual native/JNI-level fault bypasses Java's
        // exception handling entirely and still ends the process (which AudioAnalysisClient already
        // detects via onServiceDisconnected and recovers from), so this doesn't change that behavior,
        // it only removes the misleading system dialog for the cases it CAN catch.
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                android.util.Log.e("AudioAnalysisService", "Suppressed uncaught exception in isolated analysis process", throwable)
            } catch (_: Throwable) {
            }
            Process.killProcess(Process.myPid())
        }
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    /** Cheap enough to answer directly on the Handler thread — no need to route through the worker
     *  pool used for actual analysis. */
    private fun handleHelloRequest(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val reply = Message.obtain(null, AudioAnalysisProtocol.MSG_PID).apply {
            data = Bundle().apply { putInt(AudioAnalysisProtocol.KEY_PID, Process.myPid()) }
        }
        try {
            replyTo.send(reply)
        } catch (_: RemoteException) {
        }
    }

    // Dispatching onto serviceScope (workerDispatcher, limitedParallelism(2)) here means the
    // Handler thread that actually receives Binder messages never blocks on decode/classify work —
    // it just launches a coroutine and immediately returns to accept the next incoming message. A
    // request stuck past AudioContentDecoder's own decode-loop cap only occupies one of the 2 worker
    // slots, not the message-receiving thread itself, so it can no longer wedge every later request.
    private fun handleAnalyzeRequest(msg: Message) {
        val replyTo = msg.replyTo ?: return // No way to reply — nothing useful to do.
        val requestId = msg.data?.getLong(AudioAnalysisProtocol.KEY_REQUEST_ID, -1L) ?: -1L
        val uri = msg.data?.getString(AudioAnalysisProtocol.KEY_URI)?.let { Uri.parse(it) }

        serviceScope.launch {
            val genres = if (uri != null) analyzeOne(uri) else null
            sendReply(replyTo, requestId, genres)
        }
    }

    /** Same graceful-degradation contract the in-process pipeline always had: any decode/model/
     *  inference failure (including native Throwable subtypes like OutOfMemoryError) yields null
     *  rather than propagating. A truly fatal native fault bypasses this catch entirely and kills
     *  this process — which is exactly the failure this service exists to contain. */
    private suspend fun analyzeOne(uri: Uri): List<String>? {
        return try {
            val yamnet = getOrCreateClassifier() ?: return null
            val pcm = withContext(Dispatchers.IO) { AudioContentDecoder.decodeClipToMono16k(applicationContext, uri) }
            val scores = synchronized(classifierLock) { yamnet.classifyAveraged(pcm) }
            AudioGenreTaxonomy.detectGenres(scores)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /** Loaded once on first request and reused across the service's lifetime, same as the
     *  in-process code used to reuse it across a scan (model load is the expensive part). */
    @Synchronized
    private fun getOrCreateClassifier(): YamnetClassifier? {
        classifier?.let { return it }
        return YamnetClassifier.createOrNull(applicationContext).also { classifier = it }
    }

    private fun sendReply(replyTo: Messenger, requestId: Long, genres: List<String>?) {
        val reply = Message.obtain(null, AudioAnalysisProtocol.MSG_RESULT).apply {
            data = Bundle().apply {
                putLong(AudioAnalysisProtocol.KEY_REQUEST_ID, requestId)
                putBoolean(AudioAnalysisProtocol.KEY_SUCCESS, genres != null)
                if (genres != null) putStringArrayList(AudioAnalysisProtocol.KEY_GENRES, ArrayList(genres))
            }
        }
        try {
            replyTo.send(reply)
        } catch (_: RemoteException) {
            // Client process/binder is already gone — nothing left to deliver the reply to.
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        classifier?.close()
        classifier = null
        return false
    }

    override fun onDestroy() {
        serviceJob.cancel()
        classifier?.close()
        classifier = null
        super.onDestroy()
    }
}
