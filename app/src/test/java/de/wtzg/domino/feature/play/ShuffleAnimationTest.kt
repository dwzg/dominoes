package de.wtzg.domino.feature.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShuffleAnimationTest {
    @get:Rule val rule = createComposeRule()

    private var active by mutableStateOf(false)

    @Before fun setUp() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.size(400.dp, 600.dp)) { ShuffleAnimation(active = active, yourTiles = 7, opponentTiles = listOf(7)) }
        }
    }

    private fun hiddenTiles() = rule.onAllNodesWithContentDescription("Hidden tile").fetchSemanticsNodes().size

    private fun emptyTable(value: Boolean) {
        rule.runOnUiThread { active = value }
        rule.waitForIdle()
    }

    /** Moves time on frame by frame, letting effects and recomposition catch up in between, as on a device. */
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            left -= 16
        }
    }

    @Test
    fun finishesEvenWhenTheHandIsOpenedWhileItPlays() {
        assertEquals("nothing before a hand starts", 0, hiddenTiles())

        emptyTable(true)
        advance(500)
        assertTrue("shuffling", hiddenTiles() > 0)

        // Online: someone plays the opening tile mid-shuffle, so the table is no longer empty.
        emptyTable(false)
        advance(SHUFFLE_MS + 500)
        assertEquals("the shuffle ran to the end and cleared", 0, hiddenTiles())
    }

    @Test
    fun aNewHandShufflesAgain() {
        emptyTable(true)
        advance(SHUFFLE_MS + 500)
        assertEquals(0, hiddenTiles())

        emptyTable(false)
        advance(1_000)
        emptyTable(true)
        advance(500)
        assertTrue(hiddenTiles() > 0)
        advance(SHUFFLE_MS + 500)
        assertEquals(0, hiddenTiles())
    }
}
