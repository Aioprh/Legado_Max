package io.legado.app.ui.video.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoAutoNextControllerTest {

    @Test
    fun blocksDuplicateSwitches() {
        val controller = VideoAutoNextController()

        assertTrue(controller.shouldAutoNext(hasNext = true))
        assertFalse(controller.shouldAutoNext(hasNext = true))

        controller.finishSwitch()
        assertTrue(controller.shouldAutoNext(hasNext = true))
    }

    @Test
    fun respectsEnabledStateAndLastEpisode() {
        val disabled = VideoAutoNextController { false }
        assertFalse(disabled.shouldAutoNext(hasNext = true))

        val controller = VideoAutoNextController()
        assertFalse(controller.shouldAutoNext(hasNext = false))
    }
}
