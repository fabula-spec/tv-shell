package ru.tvshell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val component: ComponentName,
    val label: String,
    /** ТВ-баннер 16:9, если приложение его предоставляет. */
    val banner: Drawable?,
    val icon: Drawable?,
    /** true — приложение для Android TV (категория LEANBACK_LAUNCHER). */
    val isTv: Boolean,
    /** Приложение отмечено разработчиком как «Видео» (Android 8+). */
    val isVideo: Boolean = false,
) {
    val packageName: String get() = component.packageName
}

object AppRepository {

    /** Загружает список запускаемых приложений. Вызывать не из UI-потока. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()

        fun collect(category: String, isTv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val infos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: RuntimeException) {
                emptyList()
            }
            for (ri in infos) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == context.packageName) continue
                if (result.containsKey(ai.packageName)) continue
                val banner = if (isTv) loadBanner(pm, ai) else null
                val icon = try { ri.loadIcon(pm) } catch (e: RuntimeException) { null }
                result[ai.packageName] = AppEntry(
                    component = ComponentName(ai.packageName, ai.name),
                    label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { ai.packageName },
                    banner = banner,
                    icon = icon,
                    isTv = isTv,
                    isVideo = Build.VERSION.SDK_INT >= 26 &&
                        ai.applicationInfo?.category == ApplicationInfo.CATEGORY_VIDEO,
                )
            }
        }

        // Сначала ТВ-версии приложений (с баннерами), потом обычные.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isTv = true)
        collect(Intent.CATEGORY_LAUNCHER, isTv = false)

        val collator = Collator.getInstance(Locale("ru"))
        return result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun loadBanner(pm: PackageManager, ai: android.content.pm.ActivityInfo): Drawable? =
        try {
            ai.loadBanner(pm) ?: ai.applicationInfo?.loadBanner(pm)
        } catch (e: RuntimeException) {
            null
        }
}
