package app.getkosha.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Refreshes run one at a time, rotated tokens are stored before use, and a refused refresh ends the session. */
class RefreshTest {
    private class MemoryStore(var tokens: Tokens?) : TokenStore(null) {
        override fun read() = tokens
        override fun write(tokens: Tokens) { this.tokens = tokens }
        override fun clear() { tokens = null }
    }

    private val server = MockWebServer()
    private val refreshes = AtomicInteger()
    @Volatile private var refusing = false
    @Volatile private var busy: Int? = null

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                if (path == "/api/auth/oauth2/token") {
                    if (refusing) return MockResponse.Builder().code(400).body("""{"error":"invalid_grant"}""").build()
                    busy?.let { return MockResponse.Builder().code(it).addHeader("X-Retry-After", "7").body("busy").build() }
                    val n = refreshes.incrementAndGet()
                    Thread.sleep(50)
                    return MockResponse.Builder().code(200)
                        .body("""{"access_token":"access-$n","refresh_token":"refresh-$n","expires_in":3600}""").build()
                }
                val auth = request.headers["Authorization"]
                return if (auth == "Bearer access-0") MockResponse.Builder().code(401).body("""{"error":{"code":"unauthorized","message":"no"}}""").build()
                else MockResponse.Builder().code(200).body("""{"ok":"$auth"}""").build()
            }
        }
        server.start()
    }

    @After fun stop() = server.close()

    private fun store() = MemoryStore(
        Tokens(server.url("/").toString().trimEnd('/'), CLIENT_ID, "access-0", "refresh-0", 0),
    )

    @kotlinx.serialization.Serializable
    data class Ok(val ok: String)

    @Test fun concurrentRequestsShareOneRefresh() = runBlocking {
        val store = store()
        val http = OkHttpClient()
        val api = KoshaApi(http, store, Auth(http, store)) { Locale.UK }
        val answers = (1..5).map { async { api.get<Ok>("/api/v1/accounts").ok } }.awaitAll()
        assertEquals(1, refreshes.get())
        assertEquals(List(5) { "Bearer access-1" }, answers)
        assertEquals("refresh-1", store.tokens?.refresh)
    }

    @Test fun refusedRefreshSignsOut() = runBlocking {
        refusing = true
        val store = store()
        val http = OkHttpClient()
        val api = KoshaApi(http, store, Auth(http, store)) { Locale.UK }
        val error = runCatching { api.get<Ok>("/api/v1/accounts") }.exceptionOrNull()
        assertEquals(SignedOutException::class, error!!::class)
        assertNull(store.tokens)
    }

    /** 429 and 5xx from the token endpoint are temporary: the session and its refresh token stay for the next try. */
    @Test fun busyTokenEndpointKeepsTheSession() = runBlocking {
        for (code in listOf(429, 503)) {
            busy = code
            val store = store()
            val http = OkHttpClient()
            val api = KoshaApi(http, store, Auth(http, store)) { Locale.UK }
            val error = runCatching { api.get<Ok>("/api/v1/accounts") }.exceptionOrNull()
            assertEquals(RetryLaterException::class, error!!::class)
            assertEquals(7L, (error as RetryLaterException).seconds)
            assertEquals("refresh-0", store.tokens?.refresh)
        }
        busy = null
    }

    @Test fun answersComeBackOnlyWhereTheyWereSent() {
        val auth = Auth(OkHttpClient(), MemoryStore(null))
        val s = "https://getkosha.app"
        assertEquals("c", auth.callback(s, "https://getkosha.app/app/oauth/callback?code=c&state=x")?.queryParameter("code"))
        assertEquals("c", auth.callback(s, "app.getkosha:/oauth/callback?code=c&state=x")?.queryParameter("code"))
        assertNull(auth.callback(s, "https://evil.example/app/oauth/callback?code=c"))
        assertNull(auth.callback(s, "https://getkosha.app/other?code=c"))
        assertNull(auth.callback(s, "http://getkosha.app/app/oauth/callback?code=c"))
        assertEquals("c", auth.callback("http://localhost:3456", "http://localhost:3456/app/oauth/callback?code=c")?.queryParameter("code"))
    }
}
