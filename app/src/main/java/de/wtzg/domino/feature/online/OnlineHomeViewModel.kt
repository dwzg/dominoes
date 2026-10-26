package de.wtzg.domino.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.online.OnlineAccount
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameInvite
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.ServerFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineHomeState(
    val account: OnlineAccount? = null,
    val games: List<GameSummary> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /** A game to open next (after creating or joining). Cleared once opened. */
    val open: String? = null,
    /** Set when this app and the server can't fully work together; says who should update. */
    val compatibility: String? = null,
    /** What the server can do beyond the basics; see [ServerFeatures]. */
    val features: List<String> = emptyList(),
    /** Public lobbies you could join. */
    val openGames: List<OpenGame> = emptyList(),
    /** Your friends page, for the invites and the request count; null on servers without friends. */
    val friends: FriendsView? = null,
    /** The list couldn't be updated just now; shown quietly above it rather than as a dialog. */
    val offline: String? = null,
) {
    /** The server lists public lobbies (older servers only have invite codes). */
    val publicGames: Boolean get() = ServerFeatures.PUBLIC_GAMES in features

    /** The server has friends and in-app invites. */
    val friendsAvailable: Boolean get() = ServerFeatures.FRIENDS in features

    val invites: List<GameInvite> get() = friends?.invites.orEmpty()
}

/** The oldest server protocol this app needs. Raise only when the app relies on something new. */
const val REQUIRED_SERVER_PROTOCOL = 1

/** A message when app and server can't fully work together, or null when all is well. */
fun compatibilityProblem(info: de.wtzg.domino.protocol.ServerInfo): String? = when {
    info.minProtocol > de.wtzg.domino.protocol.ProtocolVersion.CURRENT ->
        "This app is too old for the server. Please update the app to keep playing online."
    info.protocol < REQUIRED_SERVER_PROTOCOL ->
        "The server is older than this app. Ask whoever hosts it to update it."
    else -> null
}

/** What the online games screen can ask for. */
interface OnlineHomeActions {
    fun refresh()
    fun setServer(url: String)
    fun register(name: String)
    fun rename(name: String)
    fun join(code: String)

    /** Says no to a friend's invite. Accepting is joining with its code. */
    fun declineInvite(gameId: String)
    fun opened()
    fun dismissError()
}

class OnlineHomeViewModel(
    private val repo: OnlineRepository,
    private val settings: SettingsRepository,
) : ViewModel(), OnlineHomeActions {
    private val _state = MutableStateFlow(OnlineHomeState())
    val state: StateFlow<OnlineHomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // A new server or account replaces whatever was still loading for the old one.
            repo.account.distinctUntilChanged().collectLatest { a ->
                _state.update {
                    it.copy(account = a, games = if (a.registration == null) emptyList() else it.games, offline = null)
                }
                if (a.serverUrl.isNotBlank()) checkServer()
                if (a.registration != null) load()
            }
        }
    }

    /** Updates the lists. Runs now and then in the background, so problems are shown quietly. */
    override fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (_state.value.account?.registration == null) return
        _state.update { it.copy(loading = true) }
        try {
            val games = try {
                repo.api().games()
            } catch (e: OnlineException) {
                // The server has forgotten us (e.g. its data was reset): sign up again under the same name.
                if (e.status != 401 || repo.registerAgain() == null) throw e
                return // the new account triggers a fresh load
            }
            _state.update { it.copy(games = games, offline = null) }
        } catch (e: OnlineException) {
            _state.update { it.copy(offline = e.message) }
            return
        } finally {
            _state.update { it.copy(loading = false) }
        }
        if (_state.value.publicGames) refreshOpenGames()
        if (_state.value.friendsAvailable) refreshFriends()
    }

    /** Friends are extra: invites and requests just stay as they were if this fails. */
    private suspend fun refreshFriends() {
        try {
            val f = repo.api().friends()
            _state.update { it.copy(friends = f) }
        } catch (_: OnlineException) {
        }
    }

    override fun declineInvite(gameId: String) = launchSafely {
        val f = repo.api().declineInvite(gameId)
        _state.update { it.copy(friends = f) }
    }

    /** The open games are extra too. */
    private suspend fun refreshOpenGames() {
        try {
            val open = repo.api().openGames()
            _state.update { it.copy(openGames = open) }
        } catch (_: OnlineException) {
        }
    }

    /** Asks the server which protocols it speaks; failures here are reported by the next real call. */
    private suspend fun checkServer() {
        val info = try {
            repo.api().serverInfo()
        } catch (_: OnlineException) {
            null
        }
        val features = info?.features.orEmpty()
        _state.update {
            it.copy(
                compatibility = info?.let(::compatibilityProblem),
                features = features,
                openGames = if (ServerFeatures.PUBLIC_GAMES in features) it.openGames else emptyList(),
                friends = if (ServerFeatures.FRIENDS in features) it.friends else null,
            )
        }
    }

    override fun setServer(url: String) = launchSafely { settings.update { it.copy(serverUrl = url.trim()) } }

    override fun register(name: String) = launchSafely { repo.register(name) }

    override fun rename(name: String) = launchSafely { repo.rename(name) }

    override fun join(code: String) = launchSafely {
        val v = repo.api().join(code)
        _state.update { it.copy(open = v.id, friends = it.friends?.let { f -> f.copy(invites = f.invites.filter { i -> i.gameId != v.id }) }) }
    }

    override fun opened() = _state.update { it.copy(open = null) }
    override fun dismissError() = _state.update { it.copy(error = null) }

    /** Runs [block], one action at a time, turning server problems into a message on screen. */
    private fun launchSafely(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
