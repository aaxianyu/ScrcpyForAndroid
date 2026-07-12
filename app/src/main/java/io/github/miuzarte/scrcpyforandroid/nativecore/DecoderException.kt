package io.github.miuzarte.scrcpyforandroid.nativecore

/**
 * Thrown when a [MediaCodec] decoder fails to initialize or enters an unrecoverable error state.
 *
 * Carries the MIME type and target dimensions so callers (e.g. [VideoDecoderController])
 * can decide whether to downgrade the video size and restart the session.
 */
class DecoderException(
    val mime: String,
    val width: Int,
    val height: Int,
    cause: Throwable? = null,
): RuntimeException(
    "Decoder failed for mime=$mime size=${width}x${height}",
    cause,
)
