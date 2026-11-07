package app.getkosha.statements

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route

/** The statements screens, registered in the app's NavHost. */
fun NavGraphBuilder.statementsScreens() {
    composable<Route.Reconcile> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
    composable<Route.Import> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Page(title, onBack = { nav.popBackStack() }) { }
}
