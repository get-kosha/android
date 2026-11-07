package app.getkosha.overview

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.ui.Route

/** The overview screens, registered in the app's NavHost: Overview and Ask Kosha. */
fun NavGraphBuilder.overviewScreens() {
    composable<Route.Overview> { OverviewScreen() }
    composable<Route.Ask> { AskScreen(it.toRoute<Route.Ask>().question) }
}
