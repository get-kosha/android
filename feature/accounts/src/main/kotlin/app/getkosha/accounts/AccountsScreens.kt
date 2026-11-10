package app.getkosha.accounts

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.ui.Route

/** The accounts screens, registered in the app's NavHost. */
fun NavGraphBuilder.accountsScreens() {
    composable<Route.Loans> { LoansScreen() }
    composable<Route.LoanDetail> { LoanDetailScreen(it.toRoute<Route.LoanDetail>().id) }
    composable<Route.LoanForm> { val r = it.toRoute<Route.LoanForm>(); LoanFormScreen(r.id, r.repayment) }
    composable<Route.Accounts> { AccountsScreen() }
    composable<Route.AccountDetail> { AccountDetailScreen(it.toRoute<Route.AccountDetail>().id) }
    composable<Route.InvestmentDetail> { InvestmentDetailScreen(it.toRoute<Route.InvestmentDetail>().id) }
    composable<Route.AccountForm> { AccountFormScreen(it.toRoute<Route.AccountForm>().id) }
    // Fix balance is a sheet over the account; opened by route (from Reconcile), closing it goes back.
    composable<Route.FixBalance> { AccountDetailScreen(it.toRoute<Route.FixBalance>().id, fix = true) }
    composable<Route.Exchanges> { ExchangesScreen() }
}
