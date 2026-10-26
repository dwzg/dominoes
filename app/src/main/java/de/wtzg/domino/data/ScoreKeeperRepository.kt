package de.wtzg.domino.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.wtzg.domino.feature.scorekeeper.ScoreGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Score keeper games for real-life play: the running game, finished games and last used names. */
class ScoreKeeperRepository(private val store: DataStore<Preferences>) {
    // Same keys as version 1 so migrated data is found.
    private val currentKey = stringPreferencesKey("current_game")
    private val historyKey = stringPreferencesKey("history")
    private val playersKey = stringPreferencesKey("last_players")
    private val gameList = ListSerializer(ScoreGame.serializer())
    private val nameList = ListSerializer(String.serializer())

    private fun Preferences.decodeCurrent(): ScoreGame? =
        this[currentKey]?.let { runCatching { AppJson.decodeFromString(ScoreGame.serializer(), it) }.getOrNull() }

    private fun Preferences.decodeHistory(): List<ScoreGame> =
        this[historyKey]?.let { runCatching { AppJson.decodeFromString(gameList, it) }.getOrNull() } ?: emptyList()

    val current: Flow<ScoreGame?> = store.data.map { it.decodeCurrent() }
    val history: Flow<List<ScoreGame>> = store.data.map { it.decodeHistory() }
    val lastPlayers: Flow<List<String>> = store.data.map { p ->
        p[playersKey]?.let { runCatching { AppJson.decodeFromString(nameList, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun saveCurrent(game: ScoreGame?) = store.edit { p ->
        if (game == null) p.remove(currentKey) else p[currentKey] = AppJson.encodeToString(ScoreGame.serializer(), game)
    }

    /**
     * Replaces the running game with [change] applied to the stored one (null if there is none;
     * return null to change nothing). Its history entry is updated in the same write: a game is
     * listed exactly while it is finished, so undoing the winning round takes it out again.
     * Reading and writing in one edit means quick changes never overwrite each other, and the
     * history can't get out of step if the app is killed in between.
     */
    suspend fun updateCurrent(change: (ScoreGame?) -> ScoreGame?) {
        store.edit { p ->
            val game = change(p.decodeCurrent()) ?: return@edit
            p[currentKey] = AppJson.encodeToString(ScoreGame.serializer(), game)
            val others = p.decodeHistory().filterNot { it.id == game.id }
            val history = if (game.isFinished) listOf(game) + others else others
            p[historyKey] = AppJson.encodeToString(gameList, history.sortedByDescending { it.finishedAt ?: it.startedAt })
        }
    }

    /** Changes the list of finished games, based on what is stored right now. */
    suspend fun updateHistory(change: (List<ScoreGame>) -> List<ScoreGame>) {
        store.edit { p -> p[historyKey] = AppJson.encodeToString(gameList, change(p.decodeHistory())) }
    }

    suspend fun saveHistory(games: List<ScoreGame>) = updateHistory { games }

    suspend fun saveLastPlayers(names: List<String>) = store.edit { p -> p[playersKey] = AppJson.encodeToString(nameList, names) }
}
