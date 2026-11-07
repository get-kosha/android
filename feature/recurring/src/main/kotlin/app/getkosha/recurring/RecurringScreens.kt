package app.getkosha.recurring

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route

/** The recurring screens, registered in the app's NavHost. */
fun NavGraphBuilder.recurringScreens() {
    composable<Route.Recurring> { Placeholder(stringResource(R.string.shell_nav_recurring)) }
    composable<Route.RecurringDetail> { Placeholder(stringResource(R.string.shell_nav_recurring)) }
    composable<Route.RecurringForm> { Placeholder(stringResource(R.string.shell_nav_recurring)) }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Page(title, onBack = { nav.popBackStack() }) { }
}
