package app.getkosha.accounts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route

/** The accounts screens, registered in the app's NavHost. */
fun NavGraphBuilder.accountsScreens() {
    composable<Route.Accounts> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
    composable<Route.AccountDetail> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
    composable<Route.InvestmentDetail> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
    composable<Route.AccountForm> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
    composable<Route.FixBalance> { Placeholder(stringResource(R.string.shell_nav_accounts)) }
    composable<Route.Exchanges> { Placeholder(stringResource(R.string.accounts_fx_title)) }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Page(title, onBack = { nav.popBackStack() }) { }
}
