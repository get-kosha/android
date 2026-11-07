package app.getkosha

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.getkosha.auth.AppLock
import app.getkosha.auth.SignInFlow
import app.getkosha.data.Kosha
import app.getkosha.ui.KoshaThemeFromPrefs
import app.getkosha.ui.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlinx.coroutines.launch

// A FragmentActivity because the fingerprint and face unlock prompt needs one.
class MainActivity : FragmentActivity() {
    /** A screen a shortcut asked for, opened once the app is signed in and unlocked. */
    val pending = MutableStateFlow<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        // Hide the app's content in the app switcher while the lock is on.
        lifecycleScope.launch {
            combine(Kosha.get().local.lock, AppLock.locked) { lock, locked -> lock to locked }.collect { (lock, locked) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!lock)
                if (locked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        lifecycleScope.launch {
            Kosha.get().meta.map { it?.pref("language", "") ?: "" }.distinctUntilChanged().collect {
                if (it != appliedLanguage && Kosha.get().meta.value != null) recreate()
            }
        }
        setContent { KoshaThemeFromPrefs { Root(pending) } }
    }

    // The app's language is the account's `language` preference, else the phone's. Resources follow the wrapped
    // context; a change of preference recreates the activity.
    private var appliedLanguage: String = ""

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = Kosha.init(newBase).meta.value?.pref("language", "") ?: ""
        if (appliedLanguage.isEmpty()) return super.attachBaseContext(newBase)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(Locale.forLanguageTag(appliedLanguage)) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        SignInFlow.resumed()
    }

    private fun handle(intent: Intent) {
        intent.data?.takeIf { it.scheme == "app.getkosha" }?.let { SignInFlow.redirect(this, it) }
        // Statements shared to Kosha (feature:statements' share activity) open Import.
        if (intent.getStringExtra("kosha.route") == "import") pending.value = Route.Import
        when (intent.getStringExtra("kosha.add")) {
            "expense" -> pending.value = Route.Add(kind = "expense")
            "income" -> pending.value = Route.Add(kind = "income")
            "scan" -> pending.value = Route.Add(scan = true)
        }
    }
}
