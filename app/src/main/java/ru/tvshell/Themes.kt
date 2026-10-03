package ru.tvshell

import android.graphics.Color

/** Цветовая тема превью по приложению-источнику (как на макете А). */
data class SourceTheme(
    val name: String,
    val keys: List<String>,
    val bg: Int,
    val soft: Int,
    val accent: Int,
    val deep: Int,
) {
    /** Совпадает ли подпись установленного приложения с этим кинотеатром. */
    fun matches(label: String): Boolean {
        val l = label.lowercase().trim()
        return keys.any { k ->
            l == k || l.startsWith("$k ") || l.startsWith("$k.") || l.startsWith("$k:") ||
                (k.length >= 6 && l.contains(k))
        }
    }
}

object Themes {
    private fun c(hex: String) = Color.parseColor(hex)

    val all = listOf(
        SourceTheme("Кинопоиск", listOf("кинопоиск", "kinopoisk"), c("#fbdcc8"), c("#f7bf9a"), c("#f26b1d"), c("#b8501a")),
        SourceTheme("Okko", listOf("okko"), c("#ddd5fb"), c("#c3b4f8"), c("#6b3cf0"), c("#4a24b8")),
        SourceTheme("Wink", listOf("wink"), c("#f9d0da"), c("#f3a6b9"), c("#e8204f"), c("#a3163a")),
        SourceTheme("Иви", listOf("иви", "ivi"), c("#cfe8da"), c("#9fd0b4"), c("#1c8a5a"), c("#14603f")),
        SourceTheme("Start", listOf("start"), c("#d4e4f7"), c("#a9c8ee"), c("#2f6fd6"), c("#1d4c9a")),
        SourceTheme("Premier", listOf("premier"), c("#e2e2e6"), c("#c4c4cc"), c("#3a3a44"), c("#1f1f26")),
        SourceTheme("KION", listOf("kion"), c("#f7d6d0"), c("#efaea2"), c("#d8402a"), c("#9a2a1a")),
        SourceTheme("Амедиатека", listOf("амедиатека", "amediateka"), c("#dcdcf2"), c("#babae6"), c("#3b3fa8"), c("#262a78")),
        SourceTheme("more.tv", listOf("more.tv", "more tv"), c("#f6e3c8"), c("#ecc68f"), c("#d48a1c"), c("#965f10")),
        SourceTheme("Смотрёшка", listOf("смотрёшка", "смотрешка", "smotreshka"), c("#d2eef0"), c("#a4dde2"), c("#1897a3"), c("#0f6a73")),
    )

    val fallback = SourceTheme("", emptyList(), c("#dfe3ea"), c("#c8ceda"), c("#5a6070"), c("#3d4250"))

    fun byName(name: String): SourceTheme = all.firstOrNull { it.name == name } ?: fallback

    fun forLabel(label: String): SourceTheme? = all.firstOrNull { it.matches(label) }

    fun isYouTube(app: AppEntry): Boolean =
        app.packageName.startsWith("com.google.android.youtube") || app.label.lowercase().contains("youtube")

    /** Кинотеатры среди установленных: известные по названию + категория «Видео», без YouTube. */
    fun streamingApps(apps: List<AppEntry>): List<AppEntry> =
        apps.filter { !isYouTube(it) && (forLabel(it.label) != null || it.isVideo) }
}
