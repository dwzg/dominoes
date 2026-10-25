package de.wtzg.domino.feature.online

import de.wtzg.domino.engine.Board
import de.wtzg.domino.feature.play.GameViewModel
import de.wtzg.domino.feature.play.OverlayUi
import de.wtzg.domino.feature.play.ResultLine
import de.wtzg.domino.feature.play.SeatUi
import de.wtzg.domino.feature.play.TableUi
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.Pace
import de.wtzg.domino.protocol.PublicEvent
import de.wtzg.domino.protocol.canDraw
import de.wtzg.domino.protocol.legalMoves
import de.wtzg.domino.protocol.mustPass

/** The seat's name, with "You" for yourself. */
private fun GameView.nameOf(seat: Int): String = if (seat == yourSeat) "You" else seats[seat].name

/** Time left as people say it: "0:42", "12 min", "5 h", "2 days". */
fun timeLeft(ms: Long): String {
    val s = (ms + 999) / 1000
    return when {
        s < 10 * 60 -> String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
        s < 2 * 60 * 60 -> "${s / 60} min"
        s < 2 * 24 * 60 * 60 -> "${s / 3600} h"
        else -> "${s / 86_400} days"
    }
}

/** How long the player to move has left at [now], or null with no time limit. */
fun OnlineGameState.turnLeftMs(now: Long): Long? {
    val v = view ?: return null
    val sent = v.turnEndsInMs ?: return null
    return (sent - (now - viewAt)).coerceAtLeast(0)
}

/** What the game screen shows for an online game at [now] (for the turn clock). Null in the lobby. */
fun OnlineGameState.toTable(now: Long = System.currentTimeMillis()): TableUi? {
    val v = view ?: return null
    if (v.status == GameStatus.LOBBY) return null
    val you = v.yourSeat
    val n = v.seats.size
    val last = v.lastHand
    // Right after a hand ends, show its final table and hands under the summary.
    val showLast = last != null && last.handNumber > ackedHand && v.winner == null
    val order = (1 until n).map { (you + it) % n }
    val yourTurn = v.isYourTurn && !showLast && !busy

    val clock = turnLeftMs(now)?.let { " ${timeLeft(it)} left." }.orEmpty()
    val status = when {
        v.status == GameStatus.FINISHED || showLast -> ""
        busy -> "Sending…"
        yourTurn && selected != null -> "$selected fits more than one end. Tap a glowing spot."
        yourTurn && v.canDraw -> "Nothing fits ${v.board.openValues.distinct().joinToString(" or ")}. Draw a tile.$clock"
        yourTurn && v.mustPass -> "Nothing fits. You have to pass.$clock"
        yourTurn -> "Your turn. Tap a tile.$clock"
        else -> {
            val s = v.seats[v.turn]
            "Waiting for ${s.name}.$clock" + if (v.pace == Pace.TURN_BASED && !s.online && !s.isAi) " We'll notify you when it's your turn." else ""
        }
    }

    return TableUi(
        title = "${v.rules.mode.title} · to ${v.rules.target}",
        board = if (showLast) last!!.board else v.board,
        hand = if (showLast) last!!.hands[you] else v.hand,
        legal = if (yourTurn) v.legalMoves else emptyList(),
        yourTurn = yourTurn,
        canDraw = yourTurn && v.canDraw,
        mustPass = yourTurn && v.mustPass,
        yourScore = if (showLast) last!!.scores[you] else v.seats[you].score,
        target = v.rules.target,
        opponents = order.map { i ->
            val s = v.seats[i]
            SeatUi(
                name = s.name,
                score = if (showLast) last!!.scores[i] else s.score,
                tiles = if (showLast) last!!.hands[i].size else s.tiles,
                revealed = if (showLast) last!!.hands[i] else null,
                active = !showLast && v.status == GameStatus.PLAYING && v.turn == i,
                thinking = !showLast && v.status == GameStatus.PLAYING && v.turn == i && s.online,
                away = !s.isAi && !s.online,
            )
        },
        boneyard = if (v.rules.mode.drawsFromBoneyard) v.boneyard else null,
        status = status,
        showHint = false,
        selected = selected,
        log = if (showLast) emptyList() else v.recentLog(),
        lastPlaced = v.events.filterIsInstance<PublicEvent.Played>().lastOrNull()?.move?.tile,
        lastPlacedByOpponent = v.events.filterIsInstance<PublicEvent.Played>().lastOrNull()?.seat != you,
        badge = badge,
        overlay = when {
            v.status == GameStatus.FINISHED -> matchOver(v, rematch)
            showLast -> handOver(v)
            else -> null
        },
    )
}

private fun matchOver(v: GameView, rematch: Boolean): OverlayUi {
    val w = v.winner
    val out = v.timedOut
    val left = v.leftSeat
    return OverlayUi(
        title = when {
            out == v.yourSeat -> "You ran out of time."
            out != null -> "${v.seats[out].name} ran out of time."
            left != null -> "${v.seats[left].name} left the game."
            w == null -> "The match is over."
            w == v.yourSeat -> "You win the match!"
            else -> "${v.seats[w].name} wins the match."
        },
        lines = if (out != null || left != null) listOf("The game ended there, so nobody wins. These were the scores.") else emptyList(),
        scores = v.seats.indices.map { ResultLine(v.nameOf(it), null, v.seats[it].score, it == v.yourSeat) },
        // Only people still at the table can play again.
        primary = when {
            !rematch || v.yourSeat < 0 -> null
            v.rematchId != null -> "Join rematch"
            else -> "Rematch"
        },
        secondary = "Back to games",
    )
}

private fun handOver(v: GameView): OverlayUi {
    val last = v.lastHand!!
    val res = last.result
    val winner = res.winner
    val title = when (winner) {
        null -> "Blocked. Nobody scores."
        v.yourSeat -> if (res.blocked) "Blocked. You win the hand!" else "Domino! You win the hand."
        else -> "${v.seats[winner].name} ${if (res.blocked) "wins the blocked hand." else "went out."}"
    }
    val lines = buildList {
        if (winner != null) {
            val who = if (winner == v.yourSeat) "You score" else "${v.seats[winner].name} scores"
            add("$who ${res.endPoints} for the pips left in the other hands.")
        }
    }
    return OverlayUi(
        title = "Hand ${last.handNumber}: $title",
        lines = lines,
        scores = v.seats.indices.map { ResultLine(v.nameOf(it), res.pipsLeft[it], last.scores[it], it == v.yourSeat) },
        primary = "Next hand",
    )
}

/** What the others did since your last move, one line per turn. */
private fun GameView.recentLog(): List<String> {
    val mine = events.indexOfLast { it.seat == yourSeat }
    val recent = events.drop(mine + 1)
    val lines = mutableListOf<String>()
    var drawn = 0
    var drawnEnds: List<Int> = emptyList()
    for (e in recent) {
        val name = nameOf(e.seat)
        when (e) {
            is PublicEvent.Drew -> {
                drawn++
                drawnEnds = e.ends
            }
            is PublicEvent.Played -> {
                val line = StringBuilder(name)
                if (drawn > 0) line.append(" drew $drawn (no ${GameViewModel.numbers(drawnEnds)}) and")
                line.append(" played ${describe(e, board)}")
                if (e.points > 0) line.append(", scoring ${e.points}")
                lines += line.append('.').toString()
                drawn = 0
            }
            is PublicEvent.Passed -> {
                lines += "$name passed (no ${GameViewModel.numbers(e.ends)})."
                drawn = 0
            }
        }
    }
    return lines
}

/** "5|3 on the 5 end", using where the tile ended up on the table. */
private fun describe(e: PublicEvent.Played, board: Board): String {
    if (board.start == e.move.tile) return "${e.move.tile} to open"
    val link = board.arm(e.move.arm).firstOrNull { it.tile == e.move.tile } ?: return "${e.move.tile}"
    return "${e.move.tile} on the ${link.inner} end"
}
