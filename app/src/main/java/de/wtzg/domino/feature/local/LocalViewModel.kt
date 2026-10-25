package de.wtzg.domino.feature.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.local.LocalHost
import de.wtzg.domino.data.local.LocalInvite
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.protocol.Limits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalState(
    /** Your name at the table; null until settings have loaded. */
    val name: String? = null,
    /** Where this phone hosts games, or null when it isn't hosting. */
    val hosting: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** Hosting has started: go and set up the game. Cleared once shown. */
    val create: Boolean = false,
    /** A game to open (after joining). Cleared once opened. */
    val open: String? = null,
    /** A friend's game from an invite link, waiting for you to enter your name and join. */
    val invite: LocalInvite? = null,
)

/**
 * Same-Wi-Fi games: host one on this phone, or join one hosted on another. Either way the
 * game itself runs like an online game, through [repo] pointed at the hosting phone.
 */
class LocalViewModel(
    private val repo: OnlineRepository,
    private val server: MutableStateFlow<String>,
    private val host: LocalHost,
    private val settings: SettingsRepository,
    /** A friend's hosted game to join straight away, from an invite link. */
    private val invite: LocalInvite? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalState())
    val state: StateFlow<LocalState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            val name = s.playerName.ifBlank { repo.account.first().registration?.name.orEmpty() }
            _state.update { it.copy(name = name, invite = invite) }
            // With a name already known, an invite link joins straight away.
            if (invite != null && name.isNotBlank()) join(invite)
        }
        viewModelScope.launch { host.url.collect { url -> _state.update { it.copy(hosting = url) } } }
    }

    fun setName(name: String) = _state.update { it.copy(name = name.take(Limits.NAME_MAX)) }

    /** Starts hosting on this phone (if it isn't already) and goes on to set up a game. */
    fun host() = withName { name ->
        val url = host.start()
        signIn(url, name)
        _state.update { it.copy(create = true) }
    }

    /** Joins a game hosted on another phone on the same Wi-Fi. */
    fun join(invite: LocalInvite) = withName { name -> join(name, invite) }

    private suspend fun join(name: String, invite: LocalInvite) {
        signIn(invite.server, name)
        val v = repo.api().join(invite.code)
        _state.update { it.copy(open = v.id, invite = null) }
    }

    fun stopHosting() = withName(needsName = false) {
        val url = host.url.value
        host.stop()
        if (server.value == url) server.value = ""
    }

    fun created() = _state.update { it.copy(create = false) }
    fun opened() = _state.update { it.copy(open = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun showError(message: String) = _state.update { it.copy(error = message) }

    /** Talks to [url] from now on, with a player of [name] on it (registered once per address). */
    private suspend fun signIn(url: String, name: String) {
        server.value = url
        settings.update { it.copy(playerName = name) }
        val account = repo.account.first { it.serverUrl == url }
        if (account.registration == null) {
            repo.register(name)
        } else if (account.registration.name != name) {
            repo.rename(name)
        }
    }

    /** Runs [block] with your name, one action at a time, showing what went wrong if it fails. */
    private fun withName(needsName: Boolean = true, block: suspend (String) -> Unit) {
        val name = _state.value.name?.trim().orEmpty()
        if (needsName && name.isEmpty()) {
            _state.update { it.copy(error = "Enter your name first, so the others know who's who.") }
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block(name)
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
