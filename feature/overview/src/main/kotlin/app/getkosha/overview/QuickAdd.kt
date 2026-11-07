package app.getkosha.overview

import app.getkosha.model.cleanTag
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.regex.Pattern

// Quick add parses one line of text into a transaction as you type. A port of ../kosha/src/lib/quick-add.ts,
// pure so QuickAddTest can run the web's asserts (core.test.ts). Amounts use BigDecimal, never floating point.

enum class QuickDate { Today, Yesterday }

data class QuickParse(
    /** Minor units, always positive; [income] carries the sign. */
    val amount: Long? = null,
    val income: Boolean = false,
    /** Today or yesterday when typed (in any language Kosha speaks). */
    val date: QuickDate? = null,
    val walletId: String? = null,
    val categoryId: String? = null,
    val payee: String = "",
    /** Words typed as #hashtags, cleaned as stored. */
    val tags: List<String> = emptyList(),
    /** A currency sign or unit was typed (€12, 9000원, 35元), so the account's own currency may not apply. */
    val currency: Boolean = false,
)

/** An account or category quick add can match by name. */
data class Named(val id: String, val name: String)

object QuickAdd {
    private class Words(val today: List<String>, val yesterday: List<String>, val sentence: String, val plain: List<String> = emptyList())

    // The words quick add knows, per language (quick-add.ts WORDS). Every language's words work whatever the UI
    // language is; `plain` words stay ordinary while that language is the UI language.
    private val WORDS = mapOf(
        "en" to Words(
            listOf("today", "tdy"), listOf("yesterday", "yday", "yest"),
            "at|for|from|with|on|in|via|by|and|paid|pay|spent|spend|bought|got|received|refund|refunded|split|last|ago|this|next|tomorrow|tonight|morning|noon|evening|week|month|(mon|tues?|wed(nes)?|thu(rs)?|fri|satur|sun)(day)?|jan(uary)?|feb(ruary)?|march|april|june|july|aug(ust)?|sept?(ember)?|oct(ober)?|nov(ember)?|dec(ember)?|\\d+(st|nd|rd|th|am|pm)|\\d{1,2}:\\d{2}|eur|usd|gbp|inr|chf|jpy|aud|cad|sgd|aed|bucks|euros?|dollars?|pounds?|quid|rupees?",
            listOf("met", "mars"),
        ),
        "de" to Words(
            listOf("heute", "heut"), listOf("gestern"),
            "am|um|für|von|vom|mit|bei|beim|im|über|und|bezahlt|gezahlt|ausgegeben|gekauft|bekommen|erhalten|erstattet|erstattung|geteilt|letzte[nrs]?|vor|diese[nrs]?|nächste[nrs]?|morgen|übermorgen|vorgestern|abends?|morgens|mittags?|tage?n?|woche|monat|(mon|diens|donners|frei|sams|sonn)tag|mittwoch|januar|februar|märz|mai|juni|juli|oktober|dezember|uhr|franken|rupien",
            listOf("hier"),
        ),
        "es" to Words(
            listOf("hoy"), listOf("ayer"),
            "con|para|por|en|desde|pagu[eé]|pagad[oa]|gast[eé]|gastad[oa]|compr[eé]|comprad[oa]|recib[ií]|recibid[oa]|reembolso|devoluci[oó]n|dividid[oa]|pasad[oa]|[uú]ltim[oa]|pr[oó]xim[oa]|est[ea]|ma[nñ]ana|anoche|anteayer|antier|tarde|noche|mediod[ií]a|hace|d[ií]as|semana|mes|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|enero|febrero|marzo|abril|mayo|junio|julio|agosto|sep?tiembre|octubre|noviembre|diciembre|pesos|d[oó]lares|pavos",
        ),
        "fr" to Words(
            listOf("aujourd'hui", "aujourd’hui", "auj"), listOf("hier"),
            "avec|pour|par|en|et|pay[ée]e?|d[ée]pens[ée]e?|achet[ée]e?|re[çc]ue?|rembours[ée]e?|remboursement|partag[ée]e?|derni[eè]re?|prochaine?|demain|avant-hier|soir|matin|midi|semaine|mois|jours|(lun|mar|mercre|jeu|vendre|same)di|dimanche|janvier|f[ée]vrier|mars|avril|juin|juillet|ao[uû]t|septembre|octobre|novembre|d[ée]cembre|balles",
            listOf("mes"),
        ),
        "it" to Words(
            listOf("oggi"), listOf("ieri"),
            "con|per|pagat[oaie]|spes[oi]|comprat[oaie]|ricevut[oaie]|rimbors[oa]|rimborsat[oaie]|divis[oa]|scors[oa]|prossim[oa]|domani|dopodomani|(l['’])?altroieri|l['’]altro|stasera|stamattina|stamani|mattina|sera|pomeriggio|settimana|mese|giorni|(lune|marte|mercole|giove|vener)d[iì]|sabato|domenica|gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre",
        ),
        "pt" to Words(
            listOf("hoje", "hj"), listOf("ontem"),
            "com|para|pra|por|em|paguei|pago|gastei|comprei|comprad[oa]|recebi|recebid[oa]|reembolso|estorno|dividid[oa]|passad[oa]|[uú]ltim[oa]|pr[oó]xim[oa]|amanh[aã]|anteontem|semana|m[eê]s|dias|atr[aá]s|(segunda|ter[cç]a|quarta|quinta|sexta)(-feira)?|s[aá]bado|domingo|janeiro|fevereiro|mar[cç]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro|reais",
        ),
        "nl" to Words(
            listOf("vandaag"), listOf("gisteren", "gister"),
            "met|voor|bij|op|en|om|betaald|uitgegeven|gekocht|gekregen|ontvangen|terugbetaald|terugbetaling|gedeeld|vorige?|afgelopen|volgende?|morgen|overmorgen|eergisteren|vanavond|vanochtend|vanmorgen|vanmiddag|ochtend|middag|avond|maand|dagen|geleden|uur|(maan|dins|woens|donder|vrij|zater|zon)dag|januari|februari|maart|mei|augustus|oktober",
            listOf("hier"),
        ),
        "ru" to Words(
            listOf("сегодня"), listOf("вчера"),
            "в|во|на|с|со|за|для|и|от|из|через|(за|о)?платил[аи]?|потратил[аи]?|купил[аи]?|получил[аи]?|вернул[аи]?|возврат|пополам|прошл\\p{L}*|следующ\\p{L}*|эт(от|у|ой|а)|завтра|позавчера|вечером|утром|днём|днем|недел\\p{L}*|месяц\\p{L}*|дн(я|ей)|назад|час(а|ов)?|понедельник|вторник|сред[аеуы]|четверг|пятниц[аеуы]|суббот[аеуы]|воскресень[еяю]|январ[ья]|феврал[ья]|марта?|апрел[ья]|ма[йя]|июн[ья]|июл[ья]|августа?|сентябр[ья]|октябр[ья]|ноябр[ья]|декабр[ья]|евро|долл\\p{L}*|бакс\\p{L}*",
        ),
        // Hindi has only "today": "कल" (and "kal") means both yesterday and tomorrow, so it goes to AI.
        "hi" to Words(
            listOf("आज", "aaj"), emptyList(),
            "में|से|को|के|का|की|और|लिए|साथ|पर|ख़?र्च[ाे]?|दिए|दिया|दी|मिले|मिला|मिली|ख़?रीद[ाे]|भुगतान|वापस|रिफंड|पिछल[ेा]|अगल[ेा]|परसों|कल|सुबह|शाम|रात|दोपहर|हफ़?्त[ेा]|सप्ताह|महीन[ेा]|दिन|पहले|बजे|(सोम|मंगल|बुध|गुरु|बृहस्पति|शुक्र|शनि|रवि)वार|जनवरी|फ़?रवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सित(ं|म्)बर|अक्[टत]ूबर|नव(ं|म्)बर|दिस(ं|म्)बर|डॉलर|kal|parso",
        ),
        "ja" to Words(
            listOf("今日", "きょう", "本日"), listOf("昨日", "きのう"),
            "で|払[っい]|支払|買っ|使っ|もらっ|貰っ|返金|割り?勘|先週|来週|今週|先月|来月|今月|明日|あした|一昨日|おととい|今朝|今夜|昨夜|曜|ドル|ユーロ",
        ),
        "zh" to Words(
            listOf("今天", "今日"), listOf("昨天", "昨日"),
            "花了|付了|[买買]了|[给給]了|收到|退款|退了|平分|在(?![线線])|上[周週]|下[周週]|本[周週]|[这這][周週]|上[个個]月|下[个個]月|明天|前天|今晚|昨晚|早上|晚上|中午|下午|上午|[周週][一二三四五六日天]|星期|礼拜|禮拜|美元|[欧歐]元",
        ),
        "ko" to Words(
            listOf("오늘"), listOf("어제"),
            "에서|이랑|하고|한테|에게|샀[어다음]|썼[어다음]|냈[어다음]|결제|받았|환불|더치|나눠|지난|다음|이번|내일|그저께|그제|오전|오후|[월화수목금토일]요일|달러|유로",
        ),
    )
    private val ATTACHED = listOf("ja", "zh", "ko")
    private const val CI = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE

    // Chinese, Japanese and Korean pack a category into two characters and need no spaces.
    private val CJK = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")
    private fun cjk(s: String) = CJK.matcher(s).find()

    private fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

    private val DATES: Map<String, QuickDate> = buildMap {
        for (w in WORDS.values) {
            w.today.forEach { put(nfkc(it), QuickDate.Today) }
            w.yesterday.forEach { put(nfkc(it), QuickDate.Yesterday) }
        }
    }

    // Longest first, so 一昨日 is never read as 昨日.
    private val CJK_DATES = DATES.keys.filter(::cjk).sortedByDescending { it.length }
    private fun sentence(langs: List<String>) = nfkc(langs.joinToString("|") { WORDS.getValue(it).sentence })
    private val SPACED = Pattern.compile("^(${sentence(WORDS.keys.filterNot { it in ATTACHED })})$", CI)
    private val JOINED = Pattern.compile(sentence(ATTACHED), CI)

    // Currency signs, and the units people write after a number (1200円, 35元, 9000원, 500₽, 50रु).
    private const val SIGN = "R\\$|US\\$|[€$£₹¥₩₽]"
    private const val UNIT =
        "円|元|块钱?|塊錢?|원|ドル|ユーロ|美元|[欧歐]元|달러|유로|руб(?:лей|ля|ль)?\\.?|р\\.?|रु\\.?|रू\\.?|रुपये|रुपए|रुपया"

    // 3万 and 5천 are 30,000 and 5,000.
    private val MULT = mapOf("万" to 10_000L, "萬" to 10_000L, "만" to 10_000L, "千" to 1_000L, "천" to 1_000L)

    // A number with `.` or `,` decimals and optional grouping: 1.234,56, 1,234.56, 1'234.50, 1,00,000 (lakh).
    // Three digits after a separator always group thousands, since no currency here has three decimals.
    private const val NUMBER = "\\d+|[1-9]\\d{0,2}(?:([.,'’])\\d{3})(?:\\3\\d{3})*|[1-9]\\d?(?:,\\d\\d)+,\\d{3}"
    private val MONEY = Pattern.compile("^([+-])?(?:$SIGN)?($NUMBER)(?:[.,](\\d{1,2}))?([万萬만千천])?($SIGN|$UNIT)?$", CI)
    private val CURRENCY_ONLY = Pattern.compile("^($SIGN|$UNIT)$", CI)
    private val MONEY_END = Pattern.compile("($SIGN|$UNIT)$", CI)
    private val RUN = Pattern.compile("[+-]?(?:$SIGN)?\\d[\\d.,'’]*[万萬만千천]?(?:$SIGN|$UNIT)?", CI)

    // Full-width forms (１２，５０ ＃旅行) become plain, and Devanagari digits become 0-9.
    private fun normalize(q: String) = buildString {
        for (ch in nfkc(q)) append(if (ch in '०'..'९') ('0' + (ch - '०')) else ch)
    }

    private val EDGE_PUNCT = Regex("^\\p{P}+|\\p{P}+$")
    private fun bare(w: String) = w.lowercase().replace(EDGE_PUNCT, "")

    /**
     * Splits words in scripts written without spaces: a number at the end of a run of text or closed by a
     * currency ("午饭35", "35元午饭", "점심9000원", but not "11月3日"), then a date word at either end ("昨天午饭").
     */
    private fun splitJoined(t: String): List<String> {
        if (!cjk(t)) return listOf(t)
        val parts = mutableListOf<String>()
        var at = 0
        val m = RUN.matcher(t)
        while (m.find()) {
            val end = m.end()
            val before = if (m.start() > 0) t[m.start() - 1].toString() else ""
            if ((end < t.length && !MONEY_END.matcher(m.group()).find()) || before.any { it.isAsciiDigit() } || before in MULT) continue
            parts += t.substring(at, m.start())
            parts += m.group()
            at = end
        }
        parts += t.substring(at)
        return parts.filter { it.isNotEmpty() }.flatMap(::splitDate)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun splitDate(t: String): List<String> {
        // A sentence keeps its date word, since AI reads it whole (一昨日 is not 昨日).
        if (!cjk(t) || JOINED.matcher(t).find()) return listOf(t)
        for (w in CJK_DATES) {
            if (t == w) return listOf(t)
            if (t.startsWith(w)) return listOf(w) + splitDate(t.substring(w.length))
            if (t.endsWith(w)) return splitDate(t.substring(0, t.length - w.length)) + w
        }
        return listOf(t)
    }

    private val SPLIT = Regex("[\\s\\p{Z}、。]+")
    private fun tokenize(q: String) = normalize(q).split(SPLIT).filter { it.isNotEmpty() }.flatMap(::splitJoined)

    /** Whether `word` (bare) is ordinary in the UI language, whatever it means in another. */
    private fun plain(word: String, locale: String) = WORDS[locale]?.plain?.contains(word) == true

    private fun dateOf(word: String, locale: String): QuickDate? {
        if (plain(word, locale)) return null
        val own = WORDS[locale]
        if (own?.today?.contains(word) == true) return QuickDate.Today
        if (own?.yesterday?.contains(word) == true) return QuickDate.Yesterday
        return DATES[word]
    }

    private val MARKS = Regex("\\p{M}")
    private val WHITESPACE = Regex("\\s+")

    /**
     * `#word` is a tag. The first number is the amount, written the way any of Kosha's languages writes it
     * (12,50, 1.234,56, 1,00,000, ¥1200, 9000원, 3万), with a leading `+` for income. Today or yesterday, in any
     * language Kosha speaks, sets the date. A word equal to an account's name or its first word sets the account.
     * A word of 3 or more letters (2 in Chinese, Japanese or Korean) that starts a category name sets the
     * category. The remaining words are the payee.
     */
    fun parse(q: String, wallets: List<Named>, categories: List<Named>, locale: String): QuickParse {
        var out = QuickParse()
        val tags = mutableListOf<String>()
        val words = mutableListOf<String>()
        for (t in tokenize(q)) {
            val low = bare(t)
            if (t.startsWith("#")) {
                val tag = cleanTag(t)
                if (tag.isNotEmpty() && tag !in tags) tags += tag
                continue
            }
            val money = if (out.amount == null) MONEY.matcher(t.replace(Regex("[.,]$"), "")).takeIf { it.matches() } else null
            if (money != null) {
                val whole = money.group(2).orEmpty().replace(Regex("[.,'’]"), "")
                val value = BigDecimal("$whole.${money.group(4) ?: "0"}")
                    .multiply(BigDecimal.valueOf(money.group(5)?.let(MULT::getValue) ?: 1L))
                out = out.copy(
                    amount = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                    income = money.group(1) == "+",
                    currency = out.currency || money.group(6) != null || Regex("^[+-]?[^\\d+-]").containsMatchIn(t),
                )
                continue
            }
            if (CURRENCY_ONLY.matcher(t).matches()) {
                out = out.copy(currency = true)
                continue
            }
            val date = if (out.date != null) null else dateOf(low, locale)
            if (date != null) {
                out = out.copy(date = date)
                continue
            }
            val w = if (out.walletId != null) null else wallets.firstOrNull { x ->
                val n = x.name.lowercase()
                n == low || n.split(WHITESPACE)[0] == low
            }
            if (w != null) {
                out = out.copy(walletId = w.id)
                continue
            }
            // Vowel signs (Devanagari, Hangul jamo) are marks, not letters.
            val stripped = low.replace(MARKS, "")
            val letters = stripped.codePointCount(0, stripped.length)
            val c = if (out.categoryId != null || letters < (if (cjk(low)) 2 else 3)) null
            else categories.firstOrNull { it.name.lowercase().startsWith(low) }
            if (c != null) {
                out = out.copy(categoryId = c.id)
                continue
            }
            words += t
        }
        return out.copy(payee = words.joinToString(" "), tags = tags)
    }

    private val MONEY_CHAR = Regex("[0-9€$£₹¥₩₽]")

    /**
     * Whether quick add should ask AI to read the line (quick-add.ts needsAi): a leftover sentence word, a
     * currency, digits inside the payee, a long payee, or no amount in three or more words.
     */
    fun needsAi(q: String, p: QuickParse, locale: String): Boolean {
        val tokens = tokenize(q)
        if (tokens.size < 2) return false
        val words = p.payee.split(WHITESPACE).map(::bare).filter { it.isNotEmpty() }
        return p.currency ||
            words.any { w ->
                MONEY_CHAR.containsMatchIn(w) ||
                    (!plain(w, locale) && (SPACED.matcher(w).matches() || (cjk(w) && JOINED.matcher(w).find())))
            } ||
            words.size > 3 ||
            (p.amount == null && tokens.size >= 3)
    }
}
