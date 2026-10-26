package de.wtzg.domino.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Tells open connections when a game changed, and tracks who has a game open right now. */
class LiveHub {
    /**
     * One counter per game, bumped on every change. A state flow keeps only the latest value,
     * so a slow connection skips states in between but never misses the newest one, and
     * never holds up anyone else.
     */
    private val versions = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    private val present = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    private fun counter(gameId: String) = versions.computeIfAbsent(gameId) { MutableStateFlow(0L) }

    /** Emits at once, then on every change to [gameId]. Each connection re-reads the game and sends its own view. */
    fun changes(gameId: String): Flow<Long> = counter(gameId)

    fun changed(gameId: String) {
        counter(gameId).value++
    }

    fun online(gameId: String): Set<String> = present[gameId]?.keys?.toSet() ?: emptySet()

    fun connect(gameId: String, playerId: String) {
        present.computeIfAbsent(gameId) { ConcurrentHashMap() }.merge(playerId, 1, Int::plus)
        changed(gameId)
    }

    fun disconnect(gameId: String, playerId: String) {
        present.computeIfPresent(gameId) { _, players ->
            players.computeIfPresent(playerId) { _, n -> if (n <= 1) null else n - 1 }
            players.takeIf { it.isNotEmpty() }
        }
        if (present[gameId] == null) versions.remove(gameId)
        changed(gameId)
    }
}
