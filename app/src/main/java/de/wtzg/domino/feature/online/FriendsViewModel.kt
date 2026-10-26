package de.wtzg.domino.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.online.KtorOnlineApi
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.ServerFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsState(
    val friends: FriendsView? = null,
    val serverUrl: String = "",
    /** You've picked a name on this server. */
    val registered: Boolean = true,
    /** Null until we know; false on servers from before friends. */
    val available: Boolean? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** A short note after something worked, e.g. "Friend request sent". */
    val message: String? = null,
    /** Counts friend codes added successfully, so the screen can clear the field after each. */
    val added: Int = 0,
    /** A friend link opened for another server than the one you play on. */
    val otherServer: String? = null,
)

/** What the friends screen can ask for. */
interface FriendsActions {
    fun refresh()
    fun add(code: String)
    fun addPlayer(playerId: String)
    fun accept(playerId: String)
    fun remove(playerId: String)
    fun dismissError()
    fun dismissMessage()
}

class FriendsViewModel(
    private val repo: OnlineRepository,
    /** The server a friend link came from, if any. */
    private val linkServer: String? = null,
) : ViewModel(), FriendsActions {
    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.account.map { it.serverUrl to (it.registration != null) }.distinctUntilChanged().collectLatest { (url, registered) ->
                // Another server: nothing from the old one may linger.
                val other = linkServer?.let(KtorOnlineApi::normalize)?.takeIf { it.isNotBlank() && it != url }
                _state.update { FriendsState(serverUrl = url, registered = registered, otherServer = other) }
                if (registered) checkAndLoad()
            }
        }
    }

    private suspend fun checkAndLoad() {
        val available = try {
            ServerFeatures.FRIENDS in repo.api().serverInfo().features
        } catch (e: OnlineException) {
            _state.update { it.copy(error = e.message) }
            return
        }
        _state.update { it.copy(available = available) }
        if (available) load()
    }

    private suspend fun load() {
        try {
            val v = repo.api().friends()
            _state.update { it.copy(friends = v) }
        } catch (_: OnlineException) {
            // Updated in the background: keep what's shown, and try again next time.
        }
    }

    /** Updates the page; if the server couldn't be reached at first, tries that again too. */
    override fun refresh() {
        if (!_state.value.registered) return
        viewModelScope.launch { if (_state.value.available == null) checkAndLoad() else load() }
    }

    override fun add(code: String) {
        val clean = friendCodeFrom(code)
        if (clean == null) {
            _state.update { it.copy(error = "A friend code has 8 letters and numbers, like ABCD-2345.") }
            return
        }
        call {
            val before = _state.value.friends
            val v = repo.api().addFriend(clean)
            _state.update { it.copy(message = addedMessage(before, v), added = it.added + 1) }
            v
        }
    }

    override fun addPlayer(playerId: String) = call {
        val before = _state.value.friends
        val v = repo.api().addFriendById(playerId)
        _state.update { it.copy(message = addedMessage(before, v)) }
        v
    }

    override fun accept(playerId: String) = call { repo.api().acceptFriend(playerId) }
    override fun remove(playerId: String) = call { repo.api().removeFriend(playerId) }

    override fun dismissError() = _state.update { it.copy(error = null) }
    override fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Says whether adding made you friends at once (they'd asked you) or sent a request. */
    private fun addedMessage(before: FriendsView?, after: FriendsView): String? {
        val oldFriends = before?.friends.orEmpty().map { it.playerId }.toSet()
        val oldSent = before?.sent.orEmpty().map { it.playerId }.toSet()
        after.friends.firstOrNull { it.playerId !in oldFriends }?.let { return "You and ${it.name} are now friends" }
        after.sent.firstOrNull { it.playerId !in oldSent }?.let { return "Friend request sent to ${it.name}" }
        return null
    }

    /** Runs one request at a time that returns the new friends page, showing its error if it fails. */
    private fun call(request: suspend () -> FriendsView) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val v = request()
                _state.update { it.copy(friends = v) }
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
