package de.wtzg.domino.feature.learn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.wtzg.domino.data.StatsRepository
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Puzzle
import de.wtzg.domino.engine.Puzzles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class PuzzleViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var storeScope: CoroutineScope
    private lateinit var stats: StatsRepository

    /** Each puzzle asked for, answered by the test whenever it likes. */
    private val requests = mutableListOf<CompletableDeferred<Puzzle>>()

    private fun viewModel() = PuzzleViewModel(GameMode.ALL_FIVES, stats) {
        CompletableDeferred<Puzzle>().also { requests += it }.await()
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        storeScope = CoroutineScope(dispatcher + SupervisorJob())
        stats = StatsRepository(PreferenceDataStoreFactory.create(scope = storeScope) { tmp.newFile("stats.preferences_pb") })
    }

    @After fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun onlyTheFirstAnswerCounts() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val p = puzzle(1)
        requests.single().complete(p)
        advanceUntilIdle()

        val moves = p.round.legalMoves()
        vm.answer(moves[0])
        vm.answer(moves[1])
        advanceUntilIdle()

        assertEquals(moves[0], vm.state.value.answer)
        assertEquals(1, stats.stats.first().puzzles.attempted)
    }

    @Test
    fun aMissEndsTheStreak() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val p = puzzle(2)
        val miss = p.round.legalMoves().firstOrNull { !p.isGood(it) }
        assumeTrue("the puzzle has a wrong answer", miss != null)

        requests.last().complete(p)
        advanceUntilIdle()
        vm.answer(p.best.move)
        advanceUntilIdle()
        assertEquals(1, stats.stats.first().puzzles.streak)

        vm.next()
        advanceUntilIdle()
        requests.last().complete(p)
        advanceUntilIdle()
        vm.answer(miss!!)
        advanceUntilIdle()

        val ps = stats.stats.first().puzzles
        assertEquals(0, ps.streak)
        assertEquals(1, ps.bestStreak)
        assertEquals(1, ps.solved)
        assertEquals(2, ps.attempted)
    }

    @Test
    fun aPuzzleThatArrivesLateDoesNotReplaceTheNextOne() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // Next puzzle before the first one is ready.
        vm.next()
        advanceUntilIdle()
        assertEquals(2, requests.size)

        val second = puzzle(3)
        requests[1].complete(second)
        advanceUntilIdle()
        vm.answer(second.best.move)
        advanceUntilIdle()

        val first = puzzle(4)
        assertNotEquals(first, second)
        requests[0].complete(first)
        advanceUntilIdle()

        assertSame(second, vm.state.value.puzzle)
        assertEquals(second.best.move, vm.state.value.answer)
    }

    private fun puzzle(seed: Int): Puzzle = Puzzles.generate(GameMode.ALL_FIVES, Random(seed), players = 2)
}
