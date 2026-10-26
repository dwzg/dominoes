package de.wtzg.domino.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDeepLinkRequest
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
import de.wtzg.domino.AppViewModel
import de.wtzg.domino.data.Settings
import de.wtzg.domino.data.local.LocalInvite
import de.wtzg.domino.data.local.isLocalNetworkAddress
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.feature.learn.LearnScreen
import de.wtzg.domino.feature.learn.LessonScreen
import de.wtzg.domino.feature.learn.PuzzleScreen
import de.wtzg.domino.feature.learn.PuzzleViewModel
import de.wtzg.domino.feature.local.LocalScreen
import de.wtzg.domino.feature.local.LocalViewModel
import de.wtzg.domino.feature.more.MoreScreen
import de.wtzg.domino.feature.online.CreateGameViewModel
import de.wtzg.domino.feature.online.FriendsScreen
import de.wtzg.domino.feature.online.FriendsViewModel
import de.wtzg.domino.feature.online.OnlineCreateScreen
import de.wtzg.domino.feature.online.OnlineGameScreen
import de.wtzg.domino.feature.online.OnlineGameViewModel
import de.wtzg.domino.feature.online.OnlineHomeScreen
import de.wtzg.domino.feature.online.OnlineHomeViewModel
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
import de.wtzg.domino.protocol.Friend
import kotlin.reflect.KClass

private enum class Tab(val label: String, val icon: ImageVector, val route: Any, val routeClass: KClass<*>) {
    PLAY("Play", Icons.Outlined.Casino, PlayRoute, PlayRoute::class),
    LEARN("Learn", Icons.Outlined.School, LearnRoute, LearnRoute::class),
    SCORES("Scores", Icons.AutoMirrored.Outlined.EventNote, ScoresRoute, ScoresRoute::class),
    MORE("More", Icons.Outlined.Tune, MoreRoute, MoreRoute::class),
}

@Composable
fun DominoApp(appVm: AppViewModel, settings: Settings) {
    val nav = rememberNavController()
    OpenLinksWhileRunning(nav)
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

/**
 * Links that arrive while the app is open (invites, notifications) open on top of what's
 * there. (handleDeepLink would rebuild the whole back stack and restart the activity.)
 * The link that started the app is handled by the NavHost itself.
 */
@Composable
private fun OpenLinksWhileRunning(nav: NavHostController) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity, nav) {
        val listener = Consumer<Intent> { intent ->
            val uri = intent.data ?: return@Consumer
            val request = NavDeepLinkRequest.Builder.fromUri(uri).build()
            if (nav.graph.hasDeepLink(request)) nav.navigate(request)
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
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
                onLocal = { nav.navigate(LocalRoute()) },
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
            val vm = appViewModel { PuzzleViewModel(mode, it.stats) }
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
        composable<OnlineRoute>(deepLinks = listOf(navDeepLink<OnlineRoute>(basePath = "dominoes://online"))) { e ->
            OnlineHome(nav, e.toRoute<OnlineRoute>().code)
        }
        composable<JoinRoute>(deepLinks = listOf(navDeepLink<JoinRoute>(basePath = "dominoes://join"))) { e ->
            val r = e.toRoute<JoinRoute>()
            val server = r.server
            // Invites to a game hosted on a phone go to the same-Wi-Fi screen, not the online server.
            if (server != null && isLocalNetworkAddress(server)) LocalHome(nav, LocalInvite(server, r.code)) else OnlineHome(nav, r.code)
        }
        composable<LocalRoute> { e ->
            val r = e.toRoute<LocalRoute>()
            val invite = if (r.server != null && r.code != null) LocalInvite(r.server, r.code) else null
            LocalHome(nav, invite)
        }
        composable<LocalCreateRoute> {
            // Everyone at a same-Wi-Fi game joins by QR code, so there's no public list to be on.
            CreateGame(nav, appViewModel { CreateGameViewModel(it.local) }, local = true, challenge = null)
        }
        composable<OnlineCreateRoute> { e ->
            val route = e.toRoute<OnlineCreateRoute>()
            val challenge = route.challengeId?.let { Friend(it, route.challengeName.orEmpty()) }
            CreateGame(nav, appViewModel { CreateGameViewModel(it.online) }, local = false, challenge = challenge)
        }
        composable<FriendsRoute>(deepLinks = listOf(navDeepLink<FriendsRoute>(basePath = "dominoes://friends"))) { e ->
            val route = e.toRoute<FriendsRoute>()
            val vm = appViewModel { FriendsViewModel(it.online, route.server) }
            val s by vm.state.collectAsStateWithLifecycle()
            FriendsScreen(
                state = s,
                vm = vm,
                initialCode = route.code,
                onBack = { if (!nav.popBackStack()) nav.navigate(PlayRoute) },
                onChallenge = { f -> nav.navigate(OnlineCreateRoute(f.playerId, f.name)) },
                onSetUp = { nav.navigate(OnlineRoute()) },
            )
        }
        composable<OnlineGameRoute>(deepLinks = listOf(navDeepLink<OnlineGameRoute>(basePath = "dominoes://game"))) { e ->
            val route = e.toRoute<OnlineGameRoute>()
            val id = route.id
            val vm = appViewModel {
                OnlineGameViewModel(id, if (route.local) it.local else it.online, local = route.local, invite = route.invite)
            }
            val s by vm.state.collectAsStateWithLifecycle()
            OnlineGameScreen(
                s,
                vm,
                haptics = settings.haptics,
                dimHandWhileWaiting = settings.dimHandWhileWaiting,
                onBack = { nav.popBackStack() },
                // The rematch replaces the finished game on the back stack.
                onOpenGame = { next -> nav.navigate(OnlineGameRoute(next, route.local)) { popUpTo<OnlineGameRoute> { inclusive = true } } },
            )
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

/** A new game; once created, its lobby replaces this screen. A [challenge] is invited from the lobby. */
@Composable
private fun CreateGame(nav: NavHostController, vm: CreateGameViewModel, local: Boolean, challenge: Friend?) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.open) {
        s.open?.let { id ->
            vm.opened()
            nav.navigate(OnlineGameRoute(id, local = local, invite = challenge?.playerId)) {
                if (local) popUpTo<LocalCreateRoute> { inclusive = true } else popUpTo<OnlineCreateRoute> { inclusive = true }
            }
        }
    }
    OnlineCreateScreen(
        busy = s.busy,
        allowPublic = s.publicGames && !local && challenge == null,
        allowTurnLimit = local || s.turnLimits,
        onBack = { nav.popBackStack() },
        onCreate = vm::create,
        challenge = challenge?.name,
        error = s.error,
        onDismissError = vm::dismissError,
    )
}

@Composable
private fun LocalHome(nav: NavHostController, invite: LocalInvite?) {
    val vm = appViewModel { LocalViewModel(it.local, it.localServer, it.localHost, it.settings, invite) }
    val s by vm.state.collectAsStateWithLifecycle()
    LocalScreen(
        state = s,
        vm = vm,
        onBack = { if (!nav.popBackStack()) nav.navigate(PlayRoute) },
        onCreate = { nav.navigate(LocalCreateRoute) },
        onOpenGame = { nav.navigate(OnlineGameRoute(it, local = true)) },
    )
}

@Composable
private fun OnlineHome(nav: NavHostController, code: String?) {
    val vm = appViewModel { OnlineHomeViewModel(it.online, it.settings) }
    val s by vm.state.collectAsStateWithLifecycle()
    OnlineHomeScreen(
        state = s,
        vm = vm,
        initialCode = code,
        onBack = { if (!nav.popBackStack()) nav.navigate(PlayRoute) },
        onCreate = { nav.navigate(OnlineCreateRoute()) },
        onOpenGame = { nav.navigate(OnlineGameRoute(it)) },
        onFriends = { nav.navigate(FriendsRoute()) },
    )
}
