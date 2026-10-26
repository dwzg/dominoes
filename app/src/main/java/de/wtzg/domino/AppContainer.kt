package de.wtzg.domino

import android.content.Context
import de.wtzg.domino.data.SavedGameRepository
import de.wtzg.domino.data.ScoreKeeperRepository
import de.wtzg.domino.data.SettingsRepository
import de.wtzg.domino.data.StatsRepository
import de.wtzg.domino.data.appStore
import de.wtzg.domino.data.local.LocalHost
import de.wtzg.domino.data.online.OnlineRepository
import de.wtzg.domino.data.online.onlineHttpClient
import de.wtzg.domino.data.scoreKeeperStore
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency injection: one place that builds the app's repositories. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsRepository(app.appStore)
    val stats = StatsRepository(app.appStore)
    val savedGame = SavedGameRepository(app.appStore)
    val scoreKeeper = ScoreKeeperRepository(app.scoreKeeperStore)
    val online by lazy { OnlineRepository(app.appStore, settings, onlineHttpClient()) }

    /** Games on the same Wi-Fi: the phone this one hosts, or the host it joined. */
    val localServer = MutableStateFlow("")
    val localHost by lazy { LocalHost() }

    /** Plain HTTP to local addresses only, so the CIO client rather than OkHttp; see LocalPlay.kt. */
    val local by lazy { OnlineRepository(app.appStore, settings, onlineHttpClient(CIO.create()), server = localServer) }
}
