package de.wtzg.domino.feature.play

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.wtzg.domino.data.GameOptions
import de.wtzg.domino.data.SavedGame
import de.wtzg.domino.data.SavedGameRepository
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.StatsRepository
import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Match
import de.wtzg.domino.engine.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var storeScope: CoroutineScope
    private lateinit var saved: SavedGameRepository
    private lateinit var stats: StatsRepository
    private lateinit var settings: SettingsRepository
    private val options = GameOptions(Difficulty.NORMAL, coach = false)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        storeScope = CoroutineScope(dispatcher + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = storeScope) { tmp.newFile("app.preferences_pb") }
        saved = SavedGameRepository(store)
        stats = StatsRepository(store)
        settings = SettingsRepository(store)
    }

    @After fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    /** A fresh match where the computer is to move. */
    private fun aiToMove(rules: Rules): Match {
        val m = Match.start(rules, Random(7))
        return if (m.round.turn == YOU) m.play(m.round.legalMoves().first()) else m
    }

    /** Runs what's ready (without moving the clock) until [done], for steps that wait on file access. */
    private fun TestScope.runUntil(done: () -> Boolean) {
        repeat(400) {
            runCurrent()
            if (done()) return
            Thread.sleep(5)
        }
        fail("condition never met")
    }

    @Test
    fun playsAWholeMatchAgainstTheAiAndKeepsStats() = runTest(dispatcher) {
        val vm = GameViewModel(saved, stats, settings)

        vm.start(Rules(GameMode.ALL_FIVES, players = 3, target = 30), GameOptions(Difficulty.HARD, coach = true))
        advanceUntilIdle()
        assertNotNull("game is saved", saved.load())

        var steps = 0
        while (vm.state.value.match?.isOver == false) {
            val s = vm.state.value
            val r = s.round!!
            when {
                r.isOver -> vm.nextHand()
                s.yourTurn && r.canDraw -> vm.draw()
                s.yourTurn && r.mustPass -> vm.pass()
                s.yourTurn -> vm.playMove(r.legalMoves().first())
            }
            advanceUntilIdle()
            assertTrue("match never ends", ++steps < 2_000)
        }
        val m = vm.state.value.match!!
        assertTrue(m.scores[m.winner!!] >= 30)
        val st = stats.stats.first().mode(GameMode.ALL_FIVES)
        assertEquals(1, st.matches)
        assertTrue(st.handsWon + st.handsLost >= 1 || !m.round.isOver)
        assertNull("finished matches aren't kept for resuming", saved.load())
    }

    @Test
    fun aGameSavedDuringTheComputersTurnCarriesOnWhenResumed() = runTest(dispatcher) {
        val match = aiToMove(Rules(GameMode.DRAW, players = 2))
        saved.save(SavedGame(match, options))

        val vm = GameViewModel(saved, stats, settings)
        advanceUntilIdle()

        val r = vm.state.value.round!!
        assertTrue("the computer moved", r.events.size > match.round.events.size)
        assertTrue("and it's your turn again", r.turn == YOU || r.isOver)
        assertFalse(vm.state.value.aiThinking)
    }

    @Test
    fun quittingWhileTheComputerThinksLeavesNothingToResume() = runTest(dispatcher) {
        saved.save(SavedGame(aiToMove(Rules(GameMode.DRAW, players = 2)), options))
        val vm = GameViewModel(saved, stats, settings)
        runUntil { vm.state.value.aiThinking }

        vm.quit()
        advanceUntilIdle()

        assertNull(vm.state.value.game)
        assertNull("no save left behind", saved.load())
    }

    @Test
    fun startingANewGameStopsTheComputerInTheOldOne() = runTest(dispatcher) {
        saved.save(SavedGame(aiToMove(Rules(GameMode.DRAW, players = 2)), options))
        val vm = GameViewModel(saved, stats, settings)
        runUntil { vm.state.value.aiThinking }

        vm.start(Rules(GameMode.BLOCK, players = 2), options)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("the old game didn't come back", GameMode.BLOCK, s.match!!.rules.mode)
        assertEquals(GameMode.BLOCK, saved.load()!!.match.rules.mode)
        assertTrue(s.round!!.turn == YOU || s.round!!.isOver)
        assertFalse(s.aiThinking)
    }

    @Test
    fun theScoreBadgeIsClearedOnceItHasShown() = runTest(dispatcher) {
        val vm = GameViewModel(saved, stats, settings)
        val shown = mutableSetOf<Long>()
        backgroundScope.launch { vm.state.collect { s -> s.badge?.let { shown += it.id } } }

        vm.start(Rules(GameMode.ALL_FIVES, players = 2, target = 1000), options)
        advanceUntilIdle()
        var steps = 0
        while (shown.isEmpty()) {
            val s = vm.state.value
            val r = s.round!!
            when {
                r.isOver -> vm.nextHand()
                s.yourTurn && r.canDraw -> vm.draw()
                s.yourTurn && r.mustPass -> vm.pass()
                s.yourTurn -> vm.playMove(r.legalMoves().first())
            }
            advanceUntilIdle()
            assertTrue("nobody ever scored", ++steps < 2_000)
        }
        // Coming back to the game (or rotating) shows the state as it is now: no badge to replay.
        assertNull(vm.state.value.badge)
    }
}
