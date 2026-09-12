package com.twinster.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Main-process bridge to [AudioAnalysisService]. One instance is created per Local Library scan
 * (see [LocalLibraryScanner]) and reused across every sampled track so the remote YAMNet
 * classifier stays warm in the isolated `:audio_analysis` process — mirroring how a single
 * [YamnetClassifier] used to be reused in-process before this work was relocated.
 *
 * [analyzeTrack] is safe to call concurrently from multiple coroutines against the same client
 * instance — the service now handles several requests at once (see [AudioAnalysisService]), and
 * [LocalLibraryScanner] fires a small batch of tracks at a time rather than awaiting each one
 * sequentially. Every request/reply pair is tagged with a request id (see
 * [AudioAnalysisProtocol.KEY_REQUEST_ID]) precisely because replies can now arrive out of order.
 *
 * [analyzeTrack] collapses every failure mode into the same "skip this track's content analysis"
 * null result that the pipeline already handled gracefully before this change:
 *  - an explicit failure reply from the service (decode/model/inference failure there),
 *  - no reply within [analyzeTrack]'s timeout (remote process hung rather than crashed),
 *  - [ServiceConnection.onServiceDisconnected] firing — the remote process actually died (e.g. a
 *    native TFLite fault). That handler drops the stale binder and unbinds, so the *next*
 *    [analyzeTrack] call transparently rebinds, which restarts the isolated service process
 *    fresh — one crashed track costs only its own content signal, not the rest of the scan's
 *    coverage.
 *
 * A timeout with genuinely no reply (as opposed to an explicit failure reply, which just means the
 * track itself failed to decode/classify) is a sign the remote process might be *wedged* rather
 * than crashed — a hang that [ServiceConnection.onServiceDisconnected] will never report, since
 * that callback only fires for an actual process death. Two such timeouts in a row are treated as
 * confirmation, not coincidence: [recoverFromWedgedService] force-kills the remote process by PID
 * (learned via a [AudioAnalysisProtocol.MSG_HELLO] handshake right after each bind) and drops the
 * connection so the next call rebinds a fresh process, rather than continuing to trust one that may
 * never reply again.
 *
 * Binding a service and awaiting a Messenger reply doesn't fit `suspend`/`await` natively, so both
 * "wait for onServiceConnected" and "wait for a reply Message" are wrapped in
 * [suspendCancellableCoroutine], resumed from whichever callback fires first and cleaned up via
 * `invokeOnCancellation` if the calling coroutine is itself cancelled (e.g. the whole scan is
 * cancelled mid-track).
 */
class AudioAnalysisClient(private val appContext: Context) {

    private var messenger: Messenger? = null
    private var servicePid: Int? = null

    // Only one bind attempt (and its immediately-following PID handshake) should ever be in flight
    // at a time even though several analyzeTrack() calls may race into ensureBound() concurrently —
    // this serializes that without blocking callers who arrive after a bind has already finished.
    private val bindMutex = Mutex()

    private val requestIdCounter = AtomicLong(0)
    private val pendingResults = ConcurrentHashMap<Long, (List<String>?) -> Unit>()

    // Consecutive timeouts-with-no-reply-at-all across this client instance (a scan reuses one
    // instance across every sampled track). Reset on any actual reply (success or explicit
    // failure) or on a clean rebind; only a run of genuine silence trips recovery.
    private val consecutiveTimeouts = AtomicInteger(0)
    private val recovering = AtomicBoolean(false)

    private var connectContinuation: ((Boolean) -> Unit)? = null
    private var helloContinuation: ((Int?) -> Unit)? = null

    private val replyThread = HandlerThread("AudioAnalysisReply").apply { start() }
    private val replyMessenger = Messenger(Handler(replyThread.looper) { msg ->
        when (msg.what) {
            AudioAnalysisProtocol.MSG_RESULT -> {
                val data = msg.data
                val requestId = data?.getLong(AudioAnalysisProtocol.KEY_REQUEST_ID, -1L) ?: -1L
                val success = data?.getBoolean(AudioAnalysisProtocol.KEY_SUCCESS, false) == true
                val genres = if (success) (data?.getStringArrayList(AudioAnalysisProtocol.KEY_GENRES) ?: arrayListOf()) else null
                if (requestId >= 0) completeResult(requestId, genres)
            }
            AudioAnalysisProtocol.MSG_PID -> {
                val pid = msg.data?.getInt(AudioAnalysisProtocol.KEY_PID, -1) ?: -1
                completeHello(if (pid > 0) pid else null)
            }
        }
        true
    })

    // lateinit rather than a plain `val` initializer: onServiceDisconnected below needs to refer to
    // `connection` itself (to unbind the stale connection), which a self-referencing initializer
    // expression can't do. By the time any ServiceConnection callback can fire, `connection` has
    // already been assigned in the init block below.
    private lateinit var connection: ServiceConnection

    init {
        connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            messenger = Messenger(binder)
            completeConnect(true)
        }

        /** Fires specifically when the bound service's *process* has died unexpectedly (a crash) —
         *  not on a normal unbind. Fail anything in flight immediately rather than waiting out a
         *  full timeout, and drop + unbind the stale connection so the next [analyzeTrack] call's
         *  [ensureBound] rebinds from scratch, restarting the service process. */
        override fun onServiceDisconnected(name: ComponentName) {
            messenger = null
            servicePid = null
            completeConnect(false)
            completeHello(null)
            failAllPending()
            try {
                appContext.unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Already unbound — fine, this is just best-effort cleanup.
            }
        }
        }
    }

    @Synchronized
    private fun completeConnect(success: Boolean) {
        val cont = connectContinuation
        connectContinuation = null
        cont?.invoke(success)
    }

    @Synchronized
    private fun completeHello(pid: Int?) {
        val cont = helloContinuation
        helloContinuation = null
        cont?.invoke(pid)
    }

    private fun completeResult(requestId: Long, genres: List<String>?) {
        pendingResults.remove(requestId)?.invoke(genres)
    }

    private fun failAllPending() {
        val ids = pendingResults.keys.toList()
        ids.forEach { id -> pendingResults.remove(id)?.invoke(null) }
    }

    private suspend fun ensureBound(): Boolean {
        if (messenger != null) return true
        return bindMutex.withLock {
            if (messenger != null) return@withLock true
            // This wait previously had NO timeout at all: if bindService() returns true (request
            // accepted) but onServiceConnected never actually fires — which can genuinely happen
            // under system resource pressure or a process-start failure that doesn't cleanly surface
            // as onServiceDisconnected — this coroutine hung forever. Because every analyzeTrack()
            // call routes through this same bindMutex-guarded function, ONE stuck bind permanently
            // froze the entire scan, with none of analyzeTrack()'s own 9s-timeout/recovery logic ever
            // getting a chance to run (it's all downstream of this call returning). Wrapping this in
            // withTimeoutOrNull both bounds the wait and — since withLock releases in a finally
            // regardless of how the block completes — cleanly frees the mutex for the next attempt.
            val connected = withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine { cont ->
                    synchronized(this) { connectContinuation = { ok -> if (cont.isActive) cont.resume(ok) } }
                    val bound = try {
                        appContext.bindService(Intent(appContext, AudioAnalysisService::class.java), connection, Context.BIND_AUTO_CREATE)
                    } catch (_: Exception) {
                        false
                    }
                    if (!bound) {
                        synchronized(this) { connectContinuation = null }
                        cont.resume(false)
                    }
                    cont.invokeOnCancellation {
                        synchronized(this) { connectContinuation = null }
                    }
                }
            } ?: false
            if (connected) handshakeForPid()
            connected
        }
    }

    /** Learns the freshly-bound service's PID so a later hang can be force-killed by PID rather than
     *  relied on to eventually crash or disconnect on its own. Best-effort: if this handshake itself
     *  times out, [servicePid] just stays null and recovery below falls back to unbind/rebind only
     *  (still correct, just without the explicit kill). */
    private suspend fun handshakeForPid() {
        val sender = messenger ?: return
        val pid = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<Int?> { cont ->
                synchronized(this) { helloContinuation = { pid -> if (cont.isActive) cont.resume(pid) } }
                try {
                    val request = Message.obtain(null, AudioAnalysisProtocol.MSG_HELLO).apply {
                        replyTo = replyMessenger
                    }
                    sender.send(request)
                } catch (_: RemoteException) {
                    synchronized(this) { helloContinuation = null }
                    cont.resume(null)
                }
                cont.invokeOnCancellation {
                    synchronized(this) { helloContinuation = null }
                }
            }
        }
        servicePid = pid
    }

    /** Called after 2 consecutive [analyzeTrack] timeouts with zero replies — evidence the remote
     *  process's worker pool is wedged (e.g. some hang not caught by AudioContentDecoder's own
     *  decode-loop cap) rather than merely slow or individually failing tracks.
     *  [ServiceConnection.onServiceDisconnected] only fires for an actual process death, never for a
     *  hang, so this force-kills the process directly by PID — legal and effective for another
     *  process under the same app UID — then drops the stale binder so the next [ensureBound]
     *  rebinds a fresh process. Guarded by [recovering] since multiple concurrent [analyzeTrack]
     *  calls could all cross the threshold around the same moment. */
    private fun recoverFromWedgedService() {
        if (!recovering.compareAndSet(false, true)) return
        try {
            consecutiveTimeouts.set(0)
            val pid = servicePid
            servicePid = null
            messenger = null
            if (pid != null) {
                try {
                    Process.killProcess(pid)
                } catch (_: Exception) {
                }
            }
            try {
                appContext.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
            failAllPending()
        } finally {
            recovering.set(false)
        }
    }

    /** Sends [uri] to the isolated service for decode+classify and awaits its reply, up to
     *  [timeoutMillis]. Returns null for every failure mode described in the class doc. Safe to call
     *  concurrently from multiple coroutines against the same client instance. */
    suspend fun analyzeTrack(uri: Uri, timeoutMillis: Long = 9_000L): List<String>? {
        if (!ensureBound()) return null
        val sender = messenger ?: return null
        val requestId = requestIdCounter.incrementAndGet()
        val receivedReply = AtomicBoolean(false)

        val result = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<List<String>?> { cont ->
                pendingResults[requestId] = { genres ->
                    receivedReply.set(true)
                    if (cont.isActive) cont.resume(genres)
                }
                try {
                    val request = Message.obtain(null, AudioAnalysisProtocol.MSG_ANALYZE).apply {
                        replyTo = replyMessenger
                        data = Bundle().apply {
                            putString(AudioAnalysisProtocol.KEY_URI, uri.toString())
                            putLong(AudioAnalysisProtocol.KEY_REQUEST_ID, requestId)
                        }
                    }
                    sender.send(request)
                } catch (_: RemoteException) {
                    // Rare race: binder died between ensureBound() succeeding and this send.
                    pendingResults.remove(requestId)
                    cont.resume(null)
                }
                cont.invokeOnCancellation {
                    pendingResults.remove(requestId)
                }
            }
        }

        if (!receivedReply.get()) {
            // Zero replies at all — a real timeout (as opposed to an explicit failure/disconnect
            // reply, both of which set receivedReply and are not evidence of a wedged process).
            pendingResults.remove(requestId)
            if (consecutiveTimeouts.incrementAndGet() >= 2) recoverFromWedgedService()
        } else {
            consecutiveTimeouts.set(0)
        }
        return result
    }

    /** Unbinds at the end of a scan (whether it finished or was cancelled) — mirrors the old
     *  `classifier.close()` cleanup in a `finally` block. */
    fun close() {
        try {
            appContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // Not currently bound (e.g. the service already crashed and was cleaned up) — fine.
        }
        messenger = null
        replyThread.quitSafely()
    }
}
