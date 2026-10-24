package de.wtzg.domino.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

@Serializable
enum class Felt(val label: String) { GREEN("Green"), BLUE("Blue"), RED("Burgundy"), CHARCOAL("Charcoal") }

@Serializable
enum class AiSpeed(val label: String, val delayMs: Long) { RELAXED("Relaxed", 1300), NORMAL("Normal", 850), FAST("Fast", 350) }

/** How the numbers on a tile are drawn. */
@Serializable
enum class TileFace(val label: String) { DOTS("Dots"), COLOURED_DOTS("Coloured"), NUMBERS("Numbers") }

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colours from the wallpaper (Android 12+) instead of the app's own palette. */
    val dynamicColor: Boolean = false,
    val felt: Felt = Felt.GREEN,
    val aiSpeed: AiSpeed = AiSpeed.NORMAL,
    val haptics: Boolean = true,
    val tileFace: TileFace = TileFace.DOTS,
    /** Your tiles are greyed out while someone else is to move. */
    val dimHandWhileWaiting: Boolean = true,
    /** Coach feedback is on by default in new games. */
    val coach: Boolean = true,
    /** Online server address; empty means the one built into the app (if any). */
    val serverUrl: String = "",
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("settings")

    val settings: Flow<Settings> = store.data.map { prefs ->
        prefs[key]?.let { runCatching { AppJson.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings()
    }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { AppJson.decodeFromString(Settings.serializer(), it) }.getOrNull() }
                ?: Settings()
            prefs[key] = AppJson.encodeToString(Settings.serializer(), transform(current))
        }
    }
}
