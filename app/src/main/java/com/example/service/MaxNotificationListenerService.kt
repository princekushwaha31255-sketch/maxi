package com.example.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.ActiveNotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MaxNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isConnected.value = true
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
            _isConnected.value = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshNotifications()
    }

    fun refreshNotifications(): List<ActiveNotificationItem> {
        return try {
            val active = activeNotifications ?: emptyArray()
            val pm = packageManager
            val parsed = active.mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null

                // Privacy filter: redact OTPs or sensitive codes automatically
                val sanitizedText = sanitizeSensitiveText(text)
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    sbn.packageName
                }

                ActiveNotificationItem(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = sanitizedText,
                    postTime = sbn.postTime
                )
            }.sortedByDescending { it.postTime }.take(15)

            _recentNotifications.value = parsed
            parsed
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sanitizeSensitiveText(raw: String): String {
        val lower = raw.lowercase()
        if (lower.contains("otp") || lower.contains("verification code") ||
            lower.contains("password") || lower.contains("passcode") || lower.contains("pin")
        ) {
            return "[Sensitive OTP / Verification Code Redacted by MAX AI Privacy Shield]"
        }
        return raw
    }

    companion object {
        @Volatile
        var instance: MaxNotificationListenerService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<ActiveNotificationItem>>(emptyList())
        val recentNotifications: StateFlow<List<ActiveNotificationItem>> = _recentNotifications.asStateFlow()

        fun isNotificationAccessEnabled(context: Context): Boolean {
            if (instance != null) return true
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(context.packageName)
        }
    }
}
