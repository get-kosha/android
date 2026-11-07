package app.getkosha.network

import app.getkosha.model.KoshaJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/** Kosha's public OAuth client on every server (migrations/0009_app_clients.sql in the web repo). */
const val CLIENT_ID = "kosha-android"
const val REDIRECT_URI = "app.getkosha:/oauth/callback"
const val DEFAULT_SERVER = "https://getkosha.app"
private const val SCOPES = "openid profile email offline_access kosha:read kosha:write"

/** What the browser must open, and what the app keeps to finish the sign-in when the browser comes back. */
@Serializable
data class PendingSignIn(val server: String, val url: String, val state: String, val verifier: String, val tokenUrl: String, val revokeUrl: String? = null)

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

class AuthException(message: String) : Exception(message)

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
    suspend fun discover(server: String): PendingSignIn {
        val doc = http.newCall(Request.Builder().url("$server/.well-known/oauth-authorization-server").build()).await().use { res ->
            if (!res.isSuccessful) throw AuthException("No Kosha server at $server")
            KoshaJson.decodeFromString(Discovery.serializer(), res.body.string())
        }
        return PendingSignIn(server, doc.authorize, "", "", doc.token, doc.revoke)
    }

    /** The browser URL for a sign-in, with a fresh PKCE verifier and state. `demo` opens the demo account. */
    fun start(found: PendingSignIn, demo: Boolean = false): PendingSignIn {
        val verifier = token(32)
        val state = token(16)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val url = found.url.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
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

    /** Finishes a sign-in from the redirect the browser handed back, and stores the tokens. */
    suspend fun finish(pending: PendingSignIn, redirect: String): Tokens {
        val url = redirect.replace("app.getkosha:/", "https://app.getkosha/").toHttpUrl()
        url.queryParameter("error")?.let { throw AuthException(url.queryParameter("error_description") ?: it) }
        if (url.queryParameter("state") != pending.state) throw AuthException("The sign-in didn't match. Try again.")
        val code = url.queryParameter("code") ?: throw AuthException("The sign-in came back without a code.")
        val tokens = exchange(
            pending.server,
            pending.tokenUrl,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
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
            // A refused refresh means the session is over; a network failure leaves it for the next try.
            if (e is AuthException) store.clear()
            throw e
        }
        store.write(next)
        next
    }

    /** Signs out: revokes the refresh token on the server (best effort when offline) and forgets the session. */
    suspend fun signOut() {
        val current = store.read() ?: return
        runCatching {
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
        }
        store.clear()
    }

    private suspend fun exchange(server: String, url: String, body: FormBody, previousRefresh: String?): Tokens {
        val res = http.newCall(Request.Builder().url(url).post(body).build()).await()
        val text = res.use { it.body.string() }
        // 4xx is the server saying no (an expired or reused code or token); anything else may pass, so retry later.
        if (res.code in 400..499) throw AuthException(text.ifBlank { "Sign-in failed (${res.code})" })
        if (!res.isSuccessful) throw java.io.IOException("Sign-in failed (${res.code})")
        val t = KoshaJson.decodeFromString(TokenResponse.serializer(), text)
        return Tokens(
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
