package app.getkosha.overview

import org.junit.Assert.assertEquals
import org.junit.Test

/** The web's quick add asserts (../kosha/src/lib/core.test.ts), so both parse a line the same way. */
class QuickAddTest {
    private val qw = listOf(Named("v", "Visa Card"), Named("n", "Main account"))
    private val qc = listOf(Named("dining", "Dining"), Named("groceries", "Groceries"))
    private fun q(s: String, locale: String = "en", c: List<Named> = qc) = QuickAdd.parse(s, qw, c, locale)
    private fun ai(s: String, locale: String = "en") = QuickAdd.needsAi(s, q(s, locale), locale)
    private fun local(s: String, locale: String) = q(s, locale).let { listOf(it.amount, it.date, it.payee, QuickAdd.needsAi(s, it, locale)) }

    @Test fun parsesALine() {
        assertEquals(
            QuickParse(amount = 2957, date = QuickDate.Yesterday, walletId = "v", categoryId = "dining", payee = "Pizza Hut"),
            q("29,57 Pizza Hut dining visa yesterday"),
        )
        val qt = q("12 #Trip lunch #trip #rome-2026")
        assertEquals(listOf("trip", "rome-2026"), qt.tags)
        assertEquals("lunch", qt.payee)
        val qi = q("+4200 salary main di")
        assertEquals(420000L, qi.amount)
        assertEquals(true, qi.income)
        assertEquals("n", qi.walletId)
        assertEquals(null, qi.categoryId) // "di" is under 3 letters
        assertEquals("salary di", qi.payee)
        assertEquals(null, q("tesco").amount)
        assertEquals(QuickDate.Yesterday, q("12,50 tesco ayer").date)
        assertEquals("tesco", q("12.50 tesco 昨天").payee)
        assertEquals("d", q("9000 외식", c = listOf(Named("d", "외식"))).categoryId)
    }

    @Test fun onlySentencesGoToAi() {
        assertEquals(false, ai("24.90 pizza hut yesterday visa"))
        assertEquals(false, ai("coffee 4.5"))
        assertEquals(false, ai("+3200 salary"))
        assertEquals(false, ai("tesco"))
        assertEquals(true, ai("lunch with sam at nandos 18.50 last friday"))
        assertEquals(true, ai("€12 coffee"))
        assertEquals(true, ai("uber 23 eur"))
        assertEquals(true, ai("rent 1200 on the 3rd"))
        assertEquals(true, ai("twelve fifty nandos"))
        val qg = q("12,50 Lebensmittel gestern", c = listOf(Named("food", "Lebensmittel")))
        assertEquals(1250L, qg.amount)
        assertEquals(QuickDate.Yesterday, qg.date)
        assertEquals("food", qg.categoryId)
        assertEquals("", qg.payee)
        assertEquals(QuickDate.Today, q("Bäcker 3 heute").date)
        assertEquals(null, q("constructor 3").date)
        assertEquals(false, ai("Bäcker 4,20 gestern visa"))
        assertEquals(true, ai("Miete 1200 am 3."))
        assertEquals(true, ai("Essen mit Sam 18,50 letzten Freitag"))
        assertEquals(true, ai("Kino 12 vorgestern"))
    }

    @Test fun numberFormats() {
        assertEquals(123456L, q("1,234.56 rent", "en").amount)
        assertEquals(123456L, q("1.234,56 Miete", "de").amount)
        assertEquals(120050L, q("1'200.50 Miete", "de").amount)
        assertEquals(123400L, q("1,234 rent", "en").amount)
        assertEquals(50L, q("0,5 café", "fr").amount)
        assertEquals(10000000L, q("1,00,000 किराया", "hi").amount)
        assertEquals(3000000L, q("３万円 家賃", "ja").amount)
        assertEquals(1500000L, q("1.5万 房租", "zh").amount)
    }

    @Test fun everyLanguage() {
        val y = QuickDate.Yesterday
        val t = QuickDate.Today
        assertEquals(listOf(2490L, y, "pizza hut", false), local("24.90 pizza hut yesterday", "en"))
        assertEquals(listOf(420L, t, "Bäcker", false), local("Bäcker 4,20 heute", "de"))
        assertEquals(listOf(1250L, y, "mercadona", false), local("12,50 mercadona ayer", "es"))
        assertEquals(listOf(420L, t, "boulangerie", false), local("4,20 boulangerie aujourd’hui", "fr"))
        assertEquals(listOf(350L, y, "bar", false), local("3,50 bar ieri", "it"))
        assertEquals(listOf(890L, t, "padaria", false), local("padaria 8,90 hj", "pt"))
        assertEquals(listOf(695L, y, "albert heijn", false), local("6,95 albert heijn gister", "nl"))
        assertEquals(listOf(35000L, y, "пятёрочка", false), local("350 пятёрочка вчера", "ru"))
        assertEquals(listOf(5000L, t, "दूध", false), local("दूध ५० आज", "hi"))
        assertEquals(listOf(85000L, y, "ランチ", false), local("昨日ランチ850", "ja"))
        assertEquals(listOf(3500L, y, "午饭", false), local("昨天午饭35", "zh"))
        assertEquals(listOf(900000L, t, "점심", false), local("점심 9000 오늘", "ko"))
        assertEquals(listOf(120000L, y, "本屋", false), local("１２００ 本屋 きのう", "ja"))
        assertEquals(8000000L, q("11月3日 家賃 80000", "ja").amount)
        assertEquals(y, q("hier boulangerie 4,20", "fr").date)
        assertEquals(y, q("hier boulangerie 4,20", "en").date)
        assertEquals(null, q("hier Bäcker 4,20", "de").date)
        assertEquals(900000L, q("어제 점심 9000원", "ko").amount)
        assertEquals("점심", q("어제 점심 9000원", "ko").payee)
        assertEquals(3000000L, q("3만원 택시", "ko").amount)
        assertEquals(true, q("500₽ такси", "ru").currency)
        assertEquals(2000L, q("R$ 20 padaria", "pt").amount)
        assertEquals("午饭", q("35元午饭", "zh").payee)
        for ((s, l) in listOf("12,50 € café" to "fr", "35元 打车" to "zh", "スタバ 500円" to "ja", "500 руб такси" to "ru")) {
            assertEquals(s, true, ai(s, l))
        }
        for ((s, l) in listOf(
            "café con Ana 4,50 el viernes" to "es",
            "déjeuner avec Paul 18,50 vendredi" to "fr",
            "pizza con Marco 25 sabato" to "it",
            "almoço com a Ana 30 sexta-feira" to "pt",
            "lunch met Sanne 15 vrijdag" to "nl",
            "обед с Машей 800 в пятницу" to "ru",
            "कल दूध 50" to "hi",
            "スタバで500" to "ja",
            "一昨日 ランチ 900" to "ja",
            "在星巴克花了35" to "zh",
            "스타벅스에서 5000" to "ko",
            "Essen 20 vorgestern" to "de",
        )) {
            assertEquals(s, true, ai(s, l))
        }
        assertEquals(false, ai("mars 1,20", "en"))
        assertEquals(true, ai("mars 1,20 courses", "fr"))
    }
}
