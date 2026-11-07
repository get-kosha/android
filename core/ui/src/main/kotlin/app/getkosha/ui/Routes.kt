package app.getkosha.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

/**
 * Every screen the app navigates to. Each feature module registers its own in a `NavGraphBuilder.xxxScreens()`
 * function that the app's NavHost calls; any screen can open any other through [LocalNav]. Sheets aren't routes:
 * they live in the screen that opens them.
 */
sealed interface Route {
    // The four tabs.
    @Serializable data object Overview : Route
    @Serializable data object Activity : Route
    @Serializable data object Recurring : Route
    @Serializable data object Accounts : Route

    /** Add, full screen over the tabs. `kind` is "expense", "income" or "transfer"; `scan` opens the receipt camera. */
    @Serializable data class Add(val kind: String? = null, val scan: Boolean = false, val duplicateOf: String? = null) : Route

    // Activity
    @Serializable data class TagView(val tag: String? = null, val eventId: String? = null) : Route
    @Serializable data class TxDetail(val id: String) : Route
    @Serializable data class TransferDetail(val id: String) : Route

    // Recurring
    @Serializable data class RecurringDetail(val id: String) : Route
    /** A new schedule, or an edit with [id]. [fromPayee] starts it from a "Looks recurring" suggestion or an entry. */
    @Serializable data class RecurringForm(val id: String? = null, val fromPayee: String? = null, val fromTransaction: String? = null) : Route

    // Accounts
    @Serializable data class AccountDetail(val id: String) : Route
    @Serializable data class InvestmentDetail(val id: String) : Route
    @Serializable data class AccountForm(val id: String? = null) : Route
    @Serializable data class FixBalance(val id: String) : Route
    @Serializable data object Exchanges : Route
    @Serializable data class Reconcile(val accountId: String? = null) : Route
    @Serializable data object Import : Route

    // Ask Kosha (AI)
    @Serializable data class Ask(val question: String? = null) : Route

    /**
     * Settings. [page] names a section: "" for the list, then "profile", "security", "general", "appearance",
     * "currencies", "categories", "category" (with [id]), "events", "rules", "recurring", "ai", "ai-provider"
     * (with [id]), "ai-privacy", "ai-log", "data", "api", "delete".
     */
    @Serializable data class Settings(val page: String = "", val id: String? = null) : Route

    /** First run: name, currency and time zone, then accounts, then done. */
    @Serializable data object Welcome : Route
}

/** The app's NavController, for any screen to open another: `LocalNav.current.navigate(Route.TxDetail(id))`. */
val LocalNav = staticCompositionLocalOf<NavController> { error("No NavController") }

/** The authority of the app's one FileProvider (core:ui's manifest), for files under cache/attachments, camera, exports and shared. */
fun android.content.Context.filesAuthority() = "$packageName.files"
