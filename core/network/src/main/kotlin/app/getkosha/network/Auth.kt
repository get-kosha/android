@file:UseSerializers(RoundedLong::class)

package app.getkosha.network

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import app.getkosha.model.KoshaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/** Kosha's public OAuth client on every server (migrations/0009_app_clients.sql in the web repo). */
const val CLIENT_ID = "kosha-android"
/** The sign-in hand-back on every Kosha server; the only redirect that skips consent. App Links claim it on getkosha.app. */
const val CALLBACK_PATH = "/app/oauth/callback"
/** Where the server's "Open Kosha" button sends the same answer when the phone doesn't hand the HTTPS page to the app. */
const val FALLBACK_REDIRECT = "app.getkosha:/oauth/callback"

/** The redirect sent in both the authorize and token requests, whichever way the answer reaches the app. */
fun redirectUri(server: String) = server + CALLBACK_PATH
const val DEFAULT_SERVER = "https://getkosha.app"
private const val SCOPES = "openid profile email offline_access kosha:read kosha:write"

/** What the browser must open, and what the app keeps to finish the sign-in when the browser comes back. */
@Serializable
data class PendingSignIn(
    val server: String,
    val url: String,
    val state: String,
    val verifier: String,
    val tokenUrl: String,
    val revokeUrl: String? = null,
    val issuer: String = "",
)

@Serializable
private data class Discovery(
    val issuer: String = "",
    @SerialName("authorization_endpoint") val authorize: String,
    @SerialName("token_endpoint") val token: String,
    @SerialName("revocation_endpoint") val revoke: String? = null,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val access: String,
    @SerialName("refresh_token") val refresh: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

/** The server said no for good: a refused code or refresh token. The session is over. */
class AuthException(message: String) : Exception(message)

/** The token endpoint is busy (429) or failing (5xx): keep the session and try again, after [seconds] when given. */
class RetryLaterException(val seconds: Long?, message: String) : java.io.IOException(message)

/**
 * Sign-in through the system browser: OAuth 2.1 authorization code with PKCE (RFC 7636, RFC 8252). The app never
 * shows a password form. Refreshes happen one at a time, and each rotated refresh token is stored before use.
 */
class Auth(private val http: OkHttpClient, private val store: TokenStore) {
    private val refreshing = Mutex()
    private val random = SecureRandom()

    /** A server's address as typed, as an origin: "kosha.example.com" becomes "https://kosha.example.com". */
    fun normalise(address: String): String? {
        val raw = address.trim().trimEnd('/')
        if (raw.isEmpty()) return null
        val withScheme = if ("://" in raw) raw else "https://$raw"
        val url = runCatching { withScheme.toHttpUrl() }.getOrNull() ?: return null
        // Plain http only for this computer's own server, as seen from the emulator.
        if (!url.isHttps && url.host !in setOf("localhost", "10.0.2.2", "127.0.0.1")) return null
        return "${url.scheme}://${url.host}${if (url.port == defaultPort(url.scheme)) "" else ":${url.port}"}"
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

    /** Reads the server's OAuth discovery document, which also proves it is a Kosha server. */
    suspend fun discover(server: String): PendingSignIn = withContext(Dispatchers.IO) {
        val doc = http.newCall(Request.Builder().url("$server/.well-known/oauth-authorization-server").build()).await().use { res ->
            if (!res.isSuccessful) throw AuthException("No Kosha server at $server")
            KoshaJson.decodeFromString(Discovery.serializer(), res.body.string())
        }
        PendingSignIn(server, doc.authorize, "", "", doc.token, doc.revoke, doc.issuer)
    }

    /** The browser URL for a sign-in, with a fresh PKCE verifier and state. `demo` opens the demo account. */
    fun start(found: PendingSignIn, demo: Boolean = false): PendingSignIn {
        val verifier = token(32)
        val state = token(16)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val url = found.url.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", redirectUri(found.server))
            .addQueryParameter("scope", SCOPES)
            .addQueryParameter("resource", "${found.server}/api/v1")
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .apply {
                if (demo) {
                    addQueryParameter("login_hint", "demo")
                    addQueryParameter("prompt", "login")
                }
            }
            .build()
        return found.copy(url = url.toString(), state = state, verifier = verifier)
    }

    /**
     * Finishes a sign-in from the answer the browser handed back, and stores the tokens. The answer comes either on
     * the server's HTTPS callback (an App Link or the Auth Tab) or on app.getkosha:/oauth/callback from the server's
     * "Open Kosha" button; the code is exchanged with the HTTPS redirect both ways.
     */
    suspend fun finish(pending: PendingSignIn, redirect: String): Tokens {
        val url = callback(pending.server, redirect) ?: throw AuthException("The sign-in came back somewhere unexpected.")
        url.queryParameter("error")?.let { throw AuthException(url.queryParameter("error_description") ?: it) }
        if (url.queryParameter("state") != pending.state) throw AuthException("The sign-in didn't match. Try again.")
        // RFC 9207: an answer from another server than the one asked is refused.
        val iss = url.queryParameter("iss")
        if (iss != null && pending.issuer.isNotEmpty() && iss.trimEnd('/') != pending.issuer.trimEnd('/')) {
            throw AuthException("The sign-in came back from another server.")
        }
        val code = url.queryParameter("code") ?: throw AuthException("The sign-in came back without a code.")
        val tokens = exchange(
            pending.server,
            pending.tokenUrl,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri(pending.server))
                .add("client_id", CLIENT_ID)
                .add("code_verifier", pending.verifier)
                .add("resource", "${pending.server}/api/v1")
                .build(),
            previousRefresh = null,
        )
        store.write(tokens)
        return tokens
    }

    /**
     * A fresh access token. Callers that saw a 401 pass the token that failed: if another caller already
     * refreshed it, they get the new one without a second refresh, so a rotated token is never replayed.
     */
    suspend fun refresh(failed: String?): Tokens? = refreshing.withLock {
        val current = store.read() ?: return null
        if (failed != null && current.access != failed) return current
        val tokenUrl = "${current.server}/api/auth/oauth2/token"
        val next = runCatching {
            exchange(
                current.server,
                tokenUrl,
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", current.refresh)
                    .add("client_id", CLIENT_ID)
                    .add("resource", "${current.server}/api/v1")
                    .build(),
                previousRefresh = current.refresh,
            )
        }.getOrElse { e ->
            // A refused refresh means the session is over. No signal, 429 or 5xx keep the session: the next try sends
            // the same refresh token, and the server answers a lost reply again until the new token is used.
            if (e is AuthException) store.clear()
            throw e
        }
        store.write(next)
        next
    }

    /**
     * Signs out: revokes the refresh token on the server once (best effort when offline) and forgets the session.
     * Never retried: revoking a revoked token ends every sign-in of this account on this client.
     */
    suspend fun signOut() {
        val current = store.read() ?: return
        withContext(Dispatchers.IO) { runCatching {
            http.newCall(
                Request.Builder()
                    .url("${current.server}/api/auth/oauth2/revoke")
                    .post(
                        FormBody.Builder()
                            .add("token", current.refresh)
                            .add("token_type_hint", "refresh_token")
                            .add("client_id", CLIENT_ID)
                            .build(),
                    )
                    .build(),
            ).await().close()
        } }
        store.clear()
    }

    /**
     * The answer as a URL when it came where this sign-in sent it: the server's own callback, or the app's scheme.
     * The scheme has no host, so it is read as one under https.
     */
    internal fun callback(server: String, redirect: String): okhttp3.HttpUrl? {
        if (redirect.startsWith("$FALLBACK_REDIRECT?") || redirect == FALLBACK_REDIRECT) {
            return redirect.replaceFirst("app.getkosha:/", "https://app.getkosha/").toHttpUrlOrNull()
        }
        val url = redirect.toHttpUrlOrNull() ?: return null
        val expected = redirectUri(server).toHttpUrlOrNull() ?: return null
        return url.takeIf { it.scheme == expected.scheme && it.host == expected.host && it.port == expected.port && it.encodedPath == expected.encodedPath }
    }

    private suspend fun exchange(server: String, url: String, body: FormBody, previousRefresh: String?): Tokens = withContext(Dispatchers.IO) {
        val res = http.newCall(Request.Builder().url(url).post(body).build()).await()
        val text = res.use { it.body.string() }
        // 429 and 5xx pass, so try again later; Better Auth's limiter sends X-Retry-After. Any other 4xx is the server
        // saying no (an expired or reused code or token).
        if (res.code == 429 || res.code == 408 || res.code >= 500) {
            val wait = (res.header("Retry-After") ?: res.header("X-Retry-After"))?.trim()?.toLongOrNull()
            throw RetryLaterException(wait, "Sign-in is busy (${res.code})")
        }
        if (res.code in 400..499) throw AuthException(text.ifBlank { "Sign-in failed (${res.code})" })
        if (!res.isSuccessful) throw java.io.IOException("Sign-in failed (${res.code})")
        val t = KoshaJson.decodeFromString(TokenResponse.serializer(), text)
        Tokens(
            server = server,
            clientId = CLIENT_ID,
            access = t.access,
            refresh = t.refresh ?: previousRefresh ?: throw AuthException("No refresh token"),
            expiresAt = System.currentTimeMillis() / 1000 + t.expiresIn,
        )
    }

    private fun token(bytes: Int) = b64(ByteArray(bytes).also(random::nextBytes))

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
