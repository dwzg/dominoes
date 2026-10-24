package de.wtzg.domino.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.Flow
import de.wtzg.domino.feature.online.OnlineCreateScreen
import de.wtzg.domino.feature.online.OnlineGameScreen
import de.wtzg.domino.feature.online.OnlineGameViewModel
import de.wtzg.domino.feature.online.OnlineHomeScreen
import de.wtzg.domino.feature.online.OnlineHomeViewModel
import de.wtzg.domino.AppViewModel
import de.wtzg.domino.data.Settings
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.feature.learn.LearnScreen
import de.wtzg.domino.feature.learn.LessonScreen
import de.wtzg.domino.feature.learn.PuzzleScreen
import de.wtzg.domino.feature.learn.PuzzleViewModel
import de.wtzg.domino.feature.more.MoreScreen
import de.wtzg.domino.feature.play.GameScreen
import de.wtzg.domino.feature.play.GameSetupScreen
import de.wtzg.domino.feature.play.GameViewModel
import de.wtzg.domino.feature.play.PlayHomeScreen
import de.wtzg.domino.feature.play.QuitGameMenu
import de.wtzg.domino.feature.play.toTable
import de.wtzg.domino.feature.scorekeeper.ScoreGameScreen
import de.wtzg.domino.feature.scorekeeper.ScoreHistoryScreen
import de.wtzg.domino.feature.scorekeeper.ScoreKeeperViewModel
import de.wtzg.domino.feature.scorekeeper.ScoreSetupScreen
import kotlin.reflect.KClass

private enum class Tab(val label: String, val icon: ImageVector, val route: Any, val routeClass: KClass<*>) {
    PLAY("Play", Icons.Outlined.Casino, PlayRoute, PlayRoute::class),
    LEARN("Learn", Icons.Outlined.School, LearnRoute, LearnRoute::class),
    SCORES("Scores", Icons.AutoMirrored.Outlined.EventNote, ScoresRoute, ScoresRoute::class),
    MORE("More", Icons.Outlined.Tune, MoreRoute, MoreRoute::class),
}

@Composable
fun DominoApp(appVm: AppViewModel, settings: Settings, intents: Flow<Intent>) {
    val nav = rememberNavController()
    // Links that arrive while the app is already open (invites, notifications).
    LaunchedEffect(nav) { intents.collect { nav.handleDeepLink(it) } }
    // Shared across screens: the game in progress and the score keeper.
    val gameVm = appViewModel { GameViewModel(it.savedGame, it.stats, it.settings) }
    val scoresVm = appViewModel { ScoreKeeperViewModel(it.scoreKeeper) }

    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    val tab = Tab.entries.firstOrNull { t -> current?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }
    val showNav = tab != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth > maxHeight
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showNav && !rail) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == tab,
                                onClick = { nav.goToTab(t) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                if (showNav && rail) {
                    NavigationRail {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = t == tab,
                                onClick = { nav.goToTab(t) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    AppNavHost(nav, appVm, gameVm, scoresVm, settings)
                }
            }
        }
    }
}

private fun NavHostController.goToTab(t: Tab) = navigate(t.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    appVm: AppViewModel,
    gameVm: GameViewModel,
    scoresVm: ScoreKeeperViewModel,
    settings: Settings,
) {
    val stats by appVm.stats.collectAsStateWithLifecycle()
    val game by gameVm.state.collectAsStateWithLifecycle()

    NavHost(nav, startDestination = PlayRoute) {
        composable<PlayRoute> {
            PlayHomeScreen(
                saved = game.game,
                onContinue = { nav.navigate(GameRoute) },
                onPickMode = { nav.navigate(SetupRoute(it.name)) },
                onLearn = { nav.goToTab(Tab.LEARN) },
                onOnline = { nav.navigate(OnlineRoute()) },
            )
        }
        composable<SetupRoute> { e ->
            val mode = GameMode.valueOf(e.toRoute<SetupRoute>().mode)
            GameSetupScreen(
                mode = mode,
                defaultCoach = settings.coach,
                hasGameInProgress = game.game?.match?.isOver == false,
                onBack = { nav.popBackStack() },
                onStart = { rules, options ->
                    gameVm.start(rules, options)
                    nav.navigate(GameRoute) { popUpTo<PlayRoute>() }
                },
            )
        }
        composable<GameRoute> {
            val table = game.toTable()
            if (table == null) {
                // Nothing to show (e.g. the game was quit); go back to the start.
                LaunchedEffect(Unit) { nav.popBackStack(PlayRoute, inclusive = false) }
            } else {
                GameScreen(
                    table = table,
                    actions = gameVm,
                    haptics = settings.haptics,
                    dimHandWhileWaiting = settings.dimHandWhileWaiting,
                    onBack = { nav.popBackStack() },
                    onOverlaySecondary = {
                        gameVm.quit()
                        nav.popBackStack(PlayRoute, inclusive = false)
                    },
                    topActions = {
                        QuitGameMenu(enabled = game.match?.isOver == false) {
                            gameVm.quit()
                            nav.popBackStack(PlayRoute, inclusive = false)
                        }
                    },
                )
            }
        }
        composable<LearnRoute> {
            LearnScreen(
                stats = stats,
                onOpenLesson = { nav.navigate(LessonRoute(it)) },
                onPuzzle = { nav.navigate(PuzzleRoute(it.name)) },
            )
        }
        composable<LessonRoute> { e ->
            LessonScreen(
                id = e.toRoute<LessonRoute>().id,
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate(LessonRoute(it)) { popUpTo<LearnRoute>() } },
                onPractice = { nav.navigate(SetupRoute(it.name)) },
                onRead = appVm::markLessonRead,
            )
        }
        composable<PuzzleRoute> { e ->
            val mode = GameMode.valueOf(e.toRoute<PuzzleRoute>().mode)
            val vm = appViewModel(key = "puzzle-${mode.name}") { PuzzleViewModel(mode, it.stats) }
            val state by vm.state.collectAsStateWithLifecycle()
            PuzzleScreen(state, vm::tapTile, vm::answer, vm::next, onBack = { nav.popBackStack() })
        }
        composable<ScoresRoute> {
            val s by scoresVm.state.collectAsStateWithLifecycle()
            val current = s.current
            when {
                s.loading -> Unit
                current == null -> ScoreSetupScreen(
                    initialPlayers = s.lastPlayers,
                    onStart = { p, m, l, first, rotate -> scoresVm.startGame(p, m, l, first, rotate) },
                    onShowHistory = { nav.navigate(ScoreHistoryRoute) },
                )
                else -> ScoreGameScreen(
                    game = current,
                    onAddRound = scoresVm::addRound,
                    onUpdateRound = scoresVm::updateRound,
                    onDeleteRound = scoresVm::deleteRound,
                    onUndo = scoresVm::undoLastRound,
                    onRematch = scoresVm::rematch,
                    onNewGame = { scoresVm.closeGame() },
                    onShowHistory = { nav.navigate(ScoreHistoryRoute) },
                )
            }
        }
        composable<ScoreHistoryRoute> {
            val s by scoresVm.state.collectAsStateWithLifecycle()
            ScoreHistoryScreen(
                games = s.history,
                onBack = { nav.popBackStack() },
                onDelete = { scoresVm.deleteFromHistory(it) },
                onClear = { scoresVm.clearHistory() },
            )
        }
        composable<OnlineRoute> { e ->
            OnlineHome(nav, e.toRoute<OnlineRoute>().code)
        }
        composable<JoinRoute>(deepLinks = listOf(navDeepLink<JoinRoute>(basePath = "dominoes://join"))) { e ->
            OnlineHome(nav, e.toRoute<JoinRoute>().code)
        }
        composable<OnlineCreateRoute> {
            val vm = appViewModel(owner = onlineOwner(nav)) { OnlineHomeViewModel(it.online, it.settings) }
            val s by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(s.open) {
                s.open?.let { id ->
                    vm.opened()
                    nav.navigate(OnlineGameRoute(id)) { popUpTo<OnlineCreateRoute> { inclusive = true } }
                }
            }
            OnlineCreateScreen(busy = s.busy, onBack = { nav.popBackStack() }, onCreate = vm::create)
        }
        composable<OnlineGameRoute>(deepLinks = listOf(navDeepLink<OnlineGameRoute>(basePath = "dominoes://game"))) { e ->
            val id = e.toRoute<OnlineGameRoute>().id
            val vm = appViewModel(key = "online-$id") { OnlineGameViewModel(id, it.online) }
            val s by vm.state.collectAsStateWithLifecycle()
            OnlineGameScreen(s, vm, haptics = settings.haptics, dimHandWhileWaiting = settings.dimHandWhileWaiting, onBack = { nav.popBackStack() })
        }
        composable<MoreRoute> {
            MoreScreen(
                stats = stats,
                settings = settings,
                onSettings = { appVm.updateSettings(it) },
                onResetStats = { appVm.resetStats() },
            )
        }
    }
}

/** The online games list is shared with the create screen, so it lives on the list's back stack entry. */
@Composable
private fun onlineOwner(nav: NavHostController): androidx.lifecycle.ViewModelStoreOwner =
    remember(nav) { runCatching { nav.getBackStackEntry<OnlineRoute>() }.getOrNull() } ?: checkNotNull(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current)

@Composable
private fun OnlineHome(nav: NavHostController, code: String?) {
    val vm = appViewModel { OnlineHomeViewModel(it.online, it.settings) }
    val s by vm.state.collectAsStateWithLifecycle()
    OnlineHomeScreen(
        state = s,
        vm = vm,
        initialCode = code,
        onBack = { if (!nav.popBackStack()) nav.navigate(PlayRoute) },
        onCreate = { nav.navigate(OnlineCreateRoute) },
        onOpenGame = { nav.navigate(OnlineGameRoute(it)) },
    )
}
