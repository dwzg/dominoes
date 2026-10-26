package de.wtzg.domino.feature.online

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.wtzg.domino.protocol.Friend
import de.wtzg.domino.protocol.FriendsView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FriendsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : FriendsActions {
        override fun refresh() { calls += "refresh" }
        override fun add(code: String) { calls += "add $code" }
        override fun addPlayer(playerId: String) { calls += "addPlayer $playerId" }
        override fun accept(playerId: String) { calls += "accept $playerId" }
        override fun remove(playerId: String) { calls += "remove $playerId" }
        override fun dismissError() = Unit
        override fun dismissMessage() = Unit
    }

    private val page = FriendsView(
        friendCode = "ABCD2345",
        friends = listOf(Friend("bob-id", "Bob")),
        requests = listOf(Friend("cat-id", "Cat")),
        recent = listOf(Friend("dan-id", "Dan")),
    )

    @Test
    fun showsYourCodeAndActsOnFriends() {
        var challenged: Friend? = null
        rule.setContent {
            FriendsScreen(
                state = FriendsState(friends = page, serverUrl = "https://dominoes.example.com", available = true),
                vm = actions,
                initialCode = null,
                onBack = {},
                onChallenge = { challenged = it },
                onSetUp = {},
            )
        }
        rule.onNodeWithText("ABCD-2345").assertIsDisplayed()

        rule.onNodeWithText("Accept").performScrollTo().performClick()
        rule.onNodeWithText("Challenge").performScrollTo().performClick()
        rule.onNodeWithText("Add friend").performScrollTo().performClick()
        assertEquals(listOf("accept cat-id", "addPlayer dan-id"), calls)
        assertEquals("Bob", challenged?.name)
    }

    @Test
    fun aFriendLinkFillsInTheCode() {
        rule.setContent {
            FriendsScreen(
                state = FriendsState(friends = page.copy(friends = emptyList()), available = true),
                vm = actions,
                initialCode = "wxyz6789",
                onBack = {},
                onChallenge = {},
                onSetUp = {},
            )
        }
        rule.onNodeWithText("WXYZ-6789").assertIsDisplayed()
        rule.onNodeWithText("Add").performClick()
        assertEquals(listOf("add WXYZ-6789"), calls)
    }
}
