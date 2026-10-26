package de.wtzg.domino.feature.online

import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.Friend
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameInvite
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.Pace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticePlanTest {
    private fun game(id: String, version: Int, yourTurn: Boolean) = GameSummary(
        id = id, code = "ABC234", status = GameStatus.PLAYING, pace = Pace.TURN_BASED, mode = GameMode.DRAW,
        seats = listOf("You", "Bob"), scores = listOf(0, 0), yourSeat = 0, turn = if (yourTurn) 0 else 1,
        winner = null, version = version, updatedAt = 0,
    )

    private val invite = GameInvite("g9", "XYZ789", "Cat", GameMode.ALL_FIVES, 100, Pace.LIVE, 2, 1, sentAt = 0)
    private val friends = FriendsView("CODE2345", requests = listOf(Friend("dan", "Dan")), invites = listOf(invite))

    @Test
    fun announcesEachTurnInviteAndRequestOnce() {
        val first = planNotices(listOf(game("g1", 3, yourTurn = true), game("g2", 5, yourTurn = false)), friends, emptyMap(), foreground = false)
        assertEquals(
            listOf("Your turn in Draw", "Cat invites you to All Fives", "Dan wants to be your friend"),
            first.notices.map { it.title },
        )
        assertEquals(listOf("dominoes://game/g1", "dominoes://online", "dominoes://friends"), first.notices.map { it.link })

        // Nothing new since: nothing to say.
        val again = planNotices(listOf(game("g1", 3, yourTurn = true)), friends, first.seen, foreground = false)
        assertTrue(again.notices.isEmpty())

        // Your turn again after more moves: say so again.
        val later = planNotices(listOf(game("g1", 7, yourTurn = true)), friends, again.seen, foreground = false)
        assertEquals(listOf("g1"), later.notices.map { it.key })
    }

    @Test
    fun staysQuietWhileTheAppIsOpenButRemembers() {
        val plan = planNotices(listOf(game("g1", 3, yourTurn = true)), friends, emptyMap(), foreground = true)
        assertTrue(plan.notices.isEmpty())
        assertTrue(planNotices(listOf(game("g1", 3, yourTurn = true)), friends, plan.seen, foreground = false).notices.isEmpty())
    }

    @Test
    fun keepsWhatItKnewWhenFriendsCantBeChecked() {
        val seen = planNotices(emptyList(), friends, emptyMap(), foreground = false).seen
        val offline = planNotices(emptyList(), null, seen, foreground = false)
        assertTrue(offline.notices.isEmpty())
        // Back online with the same invite and request: still not announced twice.
        assertTrue(planNotices(emptyList(), friends, offline.seen, foreground = false).notices.isEmpty())
        // Answered ones are forgotten.
        assertTrue(planNotices(emptyList(), FriendsView("CODE2345"), offline.seen, foreground = false).seen.isEmpty())
    }
}
