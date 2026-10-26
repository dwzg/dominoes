package de.wtzg.domino.feature.scorekeeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.ScoreKeeperRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScoreKeeperUiState(
    val loading: Boolean = true,
    val current: ScoreGame? = null,
    val history: List<ScoreGame> = emptyList(),
    val lastPlayers: List<String> = emptyList(),
)

class ScoreKeeperViewModel(
    private val repo: ScoreKeeperRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<ScoreKeeperUiState> =
        combine(repo.current, repo.history, repo.lastPlayers) { c, h, p -> ScoreKeeperUiState(false, c, h, p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoreKeeperUiState())

    fun startGame(
        players: List<String>,
        mode: ScoreMode,
        limit: Int,
        firstStarter: Int = 0,
        rotateStarter: Boolean = false,
    ) = viewModelScope.launch {
        repo.saveLastPlayers(players)
        val game = ScoreGame(
            id = clock(),
            players = players,
            mode = mode,
            limit = limit,
            startedAt = clock(),
            firstStarter = firstStarter.coerceIn(0, players.lastIndex),
            rotateStarter = rotateStarter,
        )
        repo.updateCurrent { stamped(game) }
    }

    fun rematch() {
        val g = state.value.current ?: return
        startGame(g.players, g.mode, g.limit, g.firstStarter, g.rotateStarter)
    }

    fun addRound(scores: List<Int>) = edit { it.addRound(scores) }
    // The index comes from the screen, which can be a change behind; ignore rounds that are gone.
    fun updateRound(index: Int, scores: List<Int>) = edit { if (index in it.rounds.indices) it.updateRound(index, scores) else it }
    fun deleteRound(index: Int) = edit { if (index in it.rounds.indices) it.deleteRound(index) else it }
    fun undoLastRound() = edit { if (it.rounds.isEmpty()) it else it.deleteRound(it.rounds.lastIndex) }

    /** Leaves the current game. Finished games stay in the history. */
    fun closeGame() = viewModelScope.launch { repo.saveCurrent(null) }

    fun deleteFromHistory(id: Long) = viewModelScope.launch { repo.updateHistory { h -> h.filterNot { it.id == id } } }
    fun clearHistory() = viewModelScope.launch { repo.saveHistory(emptyList()) }

    /** Applies [change] to the stored game, not to [state], which may not have caught up with the last change yet. */
    private fun edit(change: (ScoreGame) -> ScoreGame) {
        viewModelScope.launch { repo.updateCurrent { g -> g?.let { stamped(change(it)) } } }
    }

    /** Notes when the game was won, or clears it again when the winning round is undone. */
    private fun stamped(game: ScoreGame): ScoreGame =
        if (game.isFinished) game.copy(finishedAt = game.finishedAt ?: clock()) else game.copy(finishedAt = null)
}
