package app.getkosha.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.getkosha.model.Dates
import app.getkosha.model.KoshaJson
import app.getkosha.model.Meta
import app.getkosha.model.Money
import app.getkosha.model.Settings
import app.getkosha.network.ApiException
import app.getkosha.network.Auth
import app.getkosha.network.KoshaApi
import app.getkosha.network.SignedOutException
import app.getkosha.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A read for a screen: what this phone saved, then what the server says. */
data class Loaded<T>(
    val value: T?,
    /** Still checking with the server. */
    val refreshing: Boolean,
    /** The server couldn't be reached, so [value] is what this phone saved. */
    val offline: Boolean = false,
    /** The server's message when it refused the read. */
    val error: String? = null,
)

/**
 * The app's one session and shared state: the API client, the cache, the outbox, the signed-in account's meta
 * and settings, and the month and display currency that Overview, Activity and Recurring share. Created once
 * in the Application; screens and workers read it through [Kosha.get].
 */
class Kosha private constructor(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // AI replies and statement reads take a while
        .build()
    val tokens = TokenStore(app)
    val auth = Auth(http, tokens)
    val api = KoshaApi(http, tokens, auth) { language }
    val cache = Cache(app)
    val outbox = Outbox(app, api)
    val local = LocalPrefs(app)

    private val _signedIn = MutableStateFlow(tokens.read() != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _meta = MutableStateFlow(cache.read("meta")?.let { runCatching { KoshaJson.decodeFromString(Meta.serializer(), it) }.getOrNull() })
    /** Accounts, categories, rules, events, tags, rates, preferences and which AI features are live. */
    val meta: StateFlow<Meta?> = _meta.asStateFlow()

    private val _settings = MutableStateFlow(cache.read("settings")?.let { runCatching { KoshaJson.decodeFromString(Settings.serializer(), it) }.getOrNull() })
    val settings: StateFlow<Settings?> = _settings.asStateFlow()

    /** The month Overview, Activity and Recurring show. */
    val month = MutableStateFlow(Dates.month(_meta.value?.tz ?: "UTC"))

    /** The currency totals show in; null means the main currency. */
    val currency = MutableStateFlow<String?>(null)

    /** Bumped after every change, so screens that read through [load] fetch again. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The app's language: the `language` setting, else the phone's. */
    val language: Locale
        get() = (_meta.value?.pref("language", "") ?: "").ifEmpty { Locale.getDefault().language }.let(Money::locale)

    val tz: String get() = _meta.value?.tz ?: "UTC"

    init {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        _online.value = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val was = _online.value
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (!was && _online.value) sendOutbox()
                }

                override fun onLost(network: Network) {
                    _online.value = cm.activeNetwork != null && cm.activeNetwork != network
                }
            },
        )
    }

    /** After a sign-in: remember the session and load the account. */
    fun signedIn() {
        _signedIn.value = true
        refresh()
    }

    /** Fetches meta and settings again. Called on launch, on resume and after changes. */
    fun refresh() {
        if (!_signedIn.value) return
        scope.launch {
            runCatching {
                val m = api.get<Meta>("/api/app/meta")
                cache.write("meta", KoshaJson.encodeToString(Meta.serializer(), m))
                if (_meta.value == null) month.value = Dates.month(m.tz)
                _meta.value = m
                val s = api.get<Settings>("/api/v1/settings")
                cache.write("settings", KoshaJson.encodeToString(Settings.serializer(), s))
                _settings.value = s
            }.onFailure {
                // The error only, never the account's data.
                android.util.Log.w("Kosha", "refresh failed: ${it::class.simpleName}: ${it.message?.take(200)}")
                if (it is SignedOutException) signedOutLocally()
            }
            sendOutbox()
        }
    }

    /** Call after a write: screens read again, and meta (balances, tags, rules) refreshes. */
    fun changed() {
        _version.value++
        refresh()
    }

    /**
     * A read that shows what this phone saved first, then the server's answer, cached under [key].
     * Offline, it stays on the saved answer and says so.
     */
    fun <T> load(key: String, serializer: KSerializer<T>, fetch: suspend KoshaApi.() -> T): Flow<Loaded<T>> = flow {
        val saved = cache.read(key)?.let { runCatching { KoshaJson.decodeFromString(serializer, it) }.getOrNull() }
        emit(Loaded(saved, refreshing = true))
        try {
            val fresh = api.fetch()
            cache.write(key, KoshaJson.encodeToString(serializer, fresh))
            emit(Loaded(fresh, refreshing = false))
        } catch (e: IOException) {
            emit(Loaded(saved, refreshing = false, offline = true))
        } catch (e: ApiException) {
            emit(Loaded(saved, refreshing = false, error = e.message))
        } catch (e: SignedOutException) {
            signedOutLocally()
            emit(Loaded(saved, refreshing = false))
        } catch (e: kotlinx.serialization.SerializationException) {
            // An answer this version can't read (a newer or older server): keep what was saved, and say so in the log.
            android.util.Log.w("Kosha", "can't read $key: ${e.message?.take(200)}")
            emit(Loaded(saved, refreshing = false, error = e.message?.take(200)))
        }
    }

    inline fun <reified T> load(key: String, noinline fetch: suspend KoshaApi.() -> T) = load(key, serializer<T>(), fetch)

    /** Saves synced preferences (PATCH /api/app/preferences) and applies them at once. */
    fun setPreference(key: String, value: String?) {
        val m = _meta.value ?: return
        _meta.value = m.copy(prefs = m.prefs + (key to (value?.let(::JsonPrimitive))))
        scope.launch {
            runCatching {
                api.send("PATCH", "/api/app/preferences", buildJsonObject { put(key, value?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull) })
            }
            changed()
        }
    }

    /** Asks WorkManager to send the outbox as soon as there is a connection. */
    fun sendOutbox() {
        if (outbox.items.value.isEmpty()) return
        WorkManager.getInstance(app).enqueueUniqueWork(
            "outbox",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Signs out: revokes the refresh token, then clears what this phone saved and the outbox. */
    suspend fun signOut() {
        auth.signOut()
        signedOutLocally()
    }

    private fun signedOutLocally() {
        tokens.clear()
        cache.clear()
        outbox.clear()
        _meta.value = null
        _settings.value = null
        _signedIn.value = false
    }

    companion object {
        @Volatile private var instance: Kosha? = null

        fun init(context: Context): Kosha = instance ?: synchronized(this) { instance ?: Kosha(context).also { instance = it } }

        fun get(): Kosha = checkNotNull(instance) { "Kosha.init runs in Application.onCreate" }
    }
}

/** Sends the outbox when the connection is back, then refreshes totals. */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kosha = Kosha.init(applicationContext)
        return runCatching {
            if (kosha.outbox.send() > 0) kosha.changed()
            if (kosha.outbox.items.value.isEmpty()) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }
}
