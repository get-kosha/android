package app.getkosha.activity

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.ui.Route

/** The activity screens, registered in the app's NavHost. */
fun NavGraphBuilder.activityScreens() {
    composable<Route.Activity> { ActivityScreen() }
    composable<Route.TagView> { it.toRoute<Route.TagView>().let { r -> TagViewScreen(r.tag, r.eventId) } }
    composable<Route.TxDetail> { TxDetailScreen(it.toRoute<Route.TxDetail>().id) }
    composable<Route.TransferDetail> { TransferDetailScreen(it.toRoute<Route.TransferDetail>().id) }
}
