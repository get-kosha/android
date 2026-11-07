package app.getkosha.recurring

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.ui.Route

/** The recurring screens, registered in the app's NavHost. */
fun NavGraphBuilder.recurringScreens() {
    composable<Route.Recurring> { RecurringScreen() }
    composable<Route.RecurringDetail> { RecurringDetailScreen(it.toRoute<Route.RecurringDetail>().id) }
    composable<Route.RecurringForm> {
        val r = it.toRoute<Route.RecurringForm>()
        RecurringFormScreen(r.id, r.fromPayee, r.fromTransaction)
    }
}
