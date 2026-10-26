package de.wtzg.domino.feature.scorekeeper

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.wtzg.domino.data.ScoreKeeperRepository
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreKeeperViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var storeScope: CoroutineScope
    private lateinit var repo: ScoreKeeperRepository
    private var now = 1_000L

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        storeScope = CoroutineScope(dispatcher + SupervisorJob())
        repo = ScoreKeeperRepository(PreferenceDataStoreFactory.create(scope = storeScope) { tmp.newFile("scores.preferences_pb") })
    }

    @After fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    /** A view model whose state is watched, as it is while the screen shows. */
    private fun TestScope.viewModel(): ScoreKeeperViewModel {
        val vm = ScoreKeeperViewModel(repo) { now++ }
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private fun TestScope.newGame(vm: ScoreKeeperViewModel) {
        vm.startGame(listOf("Ann", "Bo"), ScoreMode.FIRST_TO_TARGET, limit = 100)
        advanceUntilIdle()
    }

    @Test
    fun aGameIsInTheHistoryExactlyWhileItIsWon() = runTest(dispatcher) {
        val vm = viewModel()
        newGame(vm)
        vm.addRound(listOf(60, 20))
        advanceUntilIdle()
        assertTrue("not won yet", repo.history.first().isEmpty())

        vm.addRound(listOf(45, 10))
        advanceUntilIdle()
        val won = repo.current.first()!!
        assertNotNull(won.finishedAt)
        assertEquals(listOf(won), repo.history.first())

        vm.undoLastRound()
        advanceUntilIdle()
        assertNull(repo.current.first()!!.finishedAt)
        assertTrue("undoing the winning round takes it out again", repo.history.first().isEmpty())
    }

    @Test
    fun aRematchKeepsTheFinishedGame() = runTest(dispatcher) {
        val vm = viewModel()
        newGame(vm)
        vm.addRound(listOf(100, 0))
        advanceUntilIdle()
        val old = repo.current.first()!!

        vm.rematch()
        advanceUntilIdle()

        val fresh = repo.current.first()!!
        assertNotEquals(old.id, fresh.id)
        assertTrue(fresh.rounds.isEmpty())
        assertEquals(old.players, fresh.players)
        assertEquals(listOf(old), repo.history.first())
    }

    @Test
    fun quickEditsInARowAllCount() = runTest(dispatcher) {
        val vm = viewModel()
        newGame(vm)

        // No waiting in between, so the screen's state hasn't caught up with the first one yet.
        vm.addRound(listOf(10, 0))
        vm.addRound(listOf(5, 20))
        vm.updateRound(0, listOf(15, 0))
        advanceUntilIdle()

        assertEquals(listOf(listOf(15, 0), listOf(5, 20)), repo.current.first()!!.rounds)
    }

    @Test
    fun aRoundThatIsAlreadyGoneIsIgnored() = runTest(dispatcher) {
        val vm = viewModel()
        newGame(vm)
        vm.addRound(listOf(10, 0))
        vm.deleteRound(0)
        vm.updateRound(0, listOf(1, 1))
        advanceUntilIdle()

        assertTrue(repo.current.first()!!.rounds.isEmpty())
    }

    @Test
    fun aTieAfterTheLastRoundIsCalledATieBreak() {
        val game = ScoreGame(1, listOf("Ann", "Bo"), ScoreMode.LOWEST_AFTER_ROUNDS, limit = 2, startedAt = 0)
        assertEquals("Round 1 of 2", scoreGameTitle(game))
        val tied = game.addRound(listOf(5, 3)).addRound(listOf(0, 2))
        assertEquals("Tie-break round", scoreGameTitle(tied))
        assertEquals("Round 2 of 2", scoreGameTitle(tied.addRound(listOf(0, 1))))
    }
}
