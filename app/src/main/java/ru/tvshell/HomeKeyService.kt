package ru.tvshell

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Необязательный сервис специальных возможностей.
 * Перехватывает кнопку «Домой» на пульте и открывает TV Shell вместо
 * штатного домашнего экрана, а также открывает оболочку после включения ТВ.
 * Содержимое экрана не читает.
 */
class HomeKeyService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Сервис подключается сразу после загрузки системы —
        // в этот момент показываем свою оболочку.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openShell()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openShell()
        return true // поглощаем нажатие, чтобы штатный лаунчер не открывался
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun openShell() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            // ничего: в худшем случае откроется штатный экран
        }
    }

    private companion object {
        const val BOOT_WINDOW_MS = 3 * 60 * 1000L
    }
}
