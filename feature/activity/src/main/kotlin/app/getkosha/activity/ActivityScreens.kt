package app.getkosha.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route

/** The activity screens, registered in the app's NavHost. */
fun NavGraphBuilder.activityScreens() {
    composable<Route.Activity> { Placeholder(stringResource(R.string.shell_nav_activity)) }
    composable<Route.TagView> { Placeholder(stringResource(R.string.tx_activity)) }
    composable<Route.TxDetail> { Placeholder(stringResource(R.string.tx_activity)) }
    composable<Route.TransferDetail> { Placeholder(stringResource(R.string.tx_activity)) }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Page(title, onBack = { nav.popBackStack() }) { }
}
