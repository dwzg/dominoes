package de.wtzg.domino.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Tile
import de.wtzg.domino.feature.play.GameActions
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.PublicEvent
import de.wtzg.domino.protocol.ServerFeatures
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.Visibility
import de.wtzg.domino.protocol.legalMoves
import de.wtzg.domino.ui.components.TableBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineGameState(
    val view: GameView? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val selected: Tile? = null,
    /** The last finished hand you've already seen the summary of. */
    val ackedHand: Int = 0,
    val badge: TableBadge? = null,
    /** The game no longer exists (the host cancelled it, or you left). */
    val gone: Boolean = false,
    val serverUrl: String = "",
    /** The server can list lobbies publicly, so the host may switch it on and off. */
    val publicGames: Boolean = false,
    /** The server lets the host change the game in the lobby. */
    val lobbySettings: Boolean = false,
)

/** What the online game screen can ask for, on top of moves at the table. */
interface OnlineGameActions : GameActions {
    /** Keeps the game up to date while the screen is visible. */
    suspend fun stayConnected()
    fun start(difficulty: Difficulty)

    /** Host only, in the lobby: list the game under Open games, or hide it. */
    fun setVisibility(visibility: Visibility)

    /** Host only, in the lobby: change the game, the table size, the target or the pace. */
    fun updateSettings(settings: SettingsRequest)
    fun leave()
    fun dismissError()
}

class OnlineGameViewModel(private val id: String, private val repo: OnlineRepository) : ViewModel(), OnlineGameActions {
    private val _state = MutableStateFlow(OnlineGameState())
    val state: StateFlow<OnlineGameState> = _state.asStateFlow()
    private var badgeId = 0L

    init {
        viewModelScope.launch {
            try {
                repo.account.collect { a -> _state.update { it.copy(serverUrl = a.serverUrl) } }
            } catch (_: Exception) {
            }
        }
        viewModelScope.launch {
            val features = try {
                repo.api().serverInfo().features
            } catch (_: OnlineException) {
                emptyList()
            }
            _state.update {
                it.copy(
                    publicGames = ServerFeatures.PUBLIC_GAMES in features,
                    lobbySettings = ServerFeatures.LOBBY_SETTINGS in features,
                )
            }
        }
    }

    override suspend fun stayConnected() {
        try {
            repo.api().live(id).collect(::apply)
        } catch (e: OnlineException) {
            _state.update { it.copy(error = e.message) }
        }
    }

    private fun apply(v: GameView) {
        _state.update { s ->
            val old = s.view
            // Badge for points scored since the last update.
            val newEvents = if (old != null && old.handNumber == v.handNumber) v.events.drop(old.events.size) else emptyList()
            val scored = newEvents.filterIsInstance<PublicEvent.Played>().lastOrNull { it.points > 0 }
            val badge = scored?.let {
                val who = if (it.seat == v.yourSeat) "" else "${v.seats[it.seat].name} "
                TableBadge(++badgeId, "$who+${it.points}")
            }
            // On first sight, only show the last hand's summary if the new hand has barely started.
            val acked = if (old == null && v.events.size > 1) v.lastHand?.handNumber ?: 0 else s.ackedHand
            s.copy(
                view = v,
                error = null,
                selected = s.selected.takeIf { v.isYourTurn },
                ackedHand = acked,
                badge = badge ?: s.badge,
            )
        }
    }

    private fun send(action: Action) {
        val v = _state.value.view ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, selected = null) }
            try {
                val next = repo.api().act(id, action, v.version)
                if (next == null) _state.update { it.copy(gone = true) } else apply(next)
            } catch (e: OnlineException) {
                if (e.status == 409) refresh()
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        try {
            apply(repo.api().game(id))
        } catch (e: OnlineException) {
            if (e.status == 404 || e.status == 403) _state.update { it.copy(gone = true) } else _state.update { it.copy(error = e.message) }
        }
    }

    override fun start(difficulty: Difficulty) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                apply(repo.api().start(id, difficulty))
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun setVisibility(visibility: Visibility) = lobbyChange { repo.api().setVisibility(id, visibility) }

    override fun updateSettings(settings: SettingsRequest) = lobbyChange { repo.api().updateSettings(id, settings) }

    /** A host's change in the lobby: one at a time, showing the server's answer or its error. */
    private fun lobbyChange(request: suspend () -> GameView) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                apply(request())
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun leave() = send(Action.Leave)

    override fun dismissError() = _state.update { it.copy(error = null) }

    override fun tapTile(tile: Tile) {
        val v = _state.value.view ?: return
        if (!v.isYourTurn || v.status != GameStatus.PLAYING || showingLastHand()) return
        val moves = v.legalMoves.filter { it.tile == tile }
        when (moves.size) {
            0 -> Unit
            1 -> send(Action.Play(moves.single()))
            else -> _state.update { it.copy(selected = if (it.selected == tile) null else tile) }
        }
    }

    override fun playMove(move: Move) {
        val v = _state.value.view ?: return
        if (move in v.legalMoves) send(Action.Play(move))
    }

    override fun draw() = send(Action.Draw)
    override fun pass() = send(Action.Pass)
    override fun showHint() = Unit

    /** Closes the end-of-hand summary. */
    override fun overlayPrimary() {
        val last = _state.value.view?.lastHand ?: return
        _state.update { it.copy(ackedHand = last.handNumber) }
    }

    private fun showingLastHand(): Boolean {
        val s = _state.value
        val last = s.view?.lastHand ?: return false
        return last.handNumber > s.ackedHand
    }
}
