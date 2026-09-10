package io.legado.app.ui.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMediaResolverTest {

    @Test
    fun detectsHlsFromPathAndQuery() {
        assertEquals(
            VideoMediaResolver.StreamType.HLS,
            VideoMediaResolver.detectType("https://example.com/api/play.m3u8?token=1")
        )
        assertEquals(
            VideoMediaResolver.StreamType.HLS,
            VideoMediaResolver.detectType("https://example.com/play?format=m3u8")
        )
    }

    @Test
    fun detectsDashFromPathAndQuery() {
        assertEquals(
            VideoMediaResolver.StreamType.DASH,
            VideoMediaResolver.detectType("https://example.com/manifest.mpd")
        )
        assertEquals(
            VideoMediaResolver.StreamType.DASH,
            VideoMediaResolver.detectType("https://example.com/play?type=mpd")
        )
    }

    @Test
    fun progressiveUrlsDoNotGetAdaptiveOverride() {
        val resolved = VideoMediaResolver.resolve("https://example.com/video.mp4")!!
        assertEquals(VideoMediaResolver.StreamType.PROGRESSIVE, resolved.type)
        assertEquals(null, resolved.overrideExtension)
        assertTrue(!VideoMediaResolver.isAdaptive(resolved.url))
    }

    @Test
    fun episodeControllerAdvancesInOrder() {
        val controller = VideoEpisodeController(listOf("1", "2", "3"))
        assertEquals("1", controller.current)
        assertEquals("2", controller.next())
        assertEquals("3", controller.next())
        assertEquals(null, controller.next())
        assertEquals("2", controller.previous())
    }
}
