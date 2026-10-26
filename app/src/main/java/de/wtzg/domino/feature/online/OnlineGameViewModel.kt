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
import de.wtzg.domino.protocol.Friend
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** What the server can do beyond the basics; see [ServerFeatures]. */
    val features: List<String> = emptyList(),
    /** When [view] arrived, by this phone's clock, to count down [GameView.turnEndsInMs] from. */
    val viewAt: Long = 0,
    /** A game to switch to, e.g. the rematch. Cleared once opened. */
    val moveTo: String? = null,
    /** The live connection dropped a moment ago and hasn't come back yet. */
    val reconnecting: Boolean = false,
    /** Hosted on a phone on the same Wi-Fi rather than the online server. */
    val local: Boolean = false,
    /** Your friends, once loaded for inviting them to the lobby. */
    val friends: List<Friend>? = null,
) {
    /** The server has friends, so you can invite them to the lobby from the app. */
    val friendsAvailable: Boolean get() = !local && ServerFeatures.FRIENDS in features

    /** The server can list lobbies publicly, so the host may switch it on and off. */
    val publicGames: Boolean get() = ServerFeatures.PUBLIC_GAMES in features

    /** The server lets the host change the game in the lobby. */
    val lobbySettings: Boolean get() = ServerFeatures.LOBBY_SETTINGS in features

    val turnLimits: Boolean get() = ServerFeatures.TURN_LIMITS in features
    val rematch: Boolean get() = ServerFeatures.REMATCH in features
}

/** How long the live connection may be down before the screen says so. */
const val RECONNECT_NOTICE_DELAY_MS = 2_000L

/** What the online game screen can ask for, on top of moves at the table. */
interface OnlineGameActions : GameActions {
    /** Keeps the game up to date while the screen is visible. */
    suspend fun stayConnected()
    fun start(difficulty: Difficulty)

    /** Host only, in the lobby: list the game under Open games, or hide it. */
    fun setVisibility(visibility: Visibility)

    /** Host only, in the lobby: change the game, the table size, the target or the pace. */
    fun updateSettings(settings: SettingsRequest)

    /** Loads your friends, to pick whom to invite. */
    fun loadFriends() = Unit

    /** Invites friends to this lobby through the app. */
    fun inviteFriends(playerIds: List<String>) = Unit

    /** The screen has switched to [OnlineGameState.moveTo]. */
    fun moved()
    fun leave()
    fun dismissError()
}

class OnlineGameViewModel(
    private val id: String,
    private val repo: OnlineRepository,
    local: Boolean = false,
) : ViewModel(), OnlineGameActions {
    private val _state = MutableStateFlow(OnlineGameState(local = local))
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
            _state.update { it.copy(features = features) }
        }
    }

    override suspend fun stayConnected() {
        try {
            repo.api().live(id, ::connection).collect(::apply)
        } catch (e: OnlineException) {
            _state.update { it.copy(error = e.message) }
        } finally {
            // Leaving the screen stops the connection on purpose; that's not a dropout.
            connection(true)
        }
    }

    private var dropout: Job? = null

    /** Shows "Reconnecting…" only if the link stays down for a moment, so quick blips don't flash. */
    private fun connection(up: Boolean) {
        if (up) {
            dropout?.cancel()
            dropout = null
            if (_state.value.reconnecting) _state.update { it.copy(reconnecting = false) }
        } else if (dropout == null) {
            dropout = viewModelScope.launch {
                delay(RECONNECT_NOTICE_DELAY_MS)
                _state.update { it.copy(reconnecting = true) }
            }
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
                viewAt = System.currentTimeMillis(),
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

    override fun loadFriends() {
        viewModelScope.launch {
            try {
                val f = repo.api().friends().friends
                _state.update { it.copy(friends = f) }
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    override fun inviteFriends(playerIds: List<String>) {
        if (playerIds.isNotEmpty()) lobbyChange { repo.api().invite(id, playerIds) }
    }

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

    /** Leaving takes you back to your games, whether you left a lobby or ended a game. */
    override fun leave() {
        val v = _state.value.view ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                repo.api().act(id, Action.Leave, v.version)
                _state.update { it.copy(gone = true) }
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

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

    /** Closes the end-of-hand summary, or at the end of the match, starts or joins the rematch. */
    override fun overlayPrimary() {
        val v = _state.value.view ?: return
        if (v.status == GameStatus.FINISHED) {
            rematch()
            return
        }
        val last = v.lastHand ?: return
        _state.update { it.copy(ackedHand = last.handNumber) }
    }

    private fun rematch() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val next = repo.api().rematch(id)
                _state.update { it.copy(moveTo = next.id) }
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun moved() = _state.update { it.copy(moveTo = null) }

    private fun showingLastHand(): Boolean {
        val s = _state.value
        val last = s.view?.lastHand ?: return false
        return last.handNumber > s.ackedHand
    }
}
