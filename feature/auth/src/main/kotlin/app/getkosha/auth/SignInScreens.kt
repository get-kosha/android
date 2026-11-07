package app.getkosha.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.data.Kosha
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Field
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.network.DEFAULT_SERVER
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

private val Peacock = Color(0xFF0F524E)
private val Paper = Color(0xFFF3F5F0)

/**
 * Signed out: SignIn, OwnServer, Finishing and SignInError, by [SignInFlow]'s step. The brand panel (peacock,
 * the K-coin and the wordmark) appears only here and on the launch screen.
 */
@Composable
fun SignedOut() {
    val step by SignInFlow.step.collectAsStateWithLifecycle()
    var ownServer by rememberSaveable { mutableStateOf(false) }
    when (val s = step) {
        is Step.Finishing -> Finishing(s)
        else -> if (ownServer) OwnServer(onBack = { ownServer = false }) else SignIn(
            failed = s as? Step.Failed,
            busy = s is Step.Browsing,
            onOwnServer = { SignInFlow.cancel(); ownServer = true },
        )
    }
}

@Composable
private fun SignIn(failed: Step.Failed?, busy: Boolean, onOwnServer: () -> Unit) {
    val activity = LocalActivity.current as Activity
    val scope = rememberCoroutineScope()
    val c = KoshaTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Peacock).statusBarsPadding().padding(24.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(app.getkosha.auth.R.drawable.kosha_logo_reversed), contentDescription = "Kosha")
                KText(
                    stringResource(R.string.site_tagline),
                    Modifier.semantics { heading() },
                    style = KoshaTheme.type.title.copy(fontSize = 26.sp),
                    color = Paper,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (failed != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bad.copy(alpha = 0.08f)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PhIcon("warning-circle", size = 20.dp, tint = c.bad)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KText(stringResource(R.string.app_signin_error_title), style = KoshaTheme.type.head)
                        KText(failed.message ?: stringResource(R.string.app_signin_error_body, failed.host), style = KoshaTheme.type.small, color = c.ink2)
                    }
                }
            }
            Button(
                stringResource(if (failed != null) R.string.common_try_again else R.string.app_signin_continue),
                { scope.launch { SignInFlow.begin(activity, DEFAULT_SERVER) } },
                Modifier.fillMaxWidth(),
                icon = "arrow-square-out",
                enabled = !busy,
            )
            Button(
                stringResource(R.string.app_signin_demo),
                { scope.launch { SignInFlow.begin(activity, DEFAULT_SERVER, demo = true) } },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Secondary,
                enabled = !busy,
            )
            KText(
                stringResource(R.string.app_signin_note, DEFAULT_SERVER.toHttpUrl().host),
                style = KoshaTheme.type.small,
                color = c.ink3,
                align = TextAlign.Center,
            )
            KText(
                stringResource(R.string.app_signin_own_server),
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onOwnServer)
                    .padding(12.dp),
                style = KoshaTheme.type.body.copy(fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                color = c.ink2,
            )
        }
    }
}

@Composable
private fun OwnServer(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val activity = LocalActivity.current as Activity
    val scope = rememberCoroutineScope()
    val auth = remember { Kosha.get().auth }
    val c = KoshaTheme.colors
    var address by rememberSaveable { mutableStateOf("") }
    var check by remember { mutableStateOf<SignInFlow.Check?>(null) }
    val server = auth.normalise(address)
    // A live check, a moment after typing stops.
    LaunchedEffect(server) {
        check = null
        if (server == null) return@LaunchedEffect
        delay(500)
        check = SignInFlow.check(server)
    }
    val host = server?.toHttpUrl()?.host ?: ""
    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton("x", stringResource(R.string.common_cancel), onBack)
        }
        KText(stringResource(R.string.app_own_title), Modifier.semantics { heading() }, style = KoshaTheme.type.page)
        KText(stringResource(R.string.app_own_lead), color = c.ink2)
        Field(
            address,
            { address = it },
            label = stringResource(R.string.app_own_label),
            placeholder = "https://kosha.home.example",
            error = when {
                address.isNotBlank() && server == null -> stringResource(R.string.app_own_invalid)
                check == SignInFlow.Check.NotFound -> stringResource(R.string.app_own_not_found, host)
                check == SignInFlow.Check.TooOld -> stringResource(R.string.app_own_too_old, host)
                else -> null
            },
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        )
        if (check is SignInFlow.Check.Found) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PhIcon("check-circle", size = 18.dp, tint = c.pos)
                KText(stringResource(R.string.app_own_found, host), style = KoshaTheme.type.small, color = c.pos)
            }
        }
        Button(
            stringResource(R.string.app_signin_continue),
            { if (server != null) scope.launch { SignInFlow.begin(activity, server) } },
            Modifier.fillMaxWidth(),
            icon = "arrow-square-out",
            enabled = check is SignInFlow.Check.Found,
        )
        KText(
            stringResource(R.string.app_own_use_default),
            Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(12.dp),
            style = KoshaTheme.type.body.copy(fontSize = 14.sp, textDecoration = TextDecoration.Underline),
            color = c.ink2,
        )
    }
}

@Composable
private fun Finishing(step: Step.Finishing) {
    val c = KoshaTheme.colors
    val lines = listOf(
        stringResource(R.string.app_finishing_signed_in, step.host),
        stringResource(R.string.app_finishing_accounts),
        stringResource(R.string.app_finishing_offline),
    )
    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        KText(stringResource(R.string.app_finishing_title), Modifier.semantics { heading() }, style = KoshaTheme.type.title)
        lines.forEachIndexed { i, line ->
            val done = i < step.done
            Row(
                Modifier.semantics(mergeDescendants = true) { },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhIcon(if (done) "check-circle" else "circle-dashed", size = 20.dp, tint = if (done) c.pos else c.ink3, fill = done)
                KText(line, color = if (done) c.ink else c.ink3)
            }
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        Button(stringResource(R.string.common_cancel), { SignInFlow.cancel() }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
}

@Suppress("unused")
private val keepContentDescription = Modifier.semantics { contentDescription = "" }
