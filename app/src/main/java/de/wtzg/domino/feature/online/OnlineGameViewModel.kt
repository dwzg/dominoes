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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    fun loadFriends()

    /** Invites friends to this lobby through the app. */
    fun inviteFriends(playerIds: List<String>)

    /** The screen has switched to [OnlineGameState.moveTo]. */
    fun moved()
    fun leave()
    fun dismissError()
}

class OnlineGameViewModel(
    private val id: String,
    private val repo: OnlineRepository,
    local: Boolean = false,
    /** A friend to invite once the lobby is open: the one this game was set up to challenge. */
    private var invite: String? = null,
) : ViewModel(), OnlineGameActions {
    private val _state = MutableStateFlow(OnlineGameState(local = local))
    val state: StateFlow<OnlineGameState> = _state.asStateFlow()
    private var badgeId = 0L

    init {
        viewModelScope.launch { repo.account.collect { a -> _state.update { it.copy(serverUrl = a.serverUrl) } } }
        viewModelScope.launch { loadFeatures() }
    }

    /** What the server can do. If it can't be reached now, this is tried again once the game connects. */
    private suspend fun loadFeatures() {
        val features = try {
            repo.api().serverInfo().features
        } catch (_: OnlineException) {
            return
        }
        _state.update { it.copy(features = features) }
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

    /**
     * Shows "Reconnecting…" only if the link stays down for a moment, so quick blips don't flash.
     * Then checks whether the game still exists: the server hangs up for good when the host
     * cancels it or the game is otherwise gone.
     */
    private fun connection(up: Boolean) {
        if (up) {
            dropout?.cancel()
            dropout = null
            if (_state.value.reconnecting) _state.update { it.copy(reconnecting = false) }
            if (_state.value.features.isEmpty()) viewModelScope.launch { loadFeatures() }
        } else if (dropout == null) {
            dropout = viewModelScope.launch {
                delay(RECONNECT_NOTICE_DELAY_MS)
                _state.update { it.copy(reconnecting = true) }
                try {
                    repo.api().game(id)
                } catch (e: OnlineException) {
                    if (e.status == 404 || e.status == 403) _state.update { it.copy(gone = true) }
                }
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
                selected = s.selected.takeIf { v.isYourTurn },
                ackedHand = acked,
                badge = badge ?: s.badge,
            )
        }
        sendChallenge(v)
    }

    /** The challenge invite goes out once, as soon as we're in the lobby. */
    private fun sendChallenge(v: GameView) {
        val friend = invite ?: return
        invite = null
        if (v.status == GameStatus.LOBBY && v.invited.none { it.playerId == friend }) inviteFriends(listOf(friend))
    }

    /**
     * Runs one request at a time and shows the server's answer, or its error. A null answer
     * means the game is gone.
     */
    private fun request(onError: (OnlineException) -> Unit = {}, call: suspend () -> GameView?) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val next = call()
                if (next == null) _state.update { it.copy(gone = true) } else apply(next)
            } catch (e: OnlineException) {
                onError(e)
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun send(action: Action) {
        val v = _state.value.view ?: return
        _state.update { it.copy(selected = null) }
        // Someone else moved first: catch up, so the next try works.
        request(onError = { if (it.status == 409) refresh() }) { repo.api().act(id, action, v.version) }
    }

    fun refresh() = viewModelScope.launch {
        try {
            apply(repo.api().game(id))
        } catch (e: OnlineException) {
            if (e.status == 404 || e.status == 403) _state.update { it.copy(gone = true) } else _state.update { it.copy(error = e.message) }
        }
    }

    override fun start(difficulty: Difficulty) = request { repo.api().start(id, difficulty) }

    override fun setVisibility(visibility: Visibility) = request { repo.api().setVisibility(id, visibility) }

    override fun updateSettings(settings: SettingsRequest) = request { repo.api().updateSettings(id, settings) }

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
        if (playerIds.isNotEmpty()) request { repo.api().invite(id, playerIds) }
    }

    /** Leaving takes you back to your games, whether you left a lobby or ended a game. */
    override fun leave() {
        val v = _state.value.view ?: return
        request {
            repo.api().act(id, Action.Leave, v.version)
            null
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
