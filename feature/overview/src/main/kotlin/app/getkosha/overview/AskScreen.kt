package app.getkosha.overview

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getkosha.design.Card
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sparkle
import app.getkosha.design.categoryIconName
import app.getkosha.model.Categories
import app.getkosha.network.ApiException
import app.getkosha.ui.ActivitySearch
import app.getkosha.ui.AmountText
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Route
import app.getkosha.ui.Skeleton
import app.getkosha.ui.meta
import app.getkosha.ui.openActivity
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException

@Serializable
data class AskTurn(val role: String, val text: String)

@Serializable
private data class AskRequest(val question: String, val history: List<AskTurn>)

/** ask_kosha's answer: the text, what it looked up, and searches for the entries behind it. */
@Serializable
data class AskReply(
    val answer: String,
    val lookups: List<String> = emptyList(),
    val sources: List<Source> = emptyList(),
    val history: List<AskTurn> = emptyList(),
) {
    @Serializable
    data class Source(val label: String, val search: JsonObject = JsonObject(emptyMap()))
}

private sealed interface Said {
    data class Question(val text: String) : Said
    data class Answer(val reply: AskReply) : Said
    data class Failed(val message: String) : Said
}

/** The conversation outlives a visit to another screen, until New chat, as on the web. */
private val kept = mutableStateListOf<Said>()
private var keptHistory: List<AskTurn> = emptyList()

private val PROVIDERS = mapOf("anthropic" to "Anthropic", "openai" to "OpenAI", "gemini" to "Gemini", "openrouter" to "OpenRouter", "ollama" to "Ollama")

/**
 * Ask Kosha (AI, only when meta.ai.ask): suggested questions, then each question with its answer, the entries
 * behind it (as Activity searches), how Kosha found it, and the model. Follow-ups carry the history. Uses the
 * public ask_kosha operation, without streaming, so the look-ups show once the answer is in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(question: String?) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    if (m?.ai?.ask != true) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val failedText = stringResource(R.string.ask_error)
    val send = { text: String ->
        val q = text.trim()
        if (q.isNotEmpty() && !busy) {
            draft = ""
            kept += Said.Question(q)
            busy = true
            scope.launch {
                try {
                    val reply = kosha.api.post<AskRequest, AskReply>("/api/v1/ai/ask", AskRequest(q.take(2000), keptHistory.takeLast(22)))
                    keptHistory = reply.history
                    kept += Said.Answer(reply)
                } catch (e: ApiException) {
                    kept += Said.Failed(e.message ?: failedText)
                } catch (e: IOException) {
                    kept += Said.Failed(res.getString(R.string.common_offline_title))
                } finally {
                    busy = false
                }
            }
        }
    }
    // A question carried in the route (from Search) is asked once.
    LaunchedEffect(question) { if (!question.isNullOrBlank() && kept.none { it is Said.Question && it.text == question.trim() }) send(question) }
    LaunchedEffect(kept.size, busy) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton("caret-left", stringResource(R.string.common_back), { nav.popBackStack() })
            KText(stringResource(R.string.ask_title), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.head.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), align = TextAlign.Center)
            Box(Modifier.alpha(if (kept.isEmpty() || busy) 0.4f else 1f)) {
                IconButton("note-pencil", stringResource(R.string.ask_new_chat), {
                    if (!busy) {
                        kept.clear()
                        keptHistory = emptyList()
                    }
                })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (kept.isEmpty() && !busy) Suggestions(send)
            kept.forEach { said ->
                when (said) {
                    is Said.Question -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        KText(
                            said.text,
                            Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(c.ink).padding(horizontal = 14.dp, vertical = 12.dp),
                            color = c.onInk,
                        )
                    }
                    is Said.Answer -> Answer(said.reply)
                    is Said.Failed -> KText(said.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = KoshaTheme.type.small, color = c.bad)
                }
            }
            if (busy) Working()
        }
        Column(Modifier.fillMaxWidth().background(c.glass).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(
                    draft, { draft = it.take(2000) }, Modifier.weight(1f),
                    placeholder = stringResource(if (kept.isEmpty()) R.string.ask_placeholder else R.string.ask_placeholder_follow_up),
                    keyboard = KeyboardOptions(imeAction = ImeAction.Send),
                    singleLine = false,
                )
                val ready = draft.isNotBlank() && !busy
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (ready) c.ink else c.soft)
                        .clickable(enabled = ready, role = Role.Button, onClickLabel = stringResource(R.string.ask_submit)) { send(draft) },
                    contentAlignment = Alignment.Center,
                ) { PhIcon("arrow-up", tint = if (ready) c.onInk else c.ink3, contentDescription = stringResource(R.string.ask_submit)) }
            }
            Spacer(Modifier.height(8.dp))
            if (kept.any { it is Said.Answer }) {
                KText(stringResource(R.string.app_ask_check), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
            } else {
                Privacy(m.aiModel?.provider)
            }
        }
    }
}

/** "Kosha sends only what you share with Anthropic.", "what you share" opening Settings › AI › What AI can see. */
@Composable
private fun Privacy(provider: String?) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val name = when (provider) {
        null -> ""
        "compatible" -> stringResource(R.string.ask_your_endpoint)
        else -> PROVIDERS[provider] ?: provider
    }
    val raw = LocalResources.current.getText(R.string.app_ask_privacy)
    val text = remember(raw, name) {
        val spanned = raw as? android.text.Spanned
        val plain = raw.toString().replace("%1\$s", name)
        val link = spanned?.getSpans(0, spanned.length, android.text.Annotation::class.java)?.firstOrNull()
        buildAnnotatedString {
            if (link == null) append(plain)
            else {
                val before = raw.subSequence(0, spanned.getSpanStart(link)).toString().replace("%1\$s", name)
                val inside = raw.subSequence(spanned.getSpanStart(link), spanned.getSpanEnd(link)).toString()
                val after = raw.subSequence(spanned.getSpanEnd(link), raw.length).toString().replace("%1\$s", name)
                append(before)
                withLink(LinkAnnotation.Clickable("privacy", TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))) { nav.navigate(Route.Settings("ai-privacy")) }) {
                    append(inside)
                }
                append(after)
            }
        }
    }
    androidx.compose.foundation.text.BasicText(
        text, Modifier.fillMaxWidth().heightIn(min = 24.dp),
        style = KoshaTheme.type.xs.copy(color = c.ink3, textAlign = TextAlign.Center),
    )
}

@Composable
private fun Suggestions(send: (String) -> Unit) {
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val top = m.categories.filter { !it.isArchived && !it.isIncome && it.parentId == null && !Categories.isSystem(it.id) }.maxByOrNull { it.uses }
    val event = m.events.firstOrNull()
    // English lowercases the category mid-sentence; German keeps nouns capitalised (ask.tsx).
    val list = listOf(
        categoryIconName(top?.icon) to (top?.let { stringResource(R.string.ask_suggest_category, if (locale.language == "en") it.name.lowercase() else it.name) } ?: stringResource(R.string.ask_suggest_spent)),
        "chart-bar" to stringResource(R.string.ask_suggest_compare),
        "arrow-fat-lines-up" to stringResource(R.string.ask_suggest_biggest),
        (if (event != null) "calendar-star" else "arrows-clockwise") to (event?.let { stringResource(R.string.ask_suggest_event, it.name) } ?: stringResource(R.string.ask_suggest_subscriptions)),
    )
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.brand.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Sparkle(size = 26.dp) }
        KText(stringResource(R.string.ask_empty_title), Modifier.padding(top = 6.dp).semantics { heading() }, style = KoshaTheme.type.title)
        KText(stringResource(R.string.ask_empty_body), style = KoshaTheme.type.small, color = c.ink2)
        Spacer(Modifier.height(16.dp))
        list.forEach { (icon, text) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
                    .border(1.dp, c.line, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { send(text) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PhIcon(icon, size = 18.dp, tint = c.ink3)
                KText(text, Modifier.weight(1f), style = KoshaTheme.type.small)
            }
        }
    }
}

/** The question is with the model: a spinner and placeholder lines until the answer is in. */
@Composable
private fun Working() {
    val c = KoshaTheme.colors
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "angle")
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sparkle()
            KText(stringResource(R.string.app_ask_looking), style = KoshaTheme.type.small, color = c.ink3)
        }
        Card(Modifier.widthIn(max = 320.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhIcon("spinner-gap", Modifier.rotate(spin), size = 16.dp, tint = c.brand)
                KText(stringResource(R.string.ask_thinking) + "…", style = KoshaTheme.type.small)
            }
        }
        Skeleton(Modifier.fillMaxWidth(0.92f), 12.dp)
        Skeleton(Modifier.fillMaxWidth(0.7f), 12.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Answer(reply: AskReply) {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Prose(reply.answer)
        if (reply.sources.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reply.sources.forEach { s ->
                    Chip(s.label, small = true, onClick = { nav.openActivity(s.search.toActivitySearch()) }, leading = { PhIcon("list-bullets", size = 16.dp) })
                }
            }
        }
        if (reply.lookups.isNotEmpty()) {
            Row(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhIcon("magnifying-glass", size = 16.dp)
                KText(pluralStringResource(R.plurals.ask_how_found, reply.lookups.size, reply.lookups.size), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink2)
                PhIcon(if (open) "caret-up" else "caret-down", size = 16.dp, tint = c.ink3)
            }
            if (open) {
                Row {
                    Box(Modifier.size(2.dp, (reply.lookups.size * 24).dp).background(c.line))
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reply.lookups.forEach { KText(it, style = KoshaTheme.type.small) }
                    }
                }
            }
        }
        m?.aiModel?.let { model ->
            val provider = if (model.provider == "compatible") stringResource(R.string.ask_your_endpoint) else PROVIDERS[model.provider] ?: model.provider
            KText(stringResource(R.string.ask_answered_by, provider, model.model), style = KoshaTheme.type.xs, color = c.ink3)
        }
    }
}

/** The little Markdown an answer uses (ask.tsx Prose): paragraphs, "- " and "1. " lists, and **bold**. */
@Composable
private fun Prose(text: String) {
    val c = KoshaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.trim().split(Regex("\\n\\s*\\n")).forEach { block ->
            block.lines().filter { it.isNotBlank() }.forEach { line ->
                val item = Regex("^\\s*(?:[-*]|\\d+\\.)\\s+").find(line)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item != null) KText(if (item.value.trim().first().isDigit()) item.value.trim() else "•", style = KoshaTheme.type.body, color = c.ink3)
                    AmountText(bold(if (item != null) line.substring(item.range.last + 1) else line), style = KoshaTheme.type.body.copy(lineHeight = 22.sp), color = c.ink)
                }
            }
        }
    }
}

private fun bold(line: String): AnnotatedString = buildAnnotatedString {
    line.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(part) } else append(part)
    }
}

/** A source's search_transactions inputs as Activity's search. */
private fun JsonObject.toActivitySearch(): ActivitySearch {
    fun s(k: String) = (this[k] as? JsonPrimitive)?.content
    fun n(k: String) = (this[k] as? JsonPrimitive)?.longOrNull
    return ActivitySearch(
        query = s("query"), categoryId = s("categoryId"), accountId = s("accountId"), tag = s("tag"), eventId = s("eventId"),
        type = s("type"), minAmount = n("minAmount"), maxAmount = n("maxAmount"), month = s("month"),
    )
}
