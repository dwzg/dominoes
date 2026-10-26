package de.wtzg.domino.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.ServerFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGameState(
    val busy: Boolean = false,
    val error: String? = null,
    /** The new game's lobby, to open next. Cleared once opened. */
    val open: String? = null,
    /** What the server can do beyond the basics; see [ServerFeatures]. */
    val features: List<String> = emptyList(),
) {
    val publicGames: Boolean get() = ServerFeatures.PUBLIC_GAMES in features
    val turnLimits: Boolean get() = ServerFeatures.TURN_LIMITS in features
}

/** Setting up a new game, online or on a phone on the same Wi-Fi. */
class CreateGameViewModel(private val repo: OnlineRepository) : ViewModel() {
    private val _state = MutableStateFlow(CreateGameState())
    val state: StateFlow<CreateGameState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val features = try {
                repo.api().serverInfo().features
            } catch (_: OnlineException) {
                emptyList()
            }
            _state.update { it.copy(features = features) }
        }
    }

    fun create(request: CreateGameRequest) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val v = repo.api().create(request)
                _state.update { it.copy(open = v.id) }
            } catch (e: OnlineException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun opened() = _state.update { it.copy(open = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
