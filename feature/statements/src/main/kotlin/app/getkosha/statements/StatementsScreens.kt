package app.getkosha.statements

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.ui.Route

/** Reconcile and Import, registered in the app's NavHost. Each flow's steps are its own state, not routes. */
fun NavGraphBuilder.statementsScreens() {
    composable<Route.Reconcile> { ReconcileScreen(it.toRoute<Route.Reconcile>().accountId) }
    composable<Route.Import> { ImportScreen() }
}
