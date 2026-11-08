package app.getkosha.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import app.getkosha.data.Kosha
import app.getkosha.model.KoshaJson
import app.getkosha.network.AuthException
import app.getkosha.network.PendingSignIn
import app.getkosha.network.await
import app.getkosha.network.redirectUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/** Where a sign-in is. */
sealed interface Step {
    data object Idle : Step
    /** The browser is open on the server's sign-in page. */
    data class Browsing(val host: String) : Step
    /** Back from the browser: exchanging the code, then loading the account. [done] counts the finished lines. */
    data class Finishing(val host: String, val done: Int) : Step
    /** The browser closed without an answer, or the server refused. Nothing was changed. */
    data class Failed(val host: String, val message: String?) : Step
}

/**
 * Sign-in through the system browser. The pending request, with its PKCE verifier, is kept on disk so a sign-in
 * survives the app being closed while the browser is open.
 *
 * The server answers on its HTTPS callback (`<server>/app/oauth/callback`). An Auth Tab hands that answer straight
 * back ([authResult]) when the browser supports one and the server is HTTPS; otherwise a Custom Tab opens and the
 * answer arrives as an App Link (getkosha.app), or as app.getkosha:/oauth/callback after the server page's "Open
 * Kosha" button. MainActivity passes both to [redirect].
 */
object SignInFlow {
    private val _step = MutableStateFlow<Step>(Step.Idle)
    val step: StateFlow<Step> = _step.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("kosha.signin", Context.MODE_PRIVATE)

    private fun host(server: String) = runCatching { server.toHttpUrl().host }.getOrDefault(server)

    /** Checks a server: it must answer OAuth discovery and have the app endpoints (Kosha from November 2026 on). */
    suspend fun check(server: String): Check {
        val kosha = Kosha.get()
        return try {
            val found = kosha.auth.discover(server)
            // The app endpoints answer 401 without a token; a server from before them answers 404.
            val app = kosha.http.newCall(Request.Builder().url("$server/api/app/meta").build()).await().use { it.code }
            if (app == 401) Check.Found(found) else Check.TooOld
        } catch (e: AuthException) {
            Check.NotFound
        } catch (e: IOException) {
            Check.NotFound
        }
    }

    sealed interface Check {
        data class Found(val found: PendingSignIn) : Check
        data object NotFound : Check
        data object TooOld : Check
    }

    /** Opens the server's sign-in page. `demo` signs in to the demo account. */
    suspend fun begin(activity: Activity, server: String, demo: Boolean = false) {
        val kosha = Kosha.get()
        val found = when (val c = check(server)) {
            is Check.Found -> c.found
            Check.TooOld -> return fail(server, null)
            Check.NotFound -> return fail(server, null)
        }
        val pending = kosha.auth.start(found, demo)
        prefs(activity).edit().putString("pending", KoshaJson.encodeToString(PendingSignIn.serializer(), pending)).commit()
        _step.value = Step.Browsing(host(server))
        val callback = Uri.parse(redirectUri(pending.server))
        val browser = CustomTabsClient.getPackageName(activity, null)
        val tab = authTab
        if (tab != null && callback.scheme == "https" && browser != null && CustomTabsClient.isAuthTabSupported(activity, browser)) {
            AuthTabIntent.Builder().build().launch(tab, Uri.parse(pending.url), callback.host!!, callback.path!!)
        } else {
            customTab(activity, pending.url)
        }
    }

    private fun customTab(context: Context, url: String) =
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))

    /** The Auth Tab launcher, registered by MainActivity when it is created; null while there is no activity. */
    var authTab: ActivityResultLauncher<Intent>? = null

    /**
     * The Auth Tab closed. An answer finishes the sign-in. If the browser couldn't verify that the app owns the
     * server's callback (a server without assetlinks.json), the sign-in reopens in a Custom Tab, where the server's
     * page offers "Open Kosha". A cancel may still be followed by that button's link, so it waits like [resumed].
     */
    fun authResult(context: Context, result: AuthTabIntent.AuthResult) {
        when (result.resultCode) {
            AuthTabIntent.RESULT_OK -> result.resultUri?.let { redirect(context, it) }
            AuthTabIntent.RESULT_VERIFICATION_FAILED, AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT -> {
                val pending = stored(context) ?: return
                customTab(context, pending.url)
            }
            else -> resumed()
        }
    }

    private fun stored(context: Context): PendingSignIn? =
        prefs(context).getString("pending", null)?.let { runCatching { KoshaJson.decodeFromString(PendingSignIn.serializer(), it) }.getOrNull() }

    /** The browser handed back the redirect. */
    fun redirect(context: Context, uri: Uri) {
        val kosha = Kosha.get()
        val pending = stored(context) ?: return
        val host = host(pending.server)
        _step.value = Step.Finishing(host, 0)
        kosha.scope.launch {
            try {
                kosha.auth.finish(pending, uri.toString())
                prefs(context).edit().remove("pending").apply()
                _step.value = Step.Finishing(host, 1)
                kosha.signedIn()
                withTimeoutOrNull(20_000) { kosha.meta.filterNotNull().first() }
                _step.value = Step.Finishing(host, 2)
                withTimeoutOrNull(10_000) { kosha.settings.filterNotNull().first() }
                _step.value = Step.Finishing(host, 3)
                delay(400)
                _step.value = Step.Idle
            } catch (e: Exception) {
                _step.value = Step.Failed(host, (e as? AuthException)?.message?.takeIf { it.length < 200 })
            }
        }
    }

    /** The app came back to the front. If the browser closed without a redirect, the sign-in didn't finish. */
    fun resumed() {
        val s = _step.value as? Step.Browsing ?: return
        Kosha.get().scope.launch {
            delay(800)
            if (_step.value == s) _step.value = Step.Failed(s.host, null)
        }
    }

    fun cancel() {
        _step.value = Step.Idle
    }

    private fun fail(server: String, message: String?) {
        _step.value = Step.Failed(host(server), message)
    }
}
