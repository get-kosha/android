package app.getkosha.model

/**
 * Money rules shared by every screen, ported from ../kosha/src/lib/core.ts. Category "0" (balance corrections)
 * and "transfer" are neutral: neither income nor spending. "transfer_fee" counts as spending.
 */
object Categories {
    const val CORRECTION = "0"
    const val TRANSFER = "transfer"
    const val TRANSFER_FEE = "transfer_fee"

    fun isNeutral(id: String) = id == CORRECTION || id == TRANSFER

    /** Kosha's own categories, which the person can't pick for an ordinary entry. */
    fun isSystem(id: String) = isNeutral(id) || id == TRANSFER_FEE
}

/**
 * Converts at rates held as units per 1 USD (core.ts convert), rounded to whole minor units.
 * ponytail: an unknown currency passes through unconverted, as on the web.
 */
fun convert(minor: Long, from: String, to: String, perUsd: Map<String, Double>): Long {
    if (from == to) return minor
    val f = perUsd[from] ?: return minor
    val t = perUsd[to] ?: return minor
    if (f == 0.0 || t == 0.0) return minor
    return Math.round(minor / f * t)
}

/** The category the payee rules give (core.ts guessCategory): the longest rule that matches wins. */
fun guessCategory(payee: String, rules: List<Rule>): String? {
    val n = payee.trim().lowercase()
    if (n.isEmpty()) return null
    var best: Rule? = null
    for (r in rules) {
        val t = r.title.lowercase()
        val hit = if (r.exact != 0) n == t else n.contains(t)
        if (hit && (best == null || t.length > best.title.length)) best = r
    }
    return best?.categoryId
}

const val MAX_TAGS = 20

/** One hashtag as stored (core.ts cleanTag): lowercase, no '#', letters, digits, '-' and '_' only. */
fun cleanTag(tag: String): String =
    tag.trim().replace(Regex("^#+"), "").lowercase().replace(Regex("""[^\p{L}\p{M}\p{N}_-]"""), "").take(32)

/** Typed text ("#trip, food rome") as unique stored tags in the order typed (core.ts parseTags). */
fun parseTags(text: String): List<String> =
    text.split(Regex("""[\s,]+""")).map(::cleanTag).filter { it.isNotEmpty() }.distinct().take(MAX_TAGS)
