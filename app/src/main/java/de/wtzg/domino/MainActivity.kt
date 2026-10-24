package de.wtzg.domino

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wtzg.domino.navigation.DominoApp
import kotlinx.coroutines.flow.MutableSharedFlow
import de.wtzg.domino.ui.theme.DominoTheme

class MainActivity : ComponentActivity() {
    /** Intents that arrive while running (invite links, notifications), for the navigation to handle. */
    private val newIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntents.tryEmit(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DominoApplication).container
        val appVm = ViewModelProvider(
            this,
            viewModelFactory { initializer { AppViewModel(container.settings, container.stats) } },
        )[AppViewModel::class.java]
        // Keep the splash up until settings are loaded, so the theme doesn't flash.
        splash.setKeepOnScreenCondition { appVm.settings.value == null }

        setContent {
            val settings by appVm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            DominoTheme(theme = s.theme, dynamicColor = s.dynamicColor, felt = s.felt, tileFace = s.tileFace) {
                Surface {
                    DominoApp(appVm, s, newIntents)
                }
            }
        }
    }
}
