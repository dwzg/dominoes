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
        commit(
            ScoreGame(
                id = clock(),
                players = players,
                mode = mode,
                limit = limit,
                startedAt = clock(),
                firstStarter = firstStarter.coerceIn(0, players.lastIndex),
                rotateStarter = rotateStarter,
            ),
        )
    }

    fun rematch() {
        val g = state.value.current ?: return
        startGame(g.players, g.mode, g.limit, g.firstStarter, g.rotateStarter)
    }

    fun addRound(scores: List<Int>) = edit { it.addRound(scores) }
    fun updateRound(index: Int, scores: List<Int>) = edit { it.updateRound(index, scores) }
    fun deleteRound(index: Int) = edit { it.deleteRound(index) }
    fun undoLastRound() = edit { if (it.rounds.isEmpty()) it else it.deleteRound(it.rounds.lastIndex) }

    /** Leaves the current game. Finished games stay in the history. */
    fun closeGame() = viewModelScope.launch { repo.saveCurrent(null) }

    fun deleteFromHistory(id: Long) = viewModelScope.launch { repo.saveHistory(state.value.history.filterNot { it.id == id }) }
    fun clearHistory() = viewModelScope.launch { repo.saveHistory(emptyList()) }

    private fun edit(change: (ScoreGame) -> ScoreGame) {
        val g = state.value.current ?: return
        viewModelScope.launch { commit(change(g)) }
    }

    private suspend fun commit(game: ScoreGame) {
        // A game is listed in the history exactly while it is finished,
        // so undoing the winning round takes it out again.
        val updated = if (game.isFinished) game.copy(finishedAt = game.finishedAt ?: clock()) else game.copy(finishedAt = null)
        repo.saveCurrent(updated)
        val others = state.value.history.filterNot { it.id == updated.id }
        val history = if (updated.isFinished) listOf(updated) + others else others
        repo.saveHistory(history.sortedByDescending { it.finishedAt ?: it.startedAt })
    }
}
