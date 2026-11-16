package app.pocketfader.client

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/**
 * The app's words in the language chosen in Settings (or the phone's own).
 *
 * Text is written in English in the code, inside tr() (trn() with the English for one and for more, for a count; trKey() marks a
 * word kept in a table, translated where it is shown). The translations are assets/i18n/<code>.json, built from the
 * repository's i18n/ folder by tools/i18n.py. The words in use are Compose state: a screen that read them is drawn
 * again when the language changes.
 */
object I18n {
    /** Every language there is: code and its own name. */
    val LANGUAGES = listOf(
        "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "pt-BR" to "Português (Brasil)",
        "it" to "Italiano", "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "zh-CN" to "简体中文",
    )
    private val CODES = LANGUAGES.map { it.first }

    /** The language in use (a code from LANGUAGES). */
    var code by mutableStateOf("en")
        private set
    internal var table by mutableStateOf<Map<String, Any>>(emptyMap())
        private set
    private var appCtx: Context? = null

    /** Every setting there is: "auto", then the codes. */
    val SETTINGS = listOf("auto") + LANGUAGES.map { it.first }

    /** Use a language setting: "auto" (the phone's language) or a code. Safe to call again with the same one. */
    fun use(ctx: Context, pref: String) {
        appCtx = ctx.applicationContext
        val next = resolve(pref, systemLanguages())
        if (next == code && (next == "en" || table.isNotEmpty())) return
        table = if (next == "en") emptyMap() else read(ctx, next)
        code = next
    }

    /** Use a setting once the app has started (from the settings screen or the computer). */
    fun use(pref: String) {
        appCtx?.let { use(it, pref) }
    }

    /** The language for a setting: the one chosen, or for "auto" the first of [wanted] there is (else English). */
    fun resolve(pref: String, wanted: List<String>): String {
        if (pref in CODES) return pref
        for (w in wanted) {
            val s = w.replace('_', '-').lowercase(Locale.ROOT)
            CODES.firstOrNull { it.lowercase(Locale.ROOT) == s }?.let { return it }
            CODES.firstOrNull { it.substringBefore('-').lowercase(Locale.ROOT) == s.substringBefore('-') }?.let { return it }
        }
        return "en"
    }

    /** The plural form a whole number takes in a language (CLDR categories). */
    fun plural(code: String, n: Long): String {
        val x = kotlin.math.abs(n)
        return when (code) {
            "ja", "ko", "zh-CN" -> "other"
            "ru" -> {
                val a = x % 10; val b = x % 100
                if (a == 1L && b != 11L) "one" else if (a in 2L..4L && (b < 12L || b > 14L)) "few" else "many"
            }
            "fr", "pt-BR" -> if (x <= 1) "one" else "other"
            else -> if (x == 1L) "one" else "other"
        }
    }

    private fun systemLanguages(): List<String> {
        val c = Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= 24) (0 until c.locales.size()).map { c.locales[it].toLanguageTag() }
        else listOf(@Suppress("DEPRECATION") c.locale.toLanguageTag())
    }

    private fun read(ctx: Context, code: String): Map<String, Any> = try {
        val o = JSONObject(ctx.assets.open("i18n/$code.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        val out = HashMap<String, Any>(o.length() * 2)
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is String -> out[k] = v
                is JSONObject -> out[k] = v.keys().asSequence().associateWith { v.optString(it) }
            }
        }
        out
    } catch (e: Exception) {
        emptyMap()
    }

    internal fun fill(s: String, args: Map<String, Any?>): String {
        if (args.isEmpty() || '{' !in s) return s
        return PLACEHOLDER.replace(s) { m -> args[m.groupValues[1]]?.toString() ?: m.value }
    }

    private val PLACEHOLDER = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
}

/** [text] (English) in the language in use; {name}s in it are filled from [args] ("name" to value). */
fun tr(text: String, vararg args: Pair<String, Any?>): String {
    val s = I18n.table[text] as? String ?: text
    return if (args.isEmpty()) s else I18n.fill(s, args.toMap())
}

/** A count: [one] is the English for 1, [other] the English for any other number; {n} in them is [n]. */
fun trn(one: String, other: String, n: Number, vararg args: Pair<String, Any?>): String {
    @Suppress("UNCHECKED_CAST")
    val forms = I18n.table[other] as? Map<String, String>
    val s = forms?.let { it[I18n.plural(I18n.code, n.toLong())] ?: it["other"] }
        ?: if (n.toLong() == 1L) one else other
    return I18n.fill(s, mapOf<String, Any?>("n" to n) + args)
}

/** Marks English kept in a table (a list of options, say) as text to translate; tr() it where it is shown. */
fun trKey(text: String): String = text
