package de.wtzg.domino.feature.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.ui.components.customTargetFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GameSetupScreenTest {
    @get:Rule val rule = createComposeRule()

    // Only the chip: a text field inside a dialog never settles under Robolectric (a plain one
    // doesn't either), so the dialog's input is covered by customTargetFrom below.
    @Test
    fun offersACustomTargetNextToThePresets() {
        rule.setContent {
            GameSetupScreen(GameMode.DRAW, defaultCoach = true, hasGameInProgress = false, onBack = {}, onStart = { _, _ -> })
        }
        rule.onNodeWithText("60 · Standard").performScrollTo().assertExists()
        rule.onNodeWithText("Custom").performScrollTo().assertExists()
    }

    @Test
    fun customTargetsAreWholeNumbersInRange() {
        assertEquals(137, customTargetFrom("137"))
        assertEquals(10, customTargetFrom(" 10 "))
        assertEquals(1000, customTargetFrom("1000"))
        assertNull(customTargetFrom("5"))
        assertNull(customTargetFrom("1001"))
        assertNull(customTargetFrom(""))
        assertNull(customTargetFrom("abc"))
    }
}
