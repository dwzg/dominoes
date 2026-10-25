package de.wtzg.domino.feature.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShuffleAnimationTest {
    @get:Rule val rule = createComposeRule()

    private fun hiddenTiles() = rule.onAllNodesWithContentDescription("Hidden tile").fetchSemanticsNodes().size

    @Test
    fun finishesEvenWhenTheHandIsOpenedWhileItPlays() {
        var active by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent { ShuffleAnimation(active = active, yourTiles = 7, opponentTiles = listOf(7)) }
        assertEquals("nothing before a hand starts", 0, hiddenTiles())

        rule.runOnUiThread { active = true }
        rule.mainClock.advanceTimeBy(500)
        assertTrue("shuffling", hiddenTiles() > 0)

        // Online: someone plays the opening tile mid-shuffle, so the table is no longer empty.
        rule.runOnUiThread { active = false }
        rule.mainClock.advanceTimeBy(SHUFFLE_MS + 500)
        assertEquals("the shuffle ran to the end and cleared", 0, hiddenTiles())
    }

    @Test
    fun aNewHandShufflesAgain() {
        var active by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent { ShuffleAnimation(active = active, yourTiles = 7, opponentTiles = listOf(7)) }
        rule.mainClock.advanceTimeBy(SHUFFLE_MS + 500)
        assertEquals(0, hiddenTiles())

        rule.runOnUiThread { active = false }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnUiThread { active = true }
        rule.mainClock.advanceTimeBy(500)
        assertTrue(hiddenTiles() > 0)
        rule.mainClock.advanceTimeBy(SHUFFLE_MS + 500)
        assertEquals(0, hiddenTiles())
    }
}
