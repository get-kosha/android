package app.getkosha

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.getkosha.accounts.accountsScreens
import app.getkosha.activity.activityScreens
import app.getkosha.add.addScreens
import app.getkosha.auth.AppLock
import app.getkosha.auth.LockScreen
import app.getkosha.auth.SignInFlow
import app.getkosha.auth.SignedOut
import app.getkosha.auth.Step
import app.getkosha.design.KoshaTheme
import app.getkosha.design.R
import app.getkosha.design.Tab
import app.getkosha.design.TabBar
import app.getkosha.design.Toast
import app.getkosha.design.ToastHost
import app.getkosha.overview.overviewScreens
import app.getkosha.recurring.recurringScreens
import app.getkosha.settings.settingsScreens
import app.getkosha.statements.statementsScreens
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import app.getkosha.welcome.welcomeScreens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

/** Signed out, signing in, locked, or the app. */
@Composable
fun Root(pending: MutableStateFlow<Route?>) {
    val kosha = rememberKosha()
    val signedIn by kosha.signedIn.collectAsStateWithLifecycle()
    val step by SignInFlow.step.collectAsStateWithLifecycle()
    val locked by AppLock.locked.collectAsStateWithLifecycle()
    val toasts = remember { app.getkosha.design.ToastState() }
    CompositionLocalProvider(LocalToasts provides toasts) {
        Box(Modifier.fillMaxSize().background(KoshaTheme.colors.bg)) {
            when {
                !signedIn || step is Step.Finishing -> SignedOut()
                locked -> LockScreen()
                else -> Main(pending)
            }
            ToastHost(toasts, Modifier.align(Alignment.TopCenter))
        }
    }
}

private val TABS: List<KClass<out Route>> = listOf(Route.Overview::class, Route.Activity::class, Route.Recurring::class, Route.Accounts::class)

@Composable
private fun Main(pending: MutableStateFlow<Route?>) {
    val kosha = rememberKosha()
    val m = meta()
    val toasts = LocalToasts.current
    val nav = rememberNavController()
    if (m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingNote() }
        return
    }
    // A new account with no accounts yet starts on Welcome, as the web does.
    val start: Route = remember { if (m.wallets.isEmpty()) Route.Welcome else Route.Overview }
    LaunchedEffect(Unit) { kosha.outbox.rejected.collect { toasts.show(Toast(it)) } }
    val asked by pending.collectAsStateWithLifecycle()
    LaunchedEffect(asked) {
        // Single top, so sharing statements while Import is open hands them to the open one.
        asked?.let { nav.navigate(it) { launchSingleTop = true }; pending.value = null }
    }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    val tab = TABS.firstOrNull { current?.hasRoute(it) == true }
    CompositionLocalProvider(LocalNav provides nav) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                nav,
                startDestination = start,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                overviewScreens()
                activityScreens()
                recurringScreens()
                accountsScreens()
                addScreens()
                statementsScreens()
                settingsScreens()
                welcomeScreens()
            }
            if (tab != null) {
                val tabs = listOf(
                    Tab<KClass<out Route>>(Route.Overview::class, stringResource(R.string.shell_nav_overview), "chart-pie-slice"),
                    Tab<KClass<out Route>>(Route.Activity::class, stringResource(R.string.shell_nav_activity), "list-bullets"),
                    Tab<KClass<out Route>>(Route.Recurring::class, stringResource(R.string.shell_nav_recurring), "arrows-clockwise"),
                    Tab<KClass<out Route>>(Route.Accounts::class, stringResource(R.string.shell_nav_accounts), "bank"),
                )
                TabBar(
                    tabs,
                    tab,
                    { picked ->
                        val route: Route = when (picked) {
                            Route.Activity::class -> Route.Activity
                            Route.Recurring::class -> Route.Recurring
                            Route.Accounts::class -> Route.Accounts
                            else -> Route.Overview
                        }
                        nav.navigate(route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    stringResource(R.string.add_heading),
                    { nav.navigate(Route.Add()) },
                    Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
