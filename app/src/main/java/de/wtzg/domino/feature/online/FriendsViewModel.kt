package de.wtzg.domino.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.ServerFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class FriendsViewModel(private val repo: OnlineRepository) : ViewModel(), FriendsActions {
    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.account.map { it.serverUrl to (it.registration != null) }.distinctUntilChanged().collect { (url, registered) ->
                _state.update { it.copy(serverUrl = url, registered = registered) }
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
        if (available) refresh()
    }

    override fun refresh() = run(busy = false) { repo.api().friends() }

    override fun add(code: String) {
        val clean = friendCodeFrom(code)
        if (clean == null) {
            _state.update { it.copy(error = "A friend code has 8 letters and numbers, like ABCD-2345.") }
            return
        }
        run {
            val before = _state.value.friends
            val v = repo.api().addFriend(clean)
            _state.update { it.copy(message = addedMessage(before, v)) }
            v
        }
    }

    override fun addPlayer(playerId: String) = run {
        val before = _state.value.friends
        val v = repo.api().addFriendById(playerId)
        _state.update { it.copy(message = addedMessage(before, v)) }
        v
    }

    override fun accept(playerId: String) = run { repo.api().acceptFriend(playerId) }
    override fun remove(playerId: String) = run { repo.api().removeFriend(playerId) }

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

    /** Runs a call that returns the new friends page, showing its error if it fails. */
    private fun run(busy: Boolean = true, call: suspend () -> FriendsView) {
        if (busy && _state.value.busy) return
        viewModelScope.launch {
            if (busy) _state.update { it.copy(busy = true, error = null) }
            try {
                val v = call()
                _state.update { it.copy(friends = v) }
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                if (busy) _state.update { it.copy(busy = false) }
            }
        }
    }
}
