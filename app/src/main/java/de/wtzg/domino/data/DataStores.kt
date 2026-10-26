package de.wtzg.domino.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import androidx.datastore.preferences.SharedPreferencesMigration as PrefsMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.wtzg.domino.engine.GameMode
import kotlinx.serialization.json.Json

/**
 * One JSON setup for everything the app stores. Unknown keys are ignored so old data keeps
 * loading, and a value this version doesn't know (say, a setting from a newer version) falls
 * back to that field's default instead of failing the whole object.
 */
internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/** A file that can't be read at all starts over empty, rather than crashing on every launch. */
private fun startOver() = ReplaceFileCorruptionHandler { emptyPreferences() }

/** Score keeper data. Version 1 kept the same keys in SharedPreferences "domino"; they're copied over once. */
internal val Context.scoreKeeperStore: DataStore<Preferences> by preferencesDataStore(
    name = "scorekeeper",
    corruptionHandler = startOver(),
    produceMigrations = { context -> listOf(PrefsMigration(context, "domino")) },
)

internal val STATS_KEY = stringPreferencesKey("stats")

/** Settings, stats and the saved match. Version 1 training stats are folded into the Draw stats once. */
internal val Context.appStore: DataStore<Preferences> by preferencesDataStore(
    name = "app",
    corruptionHandler = startOver(),
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                produceSharedPreferences = { context.getSharedPreferences("training", Context.MODE_PRIVATE) },
            ) { old: SharedPreferencesView, prefs: Preferences ->
                val draw = ModeStats(
                    matchesWon = old.getInt("matchesWon", 0),
                    matchesLost = old.getInt("matchesLost", 0),
                    handsWon = old.getInt("handsWon", 0),
                    handsLost = old.getInt("handsLost", 0),
                    movesRated = old.getInt("movesRated", 0),
                    movesMatched = old.getInt("movesMatched", 0),
                )
                val stats = Stats(modes = mapOf(GameMode.DRAW to draw))
                prefs.toMutablePreferences().apply { this[STATS_KEY] = AppJson.encodeToString(Stats.serializer(), stats) }
            },
        )
    },
)
