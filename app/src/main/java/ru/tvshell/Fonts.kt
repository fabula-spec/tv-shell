package ru.tvshell

import android.content.Context
import android.graphics.Typeface
import android.os.Build

/** Шрифт Onest (как на макете) с нужной насыщенностью. */
object Fonts {
    private val cache = HashMap<Int, Typeface>()

    fun get(context: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Typeface.Builder(context.assets, "fonts/onest.ttf")
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            } catch (e: RuntimeException) {
                fallback(weight)
            }
        } else {
            fallback(weight)
        }
    }

    private fun fallback(weight: Int): Typeface = when {
        weight >= 600 -> Typeface.create("sans-serif", Typeface.BOLD)
        weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
