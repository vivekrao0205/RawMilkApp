package com.rawmilk.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        android.util.Log.d("RawMilkFCMDiag", "[DATA ONLY ORDER PUSH RECEIVED]")
        FCMDiagnostics.log("[DATA ONLY ORDER PUSH RECEIVED]")
        val from = remoteMessage.from
        val data = remoteMessage.data
        FCMDiagnostics.log("onMessageReceived: Inbound push from='$from'. Data payload: $data")

        // Extract title and body from notification payload OR data payload dynamically
        val title = remoteMessage.notification?.title 
            ?: data["title"] 
            ?: "RAW MILK"

        val body = remoteMessage.notification?.body 
            ?: data["body"] 
            ?: data["message"] 
            ?: ""

        FCMDiagnostics.log("onMessageReceived: Resolved title='$title', body='$body'")

        // ALWAYS generate the native system notification if either title or body is present
        // This ensures test alerts or empty-body payloads are never silently dropped
        if (title.isNotEmpty() || body.isNotEmpty()) {
            sendNotification(title, body, data)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FCMDiagnostics.log("onNewToken: Refreshed FCM token received natively: $token")

        // Save token to SharedPreferences for offline/cold startup reading
        val sharedPrefs = getSharedPreferences("RawMilkPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
        FCMDiagnostics.log("Fresh token successfully cached in SharedPreferences.")

        // Send a dynamic local broadcast to MainActivity if active
        val intent = Intent("com.rawmilk.app.FCM_TOKEN_REFRESH").apply {
            putExtra("token", token)
            setPackage(packageName) // Restrict recipient to our application package only
        }
        sendBroadcast(intent)
        FCMDiagnostics.log("Dynamic token refresh broadcast sent locally.")
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        val channelId = "rawmilk_notifications"
        val channelName = "Order and Subscription Notifications"
        val groupKey = "com.rawmilk.app.NOTIFICATIONS"
        
        val targetUrl = data["url"] 
            ?: data["click_action"]?.let { action ->
                if (action.startsWith("http")) action else "https://www.rawmilk.in" + (if (action.startsWith("/")) "" else "/") + action
            } 
            ?: "https://www.rawmilk.in/"

        FCMDiagnostics.log("sendNotification: Building notification container with deep link: $targetUrl")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_url", targetUrl)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent, pendingIntentFlags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // Small icon resource reference - using our vector silhouette bottle icon
        val smallIconResId = resources.getIdentifier("ic_notification_icon", "drawable", packageName)
            .let { if (it != 0) it else applicationInfo.icon }

        // Configure builder with maximum priority and standard alerts to trigger a heads-up card
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(smallIconResId)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Beep, vibrate, and flash light
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Maximum priority for heads-up peeking
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Make content visible on lock screen for heads-up alerts
            .setGroup(groupKey)

        val notificationManager = NotificationManagerCompat.from(this)

        // Create high-importance channel natively to support background delivery O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channels for raw milk updates, delivery, and orders"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(defaultSoundUri, audioAttributes)
            }
            systemNotificationManager.createNotificationChannel(channel)
        }

        // Post native notification if system permissions are granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33
        ) {
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())
            FCMDiagnostics.log("Native notification successfully generated and posted to tray. ID: $notificationId")
        } else {
            FCMDiagnostics.log("ERROR: POST_NOTIFICATIONS permission not granted. Cannot show notification natively.")
        }
    }
}
