package de.wtzg.domino.feature.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.local.LocalHost
import de.wtzg.domino.data.local.LocalInvite
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.data.online.onlineHttpClient
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameStatus
import de.wtzg.domino.protocol.Pace
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** One phone hosts a game on its own server, another joins it through the same-Wi-Fi screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalGameEndToEndTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The test machine may have no Wi-Fi; the host is reached on this machine instead.
    private val host = LocalHost(address = { "127.0.0.1" })

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun tearDown() {
        runBlocking { host.stop() }
        scope.cancel()
        Dispatchers.resetMain()
    }

    private class Phone(val repo: OnlineRepository, val server: MutableStateFlow<String>, val settings: SettingsRepository)

    private fun phone(name: String): Phone {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("$name.preferences_pb") }
        val server = MutableStateFlow("")
        val settings = SettingsRepository(store)
        return Phone(OnlineRepository(store, settings, onlineHttpClient(CIO.create(), appVersion = "test"), server = server), server, settings)
    }

    private suspend fun <T> eventually(read: () -> T?): T = withTimeout(10_000) {
        var value = read()
        while (value == null) {
            delay(20)
            value = read()
        }
        value
    }

    @Test
    fun hostAndGuestMeetAtTheSameTable() = runBlocking {
        val ann = phone("ann")
        val annVm = LocalViewModel(ann.repo, ann.server, host, ann.settings)
        eventually { annVm.state.value.name }
        annVm.setName("Ann")
        annVm.host()
        eventually { annVm.state.value.create.takeIf { it } }
        val url = checkNotNull(host.url.value) { "hosting started" }
        assertEquals(url, annVm.state.value.hosting)
        val lobby = ann.repo.api().create(CreateGameRequest(GameMode.ALL_FIVES, 2, 100, Pace.LIVE))
        assertEquals(GameStatus.LOBBY, lobby.status)

        // Bob opens Ann's invite with his name already known, so he joins straight away.
        val bob = phone("bob")
        bob.settings.update { it.copy(playerName = "Bob") }
        val bobVm = LocalViewModel(bob.repo, bob.server, LocalHost(), bob.settings, LocalInvite(url, lobby.code))
        val opened = eventually { bobVm.state.value.open }
        assertEquals(lobby.id, opened)
        assertNull(bobVm.state.value.error)

        val seen = ann.repo.api().game(lobby.id)
        assertEquals(listOf("Ann", "Bob"), seen.seats.map { it.name })

        // Stopping ends hosting; the address is gone.
        annVm.stopHosting()
        eventually { if (host.url.value == null) true else null }
        Unit
    }
}
