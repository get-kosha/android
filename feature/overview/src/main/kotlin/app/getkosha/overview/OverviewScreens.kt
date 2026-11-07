package app.getkosha.overview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route

/** The overview screens, registered in the app's NavHost. */
fun NavGraphBuilder.overviewScreens() {
    composable<Route.Overview> { Placeholder(stringResource(R.string.shell_nav_overview)) }
    composable<Route.Ask> { Placeholder(stringResource(R.string.shell_ask_kosha)) }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Page(title, onBack = { nav.popBackStack() }) { }
}
