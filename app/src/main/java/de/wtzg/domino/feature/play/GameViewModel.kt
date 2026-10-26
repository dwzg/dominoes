package de.wtzg.domino.feature.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.AiSpeed
import de.wtzg.domino.data.GameOptions
import de.wtzg.domino.data.SavedGame
import de.wtzg.domino.data.SavedGameRepository
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.StatsRepository
import de.wtzg.domino.engine.Evaluation
import de.wtzg.domino.engine.Event
import de.wtzg.domino.engine.Match
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Round
import de.wtzg.domino.engine.Rules
import de.wtzg.domino.engine.Strategy
import de.wtzg.domino.engine.Tile
import de.wtzg.domino.ui.components.BADGE_MS
import de.wtzg.domino.ui.components.TableBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val YOU = 0

/** Names around the table, clockwise from you. */
val SEAT_NAMES = listOf("You", "Rosa", "Hank", "Mabel")

enum class NoteKind { GOOD, TIP, INFO }

data class CoachNote(val kind: NoteKind, val title: String, val text: String)

data class GameUiState(
    val game: SavedGame? = null,
    /** A tile that fits more than one end; waiting for you to pick where. */
    val selected: Tile? = null,
    val hint: Evaluation? = null,
    /** Feedback on your last move. */
    val coach: CoachNote? = null,
    /** What the other players did since your last move. */
    val aiLog: List<String> = emptyList(),
    val lastPlaced: Tile? = null,
    val lastPlacedByOpponent: Boolean = false,
    val aiThinking: Boolean = false,
    val badge: TableBadge? = null,
    val handRated: Int = 0,
    val handMatched: Int = 0,
) {
    val match: Match? get() = game?.match
    val round: Round? get() = game?.match?.round
    val yourTurn: Boolean get() = round?.let { it.turn == YOU && !it.isOver && match?.isOver == false } == true && !aiThinking
}

class GameViewModel(
    private val saved: SavedGameRepository,
    private val stats: StatsRepository,
    settings: SettingsRepository,
) : ViewModel(), GameActions {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val aiSpeed: StateFlow<AiSpeed> =
        settings.settings.map { it.aiSpeed }.stateIn(viewModelScope, SharingStarted.Eagerly, AiSpeed.NORMAL)

    private var aiJob: Job? = null
    private var aiRuns = 0
    private var badgeId = 0L

    init {
        viewModelScope.launch {
            val game = saved.load() ?: return@launch
            if (_state.value.game == null) {
                _state.value = GameUiState(game = game)
                // Only resume the AI; a finished hand was already counted before saving.
                val r = game.match.round
                if (!game.match.isOver && !r.isOver && r.turn != YOU) runAi()
            }
        }
    }

    fun start(rules: Rules, options: GameOptions) {
        aiJob?.cancel()
        val game = SavedGame(Match.start(rules), options)
        _state.value = GameUiState(game = game, coach = openingNote(game.match.round))
        persist()
        afterTurn()
    }

    override fun overlayPrimary() {
        val m = _state.value.match ?: return
        if (m.isOver) rematch() else if (m.round.isOver) nextHand()
    }

    fun rematch() {
        val g = _state.value.game ?: return
        start(g.match.rules, g.options)
    }

    /** Leaves the match for good. */
    fun quit() {
        aiJob?.cancel()
        _state.value = GameUiState()
        viewModelScope.launch { saved.save(null) }
    }

    fun nextHand() {
        val g = _state.value.game ?: return
        if (!g.match.round.isOver || g.match.isOver) return
        val next = g.copy(match = g.match.nextHand())
        _state.value = GameUiState(game = next, coach = openingNote(next.match.round))
        persist()
        afterTurn()
    }

    override fun tapTile(tile: Tile) {
        val s = _state.value
        val r = s.round ?: return
        if (!s.yourTurn) return
        val moves = r.legalMoves().filter { it.tile == tile }
        when (moves.size) {
            0 -> _state.update {
                it.copy(
                    selected = null,
                    coach = CoachNote(NoteKind.INFO, "$tile doesn't fit", "The open ends are ${r.board.openValues.distinct().joinToString(", ")}."),
                )
            }
            1 -> play(moves.single())
            else -> _state.update { it.copy(selected = if (it.selected == tile) null else tile) }
        }
    }

    /** Plays [move] if it's your turn and legal (e.g. from tapping a spot on the table). */
    override fun playMove(move: Move) {
        val s = _state.value
        if (!s.yourTurn || move !in (s.round?.legalMoves() ?: emptyList())) return
        play(move)
    }

    private fun play(move: Move) {
        val s = _state.value
        val g = s.game ?: return
        val r = g.match.round
        val evals = Strategy.evaluateAll(r, YOU)
        val mine = evals.first { it.move == move }
        val best = evals.first()
        val coachOn = g.options.coach
        val points = r.pointsFor(move)
        var rated = s.handRated
        var matched = s.handMatched

        val note = when {
            r.hands[YOU].size == 1 -> CoachNote(NoteKind.GOOD, "Domino!", "You went out.")
            evals.map { it.move.tile }.distinct().size < 2 || (r.board.isEmpty && r.opening != null) ->
                if (coachOn) CoachNote(NoteKind.INFO, "Forced move", "That was your only sensible play.") else null
            else -> {
                val good = mine.score >= best.score - 1.0
                rated++
                if (good) matched++
                viewModelScope.launch {
                    stats.updateMode(r.rules.mode) {
                        it.copy(movesRated = it.movesRated + 1, movesMatched = it.movesMatched + if (good) 1 else 0)
                    }
                }
                when {
                    !coachOn -> null
                    good -> CoachNote(NoteKind.GOOD, "Good move", mine.pros.firstOrNull() ?: "That's what the coach would have played.")
                    else -> CoachNote(
                        NoteKind.TIP,
                        "Better: ${Strategy.describe(best.move, r.board)}",
                        buildString {
                            append(best.pros.take(2).joinToString(" "))
                            mine.cons.firstOrNull()?.let { append(" Your move: ").append(it.replaceFirstChar(Char::lowercase)) }
                        }.trim(),
                    )
                }
            }
        }
        val next = g.copy(match = g.match.play(move))
        _state.update {
            it.copy(
                game = next,
                coach = note,
                aiLog = emptyList(),
                selected = null,
                hint = null,
                lastPlaced = move.tile,
                lastPlacedByOpponent = false,
                handRated = rated,
                handMatched = matched,
                badge = if (points > 0) newBadge("+$points") else it.badge,
            )
        }
        persist()
        afterTurn()
    }

    override fun draw() {
        val s = _state.value
        val g = s.game ?: return
        val r = g.match.round
        if (!s.yourTurn || !r.canDraw) return
        val next = g.copy(match = g.match.draw())
        val drawn = (next.match.round.events.last() as Event.Drew).tile
        val who = if (r.players == 2) "The AI now knows" else "Everyone now knows"
        _state.update {
            it.copy(
                game = next,
                selected = null,
                hint = null,
                coach = CoachNote(
                    NoteKind.INFO,
                    "You drew $drawn",
                    (if (next.match.round.canDraw) "Still nothing fits. Draw again. " else "") +
                        "$who you have no ${numbers(r.board.openValues)}.",
                ),
            )
        }
        persist()
    }

    override fun pass() {
        val s = _state.value
        val g = s.game ?: return
        if (!s.yourTurn || !g.match.round.mustPass) return
        val next = g.copy(match = g.match.pass())
        val why = if (g.match.rules.mode.drawsFromBoneyard) "The boneyard is empty and nothing fits." else "Nothing fits, and in Block you can't draw."
        _state.update { it.copy(game = next, coach = CoachNote(NoteKind.INFO, "You passed", why), aiLog = emptyList()) }
        persist()
        afterTurn()
    }

    override fun showHint() {
        val s = _state.value
        val r = s.round ?: return
        if (!s.yourTurn) return
        _state.update { it.copy(hint = Strategy.evaluateAll(r, YOU).firstOrNull(), selected = null) }
    }

    private fun afterTurn() {
        val g = _state.value.game ?: return
        val m = g.match
        when {
            m.isOver -> finishMatch(m)
            m.round.isOver -> finishHand(m)
            m.round.turn != YOU -> runAi()
        }
    }

    private fun runAi() {
        aiJob?.cancel()
        val run = ++aiRuns
        aiJob = viewModelScope.launch {
            _state.update { it.copy(aiThinking = true) }
            try {
                while (true) {
                    val g = _state.value.game ?: break
                    val m = g.match
                    if (m.isOver || m.round.isOver || m.round.turn == YOU) break
                    // On a fresh hand, let the shuffle on screen finish before the opening play.
                    delay(aiSpeed.value.delayMs + if (m.round.board.isEmpty) SHUFFLE_MS else 0L)
                    aiTurn(g)
                }
            } finally {
                // Also when something fails, so your tiles never stay locked. A run that was
                // replaced by a newer one leaves the flag to that one.
                if (run == aiRuns) _state.update { it.copy(aiThinking = false) }
            }
            val g = _state.value.game ?: return@launch
            if (g.match.isOver || g.match.round.isOver) afterTurn()
        }
    }

    /** One opponent's whole turn: draws (with a little pause each), then a play or a pass. */
    private suspend fun aiTurn(start: SavedGame) {
        var g = start
        val p = g.match.round.turn
        val name = SEAT_NAMES[p]
        val ends = g.match.round.board.openValues
        var drawn = 0
        while (g.match.round.canDraw) {
            g = g.copy(match = g.match.draw())
            drawn++
            _state.update { it.copy(game = g) }
            delay(aiSpeed.value.delayMs / 4)
        }
        val r = g.match.round
        val move = Strategy.chooseMove(r, g.options.difficulty)
        val line = StringBuilder(name)
        if (drawn > 0) line.append(" drew $drawn (no ${numbers(ends)}) and")
        var badge: TableBadge? = null
        if (move != null) {
            val points = r.pointsFor(move)
            line.append(" played ${Strategy.describe(move, r.board)}")
            if (points > 0) {
                line.append(", scoring $points")
                badge = newBadge("$name +$points")
            }
            g = g.copy(match = g.match.play(move))
        } else {
            line.append(" passed (no ${numbers(r.board.openValues)})")
            g = g.copy(match = g.match.pass())
        }
        line.append('.')
        val placed = move?.tile
        _state.update {
            it.copy(
                game = g,
                aiLog = it.aiLog + line.toString(),
                lastPlaced = placed ?: it.lastPlaced,
                lastPlacedByOpponent = if (placed != null) true else it.lastPlacedByOpponent,
                badge = badge ?: it.badge,
            )
        }
        persist()
    }

    /**
     * A badge for a scoring play. It is cleared once it has shown, so it doesn't pop up (and
     * buzz) again when you come back to the game or turn the phone.
     */
    private fun newBadge(text: String): TableBadge {
        val badge = TableBadge(++badgeId, text)
        viewModelScope.launch {
            delay(BADGE_MS)
            _state.update { if (it.badge?.id == badge.id) it.copy(badge = null) else it }
        }
        return badge
    }

    private fun finishHand(m: Match) {
        val res = m.round.result ?: return
        viewModelScope.launch {
            stats.updateMode(m.rules.mode) {
                when (res.winner) {
                    YOU -> it.copy(handsWon = it.handsWon + 1)
                    null -> it
                    else -> it.copy(handsLost = it.handsLost + 1)
                }
            }
        }
    }

    private fun finishMatch(m: Match) {
        if (m.round.isOver) finishHand(m)
        val won = m.winner == YOU
        viewModelScope.launch {
            stats.updateMode(m.rules.mode) {
                it.copy(
                    matchesWon = it.matchesWon + if (won) 1 else 0,
                    matchesLost = it.matchesLost + if (won) 0 else 1,
                    bestMatchScore = maxOf(it.bestMatchScore, m.scores[YOU]),
                )
            }
            // Nothing left to resume.
            saved.save(null)
        }
    }

    private fun persist() {
        val g = _state.value.game ?: return
        if (g.match.isOver) return
        viewModelScope.launch { saved.save(g) }
    }

    private fun openingNote(r: Round): CoachNote {
        val open = r.opening
        val who = SEAT_NAMES[r.turn]
        return when {
            open == null -> CoachNote(NoteKind.INFO, "New hand", "$who leads.")
            r.turn == YOU -> CoachNote(NoteKind.INFO, "You open", "You hold the highest tile, $open, so you play it first.")
            else -> CoachNote(NoteKind.INFO, "$who opens", "$who holds the highest tile, $open, so they play it first.")
        }
    }

    companion object {
        fun numbers(values: List<Int>): String {
            val d = values.distinct()
            return when (d.size) {
                1 -> "${d[0]}s"
                else -> d.dropLast(1).joinToString(", ") { "${it}s" } + " or ${d.last()}s"
            }
        }
    }
}
