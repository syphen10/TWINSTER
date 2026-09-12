package com.twinster.app.data

/** Shared Messenger-protocol constants between [AudioAnalysisService] (running in the isolated
 *  `:audio_analysis` process) and [AudioAnalysisClient] (running in the main app process). Kept as
 *  plain `what`/Bundle-key constants rather than AIDL/Parcelable payloads — the request is just a
 *  URI string and the reply is just a success flag plus a small string list, so a full IPC schema
 *  would be more machinery than the data warrants. */
object AudioAnalysisProtocol {
    /** Client -> service: analyze the track at [KEY_URI], tagged with [KEY_REQUEST_ID].
     *  `msg.replyTo` must be set. Multiple requests may be in flight concurrently — the service
     *  processes them on a bounded worker pool rather than one at a time, so replies can arrive out
     *  of order, which is exactly why every request/reply pair carries a matching request id. */
    const val MSG_ANALYZE = 1

    /** Service -> client: reply to a [MSG_ANALYZE] request, echoing back its [KEY_REQUEST_ID]. */
    const val MSG_RESULT = 2

    /** Client -> service: handshake sent right after binding, before any [MSG_ANALYZE] — asks the
     *  service to report its own process id so the client can later force-kill a wedged process by
     *  PID (see AudioAnalysisClient's timeout-recovery logic). `msg.replyTo` must be set. */
    const val MSG_HELLO = 3

    /** Service -> client: reply to [MSG_HELLO] carrying [KEY_PID]. */
    const val MSG_PID = 4

    /** String extra: the track's `content://` MediaStore URI, as sent to [MSG_ANALYZE]. */
    const val KEY_URI = "uri"

    /** Long extra on both [MSG_ANALYZE] and its [MSG_RESULT] reply: matches a reply back to the
     *  specific in-flight request that produced it, since the service may now handle several
     *  requests concurrently and reply out of order. */
    const val KEY_REQUEST_ID = "requestId"

    /** Boolean extra on [MSG_RESULT]: false for any failure (decode/classify/model-load), in which
     *  case [KEY_GENRES] is absent and the caller should treat this exactly like any other
     *  per-track content-analysis failure. */
    const val KEY_SUCCESS = "success"

    /** String ArrayList extra on [MSG_RESULT]: the detected genres (possibly empty) when
     *  [KEY_SUCCESS] is true. */
    const val KEY_GENRES = "genres"

    /** Int extra on [MSG_PID]: this service process's `android.os.Process.myPid()`. */
    const val KEY_PID = "pid"
}
