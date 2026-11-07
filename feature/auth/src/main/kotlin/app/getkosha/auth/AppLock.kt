package app.getkosha.auth

import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.getkosha.data.Kosha
import app.getkosha.design.Button
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings › Sign-in & security › On this phone: unlock with fingerprint or face (or the phone's PIN), and lock
 * again after some time in the background. The window hides its content in the app switcher while locked.
 */
object AppLock {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** At launch: locked whenever the lock is on. */
    fun launched() {
        _locked.value = Kosha.get().local.lock.value && Kosha.get().signedIn.value
    }

    fun backgrounded() {
        Kosha.get().local.backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun foregrounded() {
        val local = Kosha.get().local
        if (!local.lock.value || !Kosha.get().signedIn.value) return
        val away = SystemClock.elapsedRealtime() - local.backgroundedAt
        if (away >= local.lockAfter.value * 60_000L) _locked.value = true
    }

    fun unlock(activity: FragmentActivity, title: String) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _locked.value = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}

/** "Kosha is locked. Locked after a minute away." with Unlock. The prompt opens by itself. */
@Composable
fun LockScreen() {
    val activity = LocalContext.current as FragmentActivity
    val c = KoshaTheme.colors
    val minutes = Kosha.get().local.lockAfter.value
    val title = stringResource(R.string.app_lock_prompt)
    LaunchedEffect(Unit) { AppLock.unlock(activity, title) }
    Box(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.soft), contentAlignment = Alignment.Center) {
                PhIcon("lock-simple", size = 28.dp, tint = c.ink2)
            }
            KText(stringResource(R.string.app_lock_title), Modifier.semantics { heading() }, style = KoshaTheme.type.title)
            KText(
                if (minutes == 0) stringResource(R.string.app_lock_left) else pluralStringResource(R.plurals.app_lock_after, minutes, minutes),
                color = c.ink3,
                align = TextAlign.Center,
            )
        }
        Button(
            stringResource(R.string.app_lock_unlock),
            { AppLock.unlock(activity, title) },
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            icon = "fingerprint",
        )
    }
}
