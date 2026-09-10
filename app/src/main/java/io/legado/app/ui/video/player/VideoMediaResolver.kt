package io.legado.app.ui.video.player

import android.net.Uri

/**
 * Video media URL normalization and stream-type inference.
 *
 * Keeps stream detection independent from the UI/player implementation so book/RSS
 * sources can use the same rules. This is intentionally conservative: only an
 * explicit HLS/DASH marker changes the extension; ordinary URLs remain untouched.
 */
object VideoMediaResolver {

    enum class StreamType {
        HLS,
        DASH,
        PROGRESSIVE,
        UNKNOWN
    }

    data class ResolvedMedia(
        val url: String,
        val type: StreamType,
        val overrideExtension: String? = null
    )

    fun resolve(url: String?): ResolvedMedia? {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return null

        val type = detectType(value)
        return ResolvedMedia(
            url = value,
            type = type,
            overrideExtension = when (type) {
                StreamType.HLS -> "m3u8"
                StreamType.DASH -> "mpd"
                else -> null
            }
        )
    }

    fun detectType(url: String): StreamType {
        val lower = url.lowercase()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val path = uri?.path.orEmpty().lowercase()
        val query = uri?.query.orEmpty().lowercase()

        return when {
            path.endsWith(".m3u8") || lower.contains(".m3u8") || query.contains("m3u8") -> StreamType.HLS
            path.endsWith(".mpd") || lower.contains(".mpd") || query.contains("mpd") -> StreamType.DASH
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") ||
                path.endsWith(".mov") || path.endsWith(".ts") -> StreamType.PROGRESSIVE
            else -> StreamType.UNKNOWN
        }
    }

    fun isAdaptive(url: String?): Boolean {
        return when (url?.let(::detectType)) {
            StreamType.HLS, StreamType.DASH -> true
            else -> false
        }
    }
}
