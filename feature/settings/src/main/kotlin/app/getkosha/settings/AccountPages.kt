package app.getkosha.settings

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Field
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayOutputStream
import kotlin.math.min

// Profile and Sign-in & security.

/** Profile: the picture (upload, change, remove, or the initial on the accent), the name, the email, Sign out. */
@Composable
fun ProfilePage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val waiting by kosha.outbox.items.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val unreadable = stringResource(R.string.settings_profile_unreadable)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || w.refused()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val jpeg = withContext(Dispatchers.Default) { runCatching { squareJpeg(context, uri) }.getOrNull() }
            if (jpeg == null) w.toast(unreadable)
            else w.run({ kosha.api.putBytes("/api/app/avatar", "image/jpeg", jpeg) })
            busy = false
        }
    }
    var name by remember(m?.user?.name) { mutableStateOf(m?.user?.name ?: "") }
    // The name saves itself a moment after typing stops; an empty name isn't saved.
    LaunchedEffect(name) {
        val next = name.trim()
        if (next.isEmpty() || next == m?.user?.name) return@LaunchedEffect
        delay(800)
        w.run({ kosha.api.send("PATCH", "/api/app/profile", buildJsonObject { put("name", JsonPrimitive(next)) }) })
    }

    Page(
        stringResource(R.string.settings_section_profile),
        onBack = { nav.popBackStack() },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KText(stringResource(R.string.settings_cat_autosave), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
                if (waiting.isNotEmpty()) {
                    KText(pluralStringResource(R.plurals.app_signout_waiting, waiting.size, waiting.size), style = KoshaTheme.type.small, color = c.warn)
                }
                Button(
                    stringResource(R.string.settings_profile_sign_out),
                    { scope.launch { kosha.signOut() } },
                    Modifier.fillMaxWidth(),
                    style = ButtonStyle.Secondary,
                    icon = "sign-out",
                )
                KText(stringResource(R.string.app_signout_clears), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
            }
        },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(88.dp)
                if (m?.avatars == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            stringResource(if (busy) R.string.settings_profile_saving else if (m.user?.image != null) R.string.settings_profile_change else R.string.settings_profile_upload),
                            { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            style = ButtonStyle.Secondary,
                            size = ButtonSize.Small,
                            icon = "camera",
                            enabled = !busy,
                        )
                        if (m.user?.image != null) {
                            Button(
                                stringResource(R.string.common_remove),
                                { w.run({ kosha.api.send("DELETE", "/api/app/avatar") }) },
                                style = ButtonStyle.Secondary,
                                size = ButtonSize.Small,
                                enabled = !busy,
                            )
                        }
                    }
                }
                KText(
                    stringResource(R.string.settings_profile_avatar_hint) +
                        if (m?.avatars == true) " " + stringResource(R.string.settings_profile_photo_desc) else "",
                    style = KoshaTheme.type.xs,
                    color = c.ink3,
                    align = TextAlign.Center,
                )
            }
        }
        Column {
            FieldLabel(stringResource(R.string.settings_row_name))
            Field(name, { name = it }, keyboard = KeyboardOptions(imeAction = ImeAction.Done))
            Hint(stringResource(R.string.settings_profile_name_desc), Modifier.padding(top = 6.dp))
        }
        Column {
            FieldLabel(stringResource(R.string.settings_row_email))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.soft)
                    .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KText(m?.user?.email ?: "", Modifier.weight(1f), style = KoshaTheme.type.body.copy(fontSize = 16.sp), color = c.ink2)
                PhIcon("lock-simple", size = 16.dp, tint = c.ink2)
            }
            Hint(stringResource(R.string.settings_profile_email_desc), Modifier.padding(top = 6.dp))
        }
    }
}

/** A photo as Kosha stores profile pictures: the centre square, at most 512px, JPEG of at most 512 KB. */
private fun squareJpeg(context: Context, uri: Uri): ByteArray {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val short = min(info.size.width, info.size.height)
        if (short > 1024) decoder.setTargetSampleSize(short / 1024)
    }
    val side = min(bitmap.width, bitmap.height)
    val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    val target = min(512, side)
    val out = square.scale(target, target)
    var quality = 90
    while (true) {
        val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        if (bytes.size <= 512 * 1024 || quality <= 40) return bytes
        quality -= 10
    }
}

fun Context.activity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

private val LOCK_AFTER = listOf(0, 1, 5, 15, 60)

/**
 * Sign-in & security: how many sign-in steps are done, the app lock on this phone, and the sign-in settings that
 * stay on the web (password, passkeys, Google, two-factor, devices), each opening the server's page in a Custom Tab.
 */
@Composable
fun SecurityPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val context = LocalContext.current
    val account = loaded("account") { get<AccountSummary>("/api/app/account") }.value
    val lock by kosha.local.lock.collectAsStateWithLifecycle()
    val lockAfter by kosha.local.lockAfter.collectAsStateWithLifecycle()
    val canLock = remember { BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }
    val web = { anchor: String -> openWeb(context, kosha.api.server, "/settings/security#$anchor") }
    val promptTitle = stringResource(R.string.app_set_unlock)

    Page(stringResource(R.string.settings_section_security), onBack = { nav.popBackStack() }) {
        if (account != null) {
            val steps = securitySteps(account, m.prefOf("backup_codes_saved_at").isNotEmpty())
            val done = steps.count { it.second }
            if (done < steps.size) {
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Ring(done, steps.size, stringResource(R.string.settings_sec_ring_label, done.toString(), steps.size.toString()))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            KText(pluralStringResource(R.plurals.settings_steps_left, steps.size - done, steps.size - done), style = KoshaTheme.type.head)
                            KText(stringResource(R.string.settings_sec_checklist_description), style = KoshaTheme.type.xs, color = c.ink3)
                        }
                    }
                }
                val labels = mapOf(
                    "password" to (R.string.settings_sec_step_password to R.string.settings_sec_action_set),
                    "passkey" to (R.string.settings_sec_step_passkey to R.string.common_add),
                    "two-factor" to (R.string.settings_sec_two_factor to R.string.settings_sec_action_turn_on),
                    "codes" to (R.string.settings_sec_step_codes to R.string.settings_sec_action_save_codes),
                )
                Rows(*steps.map { (key, ok) ->
                    @Composable {
                        // Backup codes come with two-factor, and two-factor protects a password, so those wait.
                        val can = !ok && (key != "codes" || account.twoFactor) && (key != "two-factor" || account.hasPassword)
                        SRow(stringResource(labels.getValue(key).first)) {
                            if (can) Button(stringResource(labels.getValue(key).second), { web("checklist") }, style = ButtonStyle.Secondary, size = ButtonSize.Small, icon = "arrow-square-out")
                        }
                    }
                }.toTypedArray())
            }
        }

        GroupCaption(stringResource(R.string.app_set_on_phone))
        Rows(
            {
                SwitchRow(
                    stringResource(R.string.app_set_unlock),
                    stringResource(if (canLock) R.string.app_set_unlock_desc else R.string.app_set_unlock_unavailable),
                    lock,
                    { on ->
                        if (!on) kosha.local.setLock(false)
                        else context.activity()?.let { activity ->
                            // Turning the lock on asks once, so nobody locks themselves out with a sensor that won't work.
                            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = kosha.local.setLock(true)
                            }).authenticate(
                                BiometricPrompt.PromptInfo.Builder().setTitle(promptTitle).setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL).build(),
                            )
                        }
                    },
                    enabled = canLock || lock,
                )
            },
            if (lock) {
                {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KText(stringResource(R.string.app_set_lock_after), style = KoshaTheme.type.head)
                        Segmented(
                            LOCK_AFTER.map { it to lockLabel(it) },
                            lockAfter,
                            { kosha.local.setLockAfter(it) },
                            Modifier.fillMaxWidth(),
                            small = true,
                        )
                    }
                }
            } else null,
        )
        Hint(stringResource(R.string.app_set_lock_note))

        Note("globe-simple", stringResource(R.string.app_set_opens_web, serverHost(kosha.api.server)))

        GroupCaption(stringResource(R.string.settings_sec_signin))
        val a = account
        Rows(
            {
                NavRow(
                    stringResource(R.string.settings_sec_password),
                    description = stringResource(if (a?.hasPassword != false) R.string.settings_sec_password_description else R.string.settings_sec_password_description_none),
                    value = stringResource(if (a?.hasPassword != false) R.string.settings_sec_change_password else R.string.settings_sec_set_a_password),
                    external = true,
                ) { web("password") }
            },
            {
                NavRow(
                    stringResource(R.string.settings_sec_passkeys) + if ((a?.passkeys ?: 0) > 0) " · ${a!!.passkeys}" else "",
                    description = stringResource(R.string.settings_sec_passkeys_description),
                    value = stringResource(R.string.common_add),
                    external = true,
                ) { web("passkeys") }
            },
            if (a?.google != null) {
                {
                    NavRow(
                        "Google",
                        description = stringResource(
                            when {
                                a.google.accountId == null -> R.string.settings_sec_google_description_none
                                a.hasPassword -> R.string.settings_sec_google_description_on
                                else -> R.string.settings_sec_google_description_no_password
                            },
                        ),
                        external = true,
                    ) { web("google") }
                }
            } else null,
            {
                NavRow(
                    stringResource(R.string.settings_sec_two_factor),
                    description = stringResource(
                        when {
                            a?.twoFactor == true -> R.string.settings_sec_two_factor_on
                            a?.hasPassword == false -> R.string.settings_sec_two_factor_need_password
                            else -> R.string.settings_sec_two_factor_off
                        },
                    ),
                    external = true,
                    titleExtra = if (a?.twoFactor == true) {
                        { Pill(stringResource(R.string.settings_appearance_on), color = c.pos) }
                    } else null,
                ) { web("two-factor") }
            },
        )
        GroupCaption(stringResource(R.string.settings_sec_devices))
        Rows({
            NavRow(stringResource(R.string.settings_row_devices), description = stringResource(R.string.settings_row_devices_desc), icon = "device-mobile", external = true) { web("devices") }
        })
    }
}

@Composable
private fun lockLabel(minutes: Int): String = when (minutes) {
    0 -> stringResource(R.string.app_set_lock_instantly)
    60 -> stringResource(R.string.app_set_lock_hour)
    else -> stringResource(R.string.app_set_lock_minutes, minutes.toString())
}

/** "2/4" in a ring that fills in the accent as steps are done. */
@Composable
private fun Ring(done: Int, total: Int, label: String) {
    val c = KoshaTheme.colors
    Box(Modifier.size(52.dp).described(label), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(c.soft, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(c.brand, -90f, 360f * done / total, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        KText("$done/$total", Modifier.clearAndSetSemantics { }, style = KoshaTheme.type.num.copy(fontSize = 13.sp))
    }
}
