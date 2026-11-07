package app.getkosha.settings

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.zone.ZoneRulesException
import java.util.Locale

// Pure rules behind the settings pages, ported from the web where they live there. Unit tested.

/**
 * The four sign-in steps and whether each is done (security.tsx securitySteps). The Settings badge counts the
 * open ones. Backup codes count only once two-factor is on.
 */
fun securitySteps(a: AccountSummary, backupCodesSaved: Boolean): List<Pair<String, Boolean>> = listOf(
    "password" to a.hasPassword,
    "passkey" to (a.passkeys > 0),
    "two-factor" to a.twoFactor,
    "codes" to (a.twoFactor && backupCodesSaved),
)

/** Settings words typed into search: every word must appear in one of the texts (registry.tsx searchSettings). */
fun matchesAll(query: String, vararg texts: String): Boolean {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    val hay = texts.joinToString(" ").lowercase()
    return words.all { it in hay }
}

/**
 * The currencies in the order the person keeps them: the default first, then the saved order, then any account
 * currency missing from it. The web lists only currencies an account uses (shell.tsx currencyOptions); the app
 * also keeps currencies added here, which the header's currency switch steps through.
 */
fun currencyOrder(primary: String, order: List<String>, used: List<String>): List<String> =
    (listOf(primary) + order + used).map { it.lowercase() }.distinct()

/** The list with the item at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Units of [to] for one unit of [from], at rates held per 1 USD. Null when either rate is missing. */
fun rate(from: String, to: String, perUsd: Map<String, Double>): Double? {
    val f = perUsd[from] ?: return null
    val t = perUsd[to] ?: return null
    if (f == 0.0) return null
    return t / f
}

/** A rate with enough digits to be useful (core.ts fmtRate): 94.60, 1.1623, 0.01057. */
fun fmtRate(r: Double): String = when {
    r >= 100 -> BigDecimal(r).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    r >= 1 -> BigDecimal(r).setScale(4, java.math.RoundingMode.HALF_UP).toPlainString()
    else -> BigDecimal(r).round(java.math.MathContext(4)).toPlainString()
}

/** A zone's city as people say it: "Europe/Kolkata" reads "Kolkata", "America/New_York" reads "New York". */
fun zoneCity(id: String): String = id.substringAfterLast('/').replace('_', ' ')

/** "UTC+1", "UTC+5:30", "UTC−5" for a zone right now, with a true minus. */
fun zoneOffset(id: String, now: Instant = Instant.now()): String {
    val seconds = try {
        ZoneId.of(id).rules.getOffset(now).totalSeconds
    } catch (e: ZoneRulesException) {
        return "UTC"
    } catch (e: java.time.DateTimeException) {
        return "UTC"
    }
    if (seconds == 0) return "UTC+0"
    val sign = if (seconds < 0) "−" else "+"
    val abs = Math.abs(seconds)
    val h = abs / 3600
    val m = abs % 3600 / 60
    return "UTC$sign$h" + if (m != 0) ":%02d".format(m) else ""
}

/** The zones a person can pick: region/city ones, without the legacy aliases (Etc/…, SystemV/…, US/…). */
fun pickableZones(): List<String> = ZoneId.getAvailableZoneIds()
    .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() && !it.startsWith("US/") }
    .sortedBy { zoneCity(it) }

/** Kosha's languages (project.inlang locales), each named in its own language: "Deutsch", "日本語". */
val LANGUAGES = listOf("en", "de", "es", "fr", "pt", "it", "nl", "ru", "ja", "zh", "ko", "hi")

fun languageName(tag: String): String {
    val l = Locale.forLanguageTag(tag)
    return l.getDisplayLanguage(l).replaceFirstChar { it.titlecase(l) }
}

// ---------- AI ----------

/** The AI features with the preference that turns each on and what it can't work without (core.ts AI_FEATURES). */
enum class AiFeature(val id: String, val pref: String, val needs: List<String>) {
    Categorise("categorise", "ai_categorise", listOf("ai_share_payee", "suggest_category")),
    QuickAdd("quick_add", "ai_quick_add", emptyList()),
    Receipts("receipts", "ai_receipts", emptyList()),
    Recap("recap", "ai_recap", listOf("ai_share_amount")),
    Ask("ask", "ai_ask", emptyList()),
    Statements("statements", "ai_statements", emptyList()),
}

/** Every preference's default (core.ts PREFS), for the keys Settings reads before the server has sent them. */
val PREF_DEFAULTS = mapOf(
    "theme" to "system", "motion" to "on", "accent" to "teal", "density" to "comfortable", "figures" to "mono",
    "hide_amounts" to "0", "account_colours" to "1", "round_hero" to "0", "default_wallet" to "last",
    "add_kind" to "expense", "suggest_category" to "1", "default_auto_pay" to "1", "upcoming_horizon" to "month",
    "learn_rules" to "1", "ai_enabled" to "0", "ai_share_amount" to "1", "ai_share_payee" to "1", "ai_share_date" to "1",
    "ai_share_note" to "0", "ai_share_account" to "0", "ai_log_enabled" to "1", "ai_categorise" to "1",
    "ai_quick_add" to "1", "ai_receipts" to "1", "ai_recap" to "1", "ai_ask" to "1", "ai_statements" to "1",
)

private fun fnv1a(s: String): Long {
    var h = 0x811c9dc5.toInt()
    for (ch in s) h = (h xor ch.code) * 0x01000193
    return h.toLong() and 0xffffffffL
}

/** The stable code that stands in for a payee name when payee names aren't shared (core.ts payeeCode). */
fun payeeCode(uid: String, payee: String): String = "payee_" + fnv1a("$uid\u0000${payee.trim().lowercase()}").toString(36)

/** How one field of the example shows: sent as is, sent in a masked form, or left out. */
enum class Shown { On, Masked, Off }

/** One sample transaction for What AI can see, as core.ts redactForAi would send it (ai.tsx Payload). */
data class SampleTx(val payee: String, val amount: Long, val currency: String, val day: String, val note: String, val account: String)

/** The example's lines: field, its JSON value, and how it shows. */
fun samplePayload(tx: SampleTx, shares: Map<String, Boolean>, uid: String): List<Triple<String, String, Shown>> {
    fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    val payee = shares["payee"] == true
    val amount = shares["amount"] == true
    val date = shares["date"] == true
    val note = shares["note"] == true
    val account = shares["account"] == true
    return listOf(
        Triple("payee", json(if (payee) tx.payee else payeeCode(uid, tx.payee)), if (payee) Shown.On else Shown.Masked),
        Triple(
            "amount",
            if (amount) BigDecimal.valueOf(tx.amount, 2).stripTrailingZeros().toPlainString() else "null",
            if (amount) Shown.On else Shown.Masked,
        ),
        Triple("currency", json(tx.currency.uppercase()), Shown.On),
        if (date) Triple("date", json(tx.day), Shown.On) else Triple("month", json(tx.day.take(7)), Shown.Masked),
        Triple("note", json(tx.note), if (note) Shown.On else Shown.Off),
        Triple("account", json(tx.account), if (account) Shown.On else Shown.Off),
    )
}
