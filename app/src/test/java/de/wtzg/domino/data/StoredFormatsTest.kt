package de.wtzg.domino.data

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.Registration
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What people already have on their phones must keep loading. These are written the way
 * released versions store them; never edit them to fit a change, change the code instead.
 */
class StoredFormatsTest {
    @Test
    fun settings() {
        val json = """{"theme":"DARK","dynamicColor":true,"felt":"RED","aiSpeed":"FAST","haptics":false,
            "tileFace":"NUMBERS","dimHandWhileWaiting":false,"coach":false,"playerName":"Ann","serverUrl":"https://d.example"}"""
        assertEquals(
            Settings(ThemeMode.DARK, true, Felt.RED, AiSpeed.FAST, false, TileFace.NUMBERS, false, false, "Ann", "https://d.example"),
            AppJson.decodeFromString(Settings.serializer(), json),
        )
        // The first settings had only a few fields; the rest take their defaults.
        assertEquals(Settings(theme = ThemeMode.LIGHT), AppJson.decodeFromString(Settings.serializer(), """{"theme":"LIGHT"}"""))
    }

    @Test
    fun aValueFromANewerVersionOnlyResetsThatSetting() {
        val json = """{"theme":"SEPIA","felt":"BLUE","haptics":false}"""
        assertEquals(Settings(felt = Felt.BLUE, haptics = false), AppJson.decodeFromString(Settings.serializer(), json))
    }

    @Test
    fun stats() {
        val json = """{"modes":{"DRAW":{"matchesWon":3,"matchesLost":1,"handsWon":10,"handsLost":4,"movesRated":20,"movesMatched":15,
            "bestMatchScore":61}},"puzzles":{"solved":5,"attempted":7,"streak":2,"bestStreak":4},"lessonsRead":["rules-draw"]}"""
        val stats = AppJson.decodeFromString(Stats.serializer(), json)
        assertEquals(ModeStats(3, 1, 10, 4, 20, 15, 61), stats.mode(GameMode.DRAW))
        assertEquals(PuzzleStats(5, 7, 2, 4), stats.puzzles)
        assertEquals(setOf("rules-draw"), stats.lessonsRead)
    }

    @Test
    fun savedGame() {
        // A match in the stored form, taken from the server's reference files (same engine types).
        val record = File("../server/src/test/resources/compat/v1/game-record.json").readText()
        val match = AppJson.parseToJsonElement(record).jsonObject.getValue("match")
        val json = """{"match":$match,"options":{"difficulty":"HARD","coach":false}}"""
        val saved = AppJson.decodeFromString(SavedGame.serializer(), json)
        assertEquals(GameOptions(Difficulty.HARD, coach = false), saved.options)
        assertTrue(saved.match.handNumber >= 1)
        assertEquals(saved, AppJson.decodeFromString(SavedGame.serializer(), AppJson.encodeToString(SavedGame.serializer(), saved)))
    }

    @Test
    fun onlineIdentities() {
        val json = """{"https://d.example":{"playerId":"p1","token":"t1","name":"Ann"}}"""
        val ids = AppJson.decodeFromString(MapSerializer(String.serializer(), Registration.serializer()), json)
        assertEquals(Registration("p1", "t1", "Ann"), ids["https://d.example"])
    }
}
