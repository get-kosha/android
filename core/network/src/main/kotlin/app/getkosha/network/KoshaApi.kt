package app.getkosha.network

import app.getkosha.model.ApiError
import app.getkosha.model.ErrorBody
import app.getkosha.model.KoshaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The server answered with an error. [error] carries its message (written for people) and invalid fields. */
class ApiException(val status: Int, val error: ApiError) : Exception(error.message) {
    val readOnly get() = status == 403
    /** Busy (429, Retry-After: 60) or failing (5xx): the same request may pass later. */
    val retryable get() = status == 429 || status >= 500
}

/** No session: sign in again. */
class SignedOutException : Exception("Signed out")

/** Runs an OkHttp call without blocking a thread, cancelling it with the coroutine. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
}

private val JSON = "application/json".toMediaType()

/**
 * Kosha's API: the public operations under /api/v1 and the app's own endpoints under /api/app. Every request
 * carries the access token; a 401 refreshes it once (one refresh at a time) and retries. Errors arrive as
 * [ApiException] with the server's message; no connection is an [IOException], which callers treat as offline.
 * Requests ask for the app's language, so the app endpoints and AI replies answer in it.
 */
class KoshaApi(val http: OkHttpClient, private val store: TokenStore, private val auth: Auth, private val language: () -> Locale) {
    val server: String? get() = store.read()?.server

    suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap()): T =
        decode(serializer(), call("GET", path, query, null))

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        decode(serializer(), call("POST", path, emptyMap(), encode(serializer(), body)))

    suspend inline fun <reified B, reified T> patch(path: String, body: B): T =
        decode(serializer(), call("PATCH", path, emptyMap(), encode(serializer(), body)))

    suspend inline fun <reified B, reified T> put(path: String, body: B): T =
        decode(serializer(), call("PUT", path, emptyMap(), encode(serializer(), body)))

    suspend inline fun <reified T> delete(path: String, query: Map<String, Any?> = emptyMap()): T =
        decode(serializer(), call("DELETE", path, query, null))

    /** A request whose answer is ignored or empty (204). */
    suspend fun send(method: String, path: String, body: JsonElement? = null, query: Map<String, Any?> = emptyMap()) {
        call(method, path, query, body?.let { it.toString().toRequestBody(JSON) })
    }

    /** Uploads one file as a multipart form field `file`. */
    suspend fun upload(path: String, name: String, type: String, bytes: ByteArray): String {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(type.toMediaType()))
            .build()
        return call("POST", path, emptyMap(), form)
    }

    /** Raw bytes, for attachments, avatars and exports. */
    suspend fun bytes(path: String, query: Map<String, Any?> = emptyMap()): ByteArray = execute("GET", path, query, null) { it.body.bytes() }

    /** Raw bytes as the body of a PUT, such as a profile picture. */
    suspend fun putBytes(path: String, type: String, bytes: ByteArray) {
        call("PUT", path, emptyMap(), bytes.toRequestBody(type.toMediaType()))
    }

    fun <T> decode(s: KSerializer<T>, text: String): T =
        KoshaJson.decodeFromString(s, text.ifBlank { "{}" })

    fun <T> encode(s: KSerializer<T>, value: T): RequestBody = KoshaJson.encodeToString(s, value).toRequestBody(JSON)

    suspend fun call(method: String, path: String, query: Map<String, Any?>, body: RequestBody?): String =
        execute(method, path, query, body) { it.body.string() }

    // Reading a response body is blocking I/O, so every request runs on the IO dispatcher, whoever calls it.
    private suspend fun <R> execute(method: String, path: String, query: Map<String, Any?>, body: RequestBody?, read: (Response) -> R): R =
        withContext(Dispatchers.IO) { send(method, path, query, body, read) }

    private suspend fun <R> send(method: String, path: String, query: Map<String, Any?>, body: RequestBody?, read: (Response) -> R): R {
        var tokens = store.read() ?: throw SignedOutException()
        repeat(2) { attempt ->
            val url = (tokens.server + path).toHttpUrl().newBuilder().apply {
                query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
            }.build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${tokens.access}")
                .header("Accept-Language", language().toLanguageTag())
                .method(method, body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody(JSON))
                .build()
            http.newCall(request).await().use { res ->
                if (res.code == 401 && attempt == 0) {
                    // A refused refresh ends the session (Auth has cleared it): the app shows sign-in.
                    tokens = try {
                        auth.refresh(tokens.access)
                    } catch (e: AuthException) {
                        null
                    } ?: throw SignedOutException()
                    return@repeat
                }
                if (res.code == 401) {
                    store.clear()
                    throw SignedOutException()
                }
                if (!res.isSuccessful) {
                    val text = res.body.string()
                    val error = runCatching { KoshaJson.decodeFromString(ErrorBody.serializer(), text).error }
                        .getOrElse { ApiError(message = text.take(300)) }
                    throw ApiException(res.code, error)
                }
                return read(res)
            }
        }
        throw SignedOutException()
    }
}
