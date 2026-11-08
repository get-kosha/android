package app.getkosha

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.getkosha.data.Kosha
import app.getkosha.model.KoshaJson
import app.getkosha.network.CLIENT_ID
import app.getkosha.network.CALLBACK_PATH
import app.getkosha.network.redirectUri
import app.getkosha.network.Tokens
import app.getkosha.ui.Route
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Signs in to a Kosha dev server as the read-only demo account (the same OAuth PKCE flow the browser runs), then
 * opens every main screen, in light and dark, and saves a screenshot of each to the app's external files
 * (`adb pull /sdcard/Android/data/app.getkosha/files/tour`). Fails if any screen crashes. Needs `npm run dev`
 * in the web repo and `adb reverse tcp:3000 tcp:3000`; skipped when the server can't be reached.
 */
@RunWith(AndroidJUnit4::class)
class TourTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val server = InstrumentationRegistry.getArguments().getString("kosha.server") ?: "http://localhost:3000"
    private val http = OkHttpClient.Builder().followRedirects(false).build()
    private val shots = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tour").apply { mkdirs() }

    @Before fun signIn() {
        val reachable = runCatching { http.newCall(Request.Builder().url("$server/api/app/meta").build()).execute().use { it.code == 401 } }.getOrDefault(false)
        assumeTrue("No Kosha dev server at $server", reachable)
        if (Kosha.get().tokens.read() != null) return
        val verifier = b64(ByteArray(32).also(SecureRandom()::nextBytes))
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val authorize = "$server/api/auth/oauth2/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code").addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", redirectUri(server)).addQueryParameter("scope", "openid profile email offline_access kosha:read kosha:write")
            .addQueryParameter("code_challenge", challenge).addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", "tour").addQueryParameter("resource", "$server/api/v1")
            .addQueryParameter("login_hint", "demo").addQueryParameter("prompt", "login").build()
        val cookies = mutableListOf<String>()
        fun browser(url: String, body: String? = null): okhttp3.Response {
            val b = Request.Builder().url(url).header("accept", if (body == null) "text/html" else "application/json")
                .header("sec-fetch-site", "same-origin").header("origin", server)
            if (cookies.isNotEmpty()) b.header("cookie", cookies.joinToString("; "))
            if (body != null) b.post(body.toRequestBody("application/json".toMediaType()))
            return http.newCall(b.build()).execute().also { r -> r.headers("set-cookie").forEach { cookies += it.substringBefore(';') } }
        }
        val login = browser(authorize.toString()).use { r -> r.header("location") ?: KoshaJson.parseToJsonElement(r.body.string()).jsonObject["url"]!!.jsonPrimitive.content }
        val query = login.substringAfter('?')
        var next = browser("$server/api/auth/sign-in/email", """{"email":"demo@kosha.local","password":"kosha-demo-2026","oauth_query":${KoshaJson.encodeToString(kotlinx.serialization.serializer<String>(), query)}}""")
            .use { r -> KoshaJson.parseToJsonElement(r.body.string()).jsonObject["url"]?.jsonPrimitive?.content ?: r.header("location") }
        repeat(5) { if (next != null && CALLBACK_PATH !in next!!) next = browser(if (next!!.startsWith("http")) next!! else server + next).use { it.header("location") } }
        val code = (if (next!!.startsWith("http")) next!! else server + next).toHttpUrl().queryParameter("code")!!
        val tokens = http.newCall(
            Request.Builder().url("$server/api/auth/oauth2/token").post(
                FormBody.Builder().add("grant_type", "authorization_code").add("code", code).add("redirect_uri", redirectUri(server))
                    .add("client_id", CLIENT_ID).add("code_verifier", verifier).add("resource", "$server/api/v1").build(),
            ).build(),
        ).execute().use { KoshaJson.parseToJsonElement(it.body.string()).jsonObject }
        Kosha.get().tokens.write(
            Tokens(server, CLIENT_ID, tokens["access_token"]!!.jsonPrimitive.content, tokens["refresh_token"]!!.jsonPrimitive.content, System.currentTimeMillis() / 1000 + 3600),
        )
        rule.runOnUiThread { Kosha.get().signedIn() }
    }

    @Test fun tour() {
        rule.waitUntil(30_000) { Kosha.get().meta.value != null }
        val meta = Kosha.get().meta.value!!
        val wallet = meta.wallets.firstOrNull()
        val investment = meta.wallets.firstOrNull { it.type == app.getkosha.model.AccountType.Investment }
        val screens = buildList {
            add("overview" to Route.Overview)
            add("activity" to Route.Activity)
            add("recurring" to Route.Recurring)
            add("accounts" to Route.Accounts)
            add("add" to Route.Add())
            add("add-transfer" to Route.Add(kind = "transfer"))
            wallet?.let { add("account-detail" to Route.AccountDetail(it.id)) }
            investment?.let { add("investment-detail" to Route.InvestmentDetail(it.id)) }
            add("account-form" to Route.AccountForm())
            add("exchanges" to Route.Exchanges)
            add("reconcile" to Route.Reconcile())
            add("import" to Route.Import)
            add("recurring-form" to Route.RecurringForm())
            if (meta.ai.ask) add("ask" to Route.Ask())
            listOf("", "profile", "security", "general", "appearance", "currencies", "categories", "events", "rules",
                "recurring", "ai", "ai-privacy", "ai-log", "data", "api", "delete").forEach { add("settings-${it.ifEmpty { "list" }}" to Route.Settings(it)) }
        }
        for (night in listOf(false, true)) {
            shell("cmd uimode night ${if (night) "yes" else "no"}")
            Thread.sleep(1500)
            for ((name, route) in screens) {
                rule.runOnUiThread { rule.activity.pending.value = route }
                Thread.sleep(2500)
                rule.waitForIdle()
                save("${if (night) "dark" else "light"}-$name")
            }
        }
        shell("cmd uimode night no")
    }

    private fun save(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
