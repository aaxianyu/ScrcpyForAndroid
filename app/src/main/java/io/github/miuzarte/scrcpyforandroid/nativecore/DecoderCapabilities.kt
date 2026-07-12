package io.github.miuzarte.scrcpyforandroid.nativecore

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log

/**
 * Queries the local device's hardware decoder capabilities to determine whether a given
 * video size is supported before attempting to create a [MediaCodec].
 *
 * This is primarily needed for chips like MTK AC8257 whose AVC decoder caps at 2048x1088,
 * while scrcpy may send 1080x2400 (long edge 2400 > 2048), causing
 * [MediaCodec.configure] to throw [IllegalArgumentException].
 */
object DecoderCapabilities {

    private const val TAG = "DecoderCapabilities"

    /**
     * The conservative fallback max size returned by [maxSupportedSize] when the codec list
     * query fails or returns no usable information. 1920 is a safe upper bound for virtually
     * all hardware decoders on Android 10+.
     */
    private const val FALLBACK_MAX_SIZE = 1920

    /**
     * Check whether any hardware decoder on this device supports decoding [width]x[height]
     * for the given [mime] type.
     *
     * - Only decoders (not encoders) are considered.
     * - Software-only decoders are included as a last resort (some devices only have a
     *   software decoder for AV1), but hardware decoders are preferred.
     * - When [MediaCodecInfo.VideoCapabilities.areSizeSupported] is unavailable or throws,
     *   returns true (optimistic) so the caller can still attempt creation and rely on
     *   [DecoderException] for the error path.
     */
    fun isSizeSupported(mime: String, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder.not() &&
                        isCodecCapable(info, mime, width, height)
            }
        } catch (e: Exception) {
            Log.w(TAG, "isSizeSupported(): query failed for $mime ${width}x$height, assuming supported", e)
            true
        }
    }

    /**
     * Returns the largest "long edge" size (i.e. `max(width, height)`) that any decoder on
     * this device supports for [mime].
     *
     * Used by the downgrade logic to pick a new `max_size` value that the decoder can handle.
     * The probe tries common sizes from 1080 up to 4096; if none are supported (unlikely),
     * returns [FALLBACK_MAX_SIZE].
     */
    fun maxSupportedSize(mime: String): Int {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val decoders = codecList.codecInfos.filter { !it.isEncoder }

            // Try from largest to smallest; return the first that works for any decoder.
            // This avoids probing every integer — a handful of common breakpoints is enough.
            for (size in intArrayOf(3840, 2560, 2048, 1920, 1600, 1280, 1080)) {
                if (decoders.any { isCodecCapable(it, mime, size, size) }) {
                    return size
                }
            }
            FALLBACK_MAX_SIZE
        } catch (e: Exception) {
            Log.w(TAG, "maxSupportedSize(): query failed for $mime, returning fallback $FALLBACK_MAX_SIZE", e)
            FALLBACK_MAX_SIZE
        }
    }

    private fun isCodecCapable(
        info: MediaCodecInfo,
        mime: String,
        width: Int,
        height: Int,
    ): Boolean {
        return try {
            val caps = info.getCapabilitiesForType(mime) ?: return false
            caps.videoCapabilities?.isSizeSupported(width, height) == true
        } catch (e: Exception) {
            // Some codecs throw for unsupported mime types — that's fine, just skip.
            false
        }
    }
}
