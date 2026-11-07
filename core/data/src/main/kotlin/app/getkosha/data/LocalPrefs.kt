package app.getkosha.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings that belong to this phone only: the app lock, the account Add used last, and how amounts animate.
 * Everything else is a synced preference in meta.prefs.
 */
class LocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kosha.local", Context.MODE_PRIVATE)

    private val _lock = MutableStateFlow(prefs.getBoolean("lock", false))
    /** Settings › Sign-in & security › Unlock with fingerprint. */
    val lock: StateFlow<Boolean> = _lock.asStateFlow()

    private val _lockAfter = MutableStateFlow(prefs.getInt("lock_after", 1))
    /** Minutes in the background before the lock comes back: 0 (instantly), 1, 5, 15 or 60. */
    val lockAfter: StateFlow<Int> = _lockAfter.asStateFlow()

    fun setLock(on: Boolean) {
        prefs.edit().putBoolean("lock", on).apply()
        _lock.value = on
    }

    fun setLockAfter(minutes: Int) {
        prefs.edit().putInt("lock_after", minutes).apply()
        _lockAfter.value = minutes
    }

    /** The account Add used last on this phone, for "Default account: Last used". */
    var lastAccount: String?
        get() = prefs.getString("last_account", null)
        set(value) = prefs.edit().putString("last_account", value).apply()

    /** When the app last went to the background, in milliseconds, for the lock. */
    var backgroundedAt: Long
        get() = prefs.getLong("backgrounded_at", 0)
        set(value) = prefs.edit().putLong("backgrounded_at", value).apply()
}
