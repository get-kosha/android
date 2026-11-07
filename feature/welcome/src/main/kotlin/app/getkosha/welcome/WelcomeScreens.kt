package app.getkosha.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route

/** The welcome screens, registered in the app's NavHost. */
fun NavGraphBuilder.welcomeScreens() {
    composable<Route.Welcome> { Placeholder(stringResource(R.string.settings_title)) }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Page(title, onBack = { nav.popBackStack() }) { }
}
