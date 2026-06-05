package com.rawmilk.app

import android.app.Activity
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayList

/**
 * Thread-safe logging buffer to capture background push notification events, token updates,
 * and WebView integrations, making developer diagnostics a breeze.
 */
object FCMDiagnostics {
    private val logList = mutableListOf<String>()

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formattedLog = "[$timestamp] $message"
        Log.d("RawMilkFCMDiag", formattedLog)
        synchronized(logList) {
            logList.add(formattedLog)
            if (logList.size > 200) {
                logList.removeAt(0)
            }
        }
    }

    fun getLogs(): List<String> {
        synchronized(logList) {
            return ArrayList(logList)
        }
    }

    fun clearLogs() {
        synchronized(logList) {
            logList.clear()
        }
    }
}

/**
 * Simplified class representation to prevent compilation failures. All customer-facing diagnostics
 * widgets, console overlays, and draggable float bubbles have been removed for production.
 */
class DiagnosticsHelper(private val activity: Activity, private val onNavigateToUrl: (String) -> Unit) {
    fun attachFloatingBubble() {
        // Draggable float bubble, token displays, copy buttons, and overlays are removed in production.
    }
}
