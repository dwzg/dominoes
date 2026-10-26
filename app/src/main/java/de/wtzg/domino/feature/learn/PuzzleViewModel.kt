package de.wtzg.domino.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.StatsRepository
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Puzzle
import de.wtzg.domino.engine.Puzzles
import de.wtzg.domino.engine.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PuzzleUiState(
    val mode: GameMode,
    val puzzle: Puzzle? = null,
    /** A tile that fits more than one end; waiting for you to pick where. */
    val selected: Tile? = null,
    val answer: Move? = null,
) {
    val solved: Boolean? get() = answer?.let { puzzle?.isGood(it) }
}

class PuzzleViewModel(
    mode: GameMode,
    private val stats: StatsRepository,
    // Finding a good position simulates games; keep it off the main thread.
    private val generate: suspend (GameMode) -> Puzzle = { withContext(Dispatchers.Default) { Puzzles.generate(it, players = 2) } },
) : ViewModel() {
    private val _state = MutableStateFlow(PuzzleUiState(mode))
    val state: StateFlow<PuzzleUiState> = _state.asStateFlow()

    // Declared before init, which sets up the first puzzle, so that one can be cancelled too.
    private var generating: Job? = null

    init {
        next()
    }

    fun next() {
        // A puzzle still being set up would otherwise replace the new one when it's done.
        generating?.cancel()
        _state.update { PuzzleUiState(it.mode) }
        generating = viewModelScope.launch {
            val p = generate(_state.value.mode)
            _state.update { it.copy(puzzle = p) }
        }
    }

    fun tapTile(tile: Tile) {
        val s = _state.value
        val p = s.puzzle ?: return
        if (s.answer != null) return
        val moves = p.round.legalMoves().filter { it.tile == tile }
        when (moves.size) {
            0 -> Unit
            1 -> answer(moves.single())
            else -> _state.update { it.copy(selected = if (it.selected == tile) null else tile) }
        }
    }

    fun answer(move: Move) {
        val s = _state.value
        val p = s.puzzle ?: return
        if (s.answer != null || move !in p.round.legalMoves()) return
        val good = p.isGood(move)
        _state.update { it.copy(answer = move, selected = null) }
        viewModelScope.launch {
            stats.update { st ->
                val ps = st.puzzles
                val streak = if (good) ps.streak + 1 else 0
                st.copy(
                    puzzles = ps.copy(
                        solved = ps.solved + if (good) 1 else 0,
                        attempted = ps.attempted + 1,
                        streak = streak,
                        bestStreak = maxOf(ps.bestStreak, streak),
                    ),
                )
            }
        }
    }
}
