package com.rawmilk.app

import android.os.Build
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.google.androidbrowserhelper.trusted.LauncherActivity

/**
 * Custom TWA LauncherActivity that optimizes startup performance and pre-warms
 * Chrome Custom Tabs to ensure instant, seamless launching without UI transition pop-ins.
 */
class CustomLauncherActivity : LauncherActivity() {

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        disableActivityTransitions()
        super.onCreate(savedInstanceState)
        prewarmCustomTabs()
    }

    override fun onResume() {
        disableActivityTransitions()
        super.onResume()
    }

    @Suppress("DEPRECATION")
    private fun disableActivityTransitions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                overridePendingTransition(0, 0)
            }
        } catch (e: Exception) {
            // Safe fallback across Android versions
        }
    }

    private fun prewarmCustomTabs() {
        try {
            val packageName = CustomTabsClient.getPackageName(this, null)
            if (packageName != null) {
                CustomTabsClient.bindCustomTabsService(
                    this,
                    packageName,
                    object : CustomTabsServiceConnection() {
                        override fun onCustomTabsServiceConnected(
                            name: android.content.ComponentName,
                            client: CustomTabsClient
                        ) {
                            customTabsClient = client
                            client.warmup(0L)
                            customTabsSession = client.newSession(null)
                        }

                        override fun onServiceDisconnected(name: android.content.ComponentName?) {
                            customTabsClient = null
                            customTabsSession = null
                        }
                    }
                )
            }
        } catch (e: Exception) {
            // Silently handle if Custom Tabs service binding is unavailable on legacy browser
        }
    }
}
