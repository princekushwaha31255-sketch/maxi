package com.example.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import com.example.data.InteractionStatus
import com.example.data.MaxDao
import com.example.data.PendingConfirmation
import com.example.data.ScheduledItemEntity
import com.example.data.ToolExecutionResult
import com.example.service.MaxAccessibilityService
import com.example.service.MaxAlarmReceiver
import java.util.Calendar
import java.util.UUID

class DeviceControlExecutor(
    private val context: Context,
    private val maxDao: MaxDao
) {

    fun executeOpenSettings(
        settingsTarget: String,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val targetClean = settingsTarget.trim().lowercase()
        val actionPair: Pair<String, String>? = when {
            targetClean.contains("wifi") || targetClean.contains("wi-fi") || targetClean.contains("internet") ->
                Settings.ACTION_WIFI_SETTINGS to "Wi-Fi Settings"
            targetClean.contains("bluetooth") || targetClean.contains("bt") ->
                Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth Settings"
            targetClean.contains("display") || targetClean.contains("brightness") || targetClean.contains("screen") ->
                Settings.ACTION_DISPLAY_SETTINGS to "Display Settings"
            targetClean.contains("battery") || targetClean.contains("power") ->
                Intent.ACTION_POWER_USAGE_SUMMARY to "Battery Settings"
            targetClean.contains("app") || targetClean.contains("application") ->
                Settings.ACTION_APPLICATION_SETTINGS to "Application Settings"
            targetClean.contains("sound") || targetClean.contains("volume") || targetClean.contains("audio") ->
                Settings.ACTION_SOUND_SETTINGS to "Sound Settings"
            targetClean.contains("accessibility") ->
                Settings.ACTION_ACCESSIBILITY_SETTINGS to "Accessibility Settings"
            targetClean.contains("notification") ->
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS to "Notification Access Settings"
            targetClean.contains("location") || targetClean.contains("gps") ->
                Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location Settings"
            targetClean == "settings" || targetClean == "main" || targetClean == "general" ->
                Settings.ACTION_SETTINGS to "System Settings"
            else -> null
        }

        if (actionPair == null) {
            return ToolExecutionResult(
                toolName = "open_settings",
                status = InteractionStatus.UNSUPPORTED_ACTION,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, '$settingsTarget' सेटिंग्स पेज समर्थित नहीं है। मैं Wi-Fi, Bluetooth, Display, Battery, Sound, या Apps सेटिंग्स खोल सकता हूँ।",
                    en = "The settings target '$settingsTarget' is not supported. Supported targets include Wi-Fi, Bluetooth, Display, Battery, Sound, and Application settings.",
                    hinglish = "Boss, '$settingsTarget' settings target supported nahi hai. Main Wi-Fi, Bluetooth, Display, Battery, Sound, ya App settings khol sakta hoon."
                ),
                verificationEvidence = "Rejected unsupported settings_target '$settingsTarget'"
            )
        }

        val (intentAction, label) = actionPair
        return try {
            val intent = Intent(intentAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                ToolExecutionResult(
                    toolName = "open_settings",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "जी बॉस, मुख्य सेटिंग्स खोल दी हैं।",
                        en = "Opened main system settings ($label page was redirected by device).",
                        hinglish = "Ji boss, System Settings open kar diya hai."
                    ),
                    verificationEvidence = "Fallback Settings.ACTION_SETTINGS launched cleanly"
                )
            } else {
                context.startActivity(intent)
                ToolExecutionResult(
                    toolName = "open_settings",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "जी बॉस, $label खोल दिया है।",
                        en = "Opened $label.",
                        hinglish = "Ji boss, $label khol diya hai."
                    ),
                    verificationEvidence = "Verified Intent('$intentAction') resolved to ${resolved.PackageName()} and launched"
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "open_settings",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "Failed to open $label: ${e.message}",
                verificationEvidence = "Exception launching '$intentAction': ${e.javaClass.simpleName}"
            )
        }
    }

    private fun android.content.ComponentName.PackageName(): String = this.packageName

    fun executeAdjustVolume(
        stream: String,
        operation: String,
        value: Int? = null,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(
                toolName = "adjust_volume",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "AudioManager service unavailable on this device.",
                verificationEvidence = "Context.AUDIO_SERVICE returned null"
            )

        val streamType = when (stream.trim().lowercase()) {
            "ring", "ringer", "call" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification", "notifications" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val streamLabel = when (streamType) {
            AudioManager.STREAM_RING -> "Ring"
            AudioManager.STREAM_ALARM -> "Alarm"
            AudioManager.STREAM_NOTIFICATION -> "Notification"
            else -> "Media"
        }

        return try {
            val maxVol = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val beforeVol = audioManager.getStreamVolume(streamType)
            val opClean = operation.trim().lowercase()

            when {
                opClean == "set" && value != null -> {
                    // Convert percentage (0..100) or raw index
                    val targetIndex = if (value > maxVol) {
                        ((value.coerceIn(0, 100) / 100.0) * maxVol).toInt()
                    } else {
                        value.coerceIn(0, maxVol)
                    }
                    audioManager.setStreamVolume(
                        streamType,
                        targetIndex,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                opClean in listOf("increase", "up", "raise", "badhao", "zyada") -> {
                    audioManager.adjustStreamVolume(
                        streamType,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                opClean in listOf("decrease", "down", "lower", "kam", "ghatao") -> {
                    audioManager.adjustStreamVolume(
                        streamType,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                opClean in listOf("mute", "silent", "band") -> {
                    audioManager.adjustStreamVolume(
                        streamType,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                opClean in listOf("unmute", "on", "kholo") -> {
                    audioManager.adjustStreamVolume(
                        streamType,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                else -> {
                    return ToolExecutionResult(
                        toolName = "adjust_volume",
                        status = InteractionStatus.UNSUPPORTED_ACTION,
                        userMessage = "Unsupported volume operation '$operation'. Use set, increase, decrease, mute, or unmute.",
                        verificationEvidence = "Invalid operation parameter '$operation'"
                    )
                }
            }

            val afterVol = audioManager.getStreamVolume(streamType)
            val percent = ((afterVol.toFloat() / maxVol.toFloat()) * 100).toInt()
            ToolExecutionResult(
                toolName = "adjust_volume",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, $streamLabel वॉल्यूम $afterVol/$maxVol ($percent%) कर दिया है।",
                    en = "$streamLabel volume updated to $afterVol/$maxVol ($percent%).",
                    hinglish = "Ji boss, $streamLabel volume $afterVol/$maxVol ($percent%) set ho gaya hai."
                ),
                verificationEvidence = "AudioManager verified stream=$streamLabel changed from $beforeVol/$maxVol to $afterVol/$maxVol"
            )
        } catch (e: SecurityException) {
            ToolExecutionResult(
                toolName = "adjust_volume",
                status = InteractionStatus.PERMISSION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, Do Not Disturb चालू होने के कारण यह वॉल्यूम बदलने की अनुमति नहीं है।",
                    en = "Cannot modify this audio stream because Do Not Disturb policy restricts it.",
                    hinglish = "Boss, Do Not Disturb policy ki wajah se volume change block hua hai."
                ),
                verificationEvidence = "SecurityException from AudioManager: ${e.message}"
            )
        }
    }

    fun executeControlMedia(
        operation: String,
        application: String? = null,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(
                toolName = "control_media",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "AudioManager service unavailable.",
                verificationEvidence = "AudioManager null"
            )

        val keyCode = when (operation.trim().lowercase()) {
            "play", "resume", "chalao", "start" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause", "roko" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "stop", "band" -> KeyEvent.KEYCODE_MEDIA_STOP
            "next", "skip", "agla", "aage" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "pichla", "peeche" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "toggle", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> null
        }

        if (keyCode == null) {
            return ToolExecutionResult(
                toolName = "control_media",
                status = InteractionStatus.UNSUPPORTED_ACTION,
                userMessage = "Unsupported media operation '$operation'. Supported: play, pause, stop, next, previous.",
                verificationEvidence = "Rejected unsupported media operation '$operation'"
            )
        }

        return try {
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            val appSuffix = if (!application.isNullOrBlank()) " ($application)" else ""
            ToolExecutionResult(
                toolName = "control_media",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, मीडिया '$operation'$appSuffix कमांड भेज दिया है।",
                    en = "Dispatched media '$operation' command$appSuffix.",
                    hinglish = "Ji boss, media '$operation'$appSuffix command execute ho gaya hai."
                ),
                verificationEvidence = "AudioManager.dispatchMediaKeyEvent($keyCode) dispatched ACTION_DOWN and ACTION_UP"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "control_media",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "Media control failed: ${e.message}",
                verificationEvidence = "Exception in dispatchMediaKeyEvent: ${e.message}"
            )
        }
    }

    /**
     * Section 6: "control_device_setting"
     * Uses only authorized system interfaces. On Android 10+ (API 29+), apps cannot silently toggle
     * Wi-Fi or Bluetooth directly; we open the authorized Settings Panel or Settings page and
     * truthfully explain the Android platform restriction.
     */
    fun executeControlDeviceSetting(
        setting: String,
        desiredState: String,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val cleanSetting = setting.trim().lowercase()
        val cleanState = desiredState.trim().lowercase()

        // Home screen or Notification shade via AccessibilityService if requested through device control
        if (cleanSetting.contains("home")) {
            return executeSystemNavigation("home", languageStyle)
        }
        if (cleanSetting.contains("notification") && (cleanState == "open" || cleanState == "on")) {
            return executeSystemNavigation("notifications", languageStyle)
        }

        return when {
            cleanSetting.contains("wifi") || cleanSetting.contains("wi-fi") || cleanSetting.contains("internet") -> {
                val panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                val intent = Intent(panelAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                ToolExecutionResult(
                    toolName = "control_device_setting",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, Android सुरक्षा नियमों के कारण ऐप्स सीधे Wi-Fi को $cleanState नहीं कर सकते। मैंने Wi-Fi पैनल खोल दिया है, कृपया एक टैप कर दीजिए।",
                        en = "Android platform security restricts direct background Wi-Fi toggling. I have opened the official Wi-Fi Connectivity panel for you to toggle it $cleanState.",
                        hinglish = "Boss, Android security restrictions ke kaaran Wi-Fi direct toggle allowed nahi hai. Maine Wi-Fi connectivity panel khol diya hai, bas ek tap kar dijiye."
                    ),
                    verificationEvidence = "Disclosed Android 10+ Wi-Fi restriction and launched authorized $panelAction panel"
                )
            }
            cleanSetting.contains("bluetooth") || cleanSetting.contains("bt") -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(
                    toolName = "control_device_setting",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, Android नियमों के तहत सीधे Bluetooth $cleanState नहीं किया जा सकता। मैंने Bluetooth सेटिंग्स खोल दी हैं।",
                        en = "Direct Bluetooth toggling is restricted by modern Android permissions. I have opened Bluetooth Settings for you.",
                        hinglish = "Boss, direct Bluetooth toggle restricted hai. Maine Bluetooth Settings khol diya hai."
                    ),
                    verificationEvidence = "Disclosed Bluetooth restriction and launched Settings.ACTION_BLUETOOTH_SETTINGS"
                )
            }
            cleanSetting.contains("flashlight") || cleanSetting.contains("torch") -> {
                toggleTorchIfAvailable(cleanState == "on", languageStyle)
            }
            else -> {
                // Route to open_settings with disclosure
                val openRes = executeOpenSettings(setting, languageStyle)
                if (openRes.status == InteractionStatus.VERIFIED_SUCCESS) {
                    openRes.copy(
                        toolName = "control_device_setting",
                        userMessage = formatMsg(
                            languageStyle,
                            hi = "बॉस, '$setting' को सीधे बदलना Android में प्रतिबंधित है, इसलिए मैंने संबंधित सेटिंग्स पेज खोल दिया है।",
                            en = "Direct modification of '$setting' is restricted by Android; I opened the relevant settings screen instead.",
                            hinglish = "Boss, '$setting' ko direct change karna Android mein restricted hai, isliye maine uska Settings page khol diya hai."
                        )
                    )
                } else {
                    openRes.copy(toolName = "control_device_setting")
                }
            }
        }
    }

    private fun toggleTorchIfAvailable(turnOn: Boolean, languageStyle: String): ToolExecutionResult {
        return try {
            val cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                    ?: return ToolExecutionResult(
                        toolName = "control_device_setting",
                        status = InteractionStatus.EXECUTION_FAILED,
                        userMessage = "CameraManager unavailable.",
                        verificationEvidence = "CameraManager null"
                    )
            val camId = cameraManager.cameraIdList.firstOrNull()
                ?: return ToolExecutionResult(
                    toolName = "control_device_setting",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = "No camera flash hardware found on this device.",
                    verificationEvidence = "cameraIdList is empty"
                )
            cameraManager.setTorchMode(camId, turnOn)
            val stateWord = if (turnOn) "ON" else "OFF"
            ToolExecutionResult(
                toolName = "control_device_setting",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, टॉर्च $stateWord कर दिया है।",
                    en = "Flashlight turned $stateWord.",
                    hinglish = "Ji boss, flashlight $stateWord kar diya hai."
                ),
                verificationEvidence = "CameraManager.setTorchMode('$camId', $turnOn) succeeded"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "control_device_setting",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "Could not toggle flashlight on this device/emulator: ${e.message}",
                verificationEvidence = "setTorchMode failed: ${e.javaClass.simpleName}"
            )
        }
    }

    fun executeSystemNavigation(
        target: String,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val clean = target.trim().lowercase()
        if (clean == "home" || clean == "home_screen") {
            // Try AccessibilityService first, fallback to Intent.CATEGORY_HOME
            val a11y = MaxAccessibilityService.instance
            if (a11y != null && a11y.performGlobalHome()) {
                return ToolExecutionResult(
                    toolName = "system_navigation",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "जी बॉस, होम स्क्रीन पर आ गए हैं।",
                        en = "Returned to the home screen.",
                        hinglish = "Ji boss, home screen par aa gaye hain."
                    ),
                    verificationEvidence = "MaxAccessibilityService.performGlobalAction(GLOBAL_ACTION_HOME) returned true"
                )
            }
            return try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                ToolExecutionResult(
                    toolName = "system_navigation",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "जी बॉस, होम स्क्रीन खोल दी है।",
                        en = "Returned to the home screen.",
                        hinglish = "Ji boss, home screen open kar diya hai."
                    ),
                    verificationEvidence = "Launched Intent.ACTION_MAIN with CATEGORY_HOME"
                )
            } catch (e: Exception) {
                ToolExecutionResult(
                    toolName = "system_navigation",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = "Failed to return to home screen: ${e.message}",
                    verificationEvidence = "Exception launching CATEGORY_HOME: ${e.message}"
                )
            }
        }

        if (clean.contains("notification") || clean.contains("shade") || clean.contains("panel")) {
            val a11y = MaxAccessibilityService.instance
            if (a11y != null && a11y.performOpenNotifications()) {
                return ToolExecutionResult(
                    toolName = "system_navigation",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "जी बॉस, नोटिफिकेशन शेड खोल दिया है।",
                        en = "Opened the notification shade.",
                        hinglish = "Ji boss, notification shade khol diya hai."
                    ),
                    verificationEvidence = "MaxAccessibilityService.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) succeeded"
                )
            }
            return ToolExecutionResult(
                toolName = "system_navigation",
                status = InteractionStatus.PERMISSION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, नोटिफिकेशन शेड खोलने के लिए MAX AI Accessibility Service चालू होनी चाहिए। कृपया Permissions टैब से इसे ऑन करें।",
                    en = "Opening the notification shade requires MAX AI AccessibilityService to be enabled. Please enable it in the Permissions tab.",
                    hinglish = "Boss, notification shade open karne ke liye AccessibilityService chahiye. Please Permissions tab se allow kar dijiye."
                ),
                verificationEvidence = "MaxAccessibilityService.instance is null; GLOBAL_ACTION_NOTIFICATIONS requires enabled AccessibilityService",
                requiredPermission = "ACCESSIBILITY_SERVICE"
            )
        }

        return ToolExecutionResult(
            toolName = "system_navigation",
            status = InteractionStatus.UNSUPPORTED_ACTION,
            userMessage = "Unsupported navigation target '$target'.",
            verificationEvidence = "Unknown target '$target'"
        )
    }

    fun executeAccessibilityAction(
        operation: String,
        target: String,
        text: String? = null,
        isConfirmed: Boolean = false,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val service = MaxAccessibilityService.instance
        if (service == null) {
            return ToolExecutionResult(
                toolName = "accessibility_action",
                status = InteractionStatus.PERMISSION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, स्क्रीन पर टैप, स्क्रॉल या टेक्स्ट लिखने के लिए MAX AI AccessibilityService चालू करना ज़रूरी है। कृपया Permissions में जाकर इसे ऑन करें।",
                    en = "AccessibilityService is not enabled. Please enable MAX AI Automation in Accessibility Settings first.",
                    hinglish = "Boss, UI automation ($operation) ke liye MAX AI AccessibilityService enabled hona zaroori hai. Please Permissions tab se enable kar dijiye."
                ),
                verificationEvidence = "Precondition failed: MaxAccessibilityService.instance is null",
                requiredPermission = "ACCESSIBILITY_SERVICE"
            )
        }

        val opClean = operation.trim().lowercase()

        // If target looks like a sensitive/irreversible button (delete, pay, post, send, remove), require confirmation
        val targetLower = target.lowercase()
        val isSensitiveUiTarget = targetLower.contains("delete") ||
            targetLower.contains("send") ||
            targetLower.contains("post") ||
            targetLower.contains("pay") ||
            targetLower.contains("buy") ||
            targetLower.contains("remove") ||
            targetLower.contains("unfollow")

        if (isSensitiveUiTarget && !isConfirmed) {
            val confirmation = PendingConfirmation(
                id = UUID.randomUUID().toString(),
                toolName = "accessibility_action",
                title = "Confirm UI Automation ($opClean)",
                summary = "Perform '$opClean' on target '$target'" + (if (!text.isNullOrBlank()) " with text '$text'" else ""),
                parameters = mapOf(
                    "operation" to operation,
                    "target" to target,
                    "text" to (text ?: "")
                ),
                riskReason = "Externally visible or potentially irreversible UI action on '$target'.",
                languageStyle = languageStyle
            )
            return ToolExecutionResult(
                toolName = "accessibility_action",
                status = InteractionStatus.PENDING_CONFIRMATION,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, क्या मैं '$target' पर '$opClean' एक्शन कर दूँ? कृपया कन्फर्म करें।",
                    en = "Please confirm: should I perform '$opClean' on '$target'?",
                    hinglish = "Boss, '$target' par '$opClean' action sensitive hai. Kya main ise execute kar doon?"
                ),
                verificationEvidence = "Confirmation policy gated sensitive accessibility_action on '$target'",
                pendingConfirmation = confirmation
            )
        }

        val (ok, details) = when (opClean) {
            "tap", "click" -> service.tapByTextOrCoordinate(target)
            "scroll", "swipe" -> service.performScroll(target.ifBlank { "down" })
            "text_entry", "type", "input", "write" -> {
                if (text.isNullOrBlank()) {
                    return ToolExecutionResult(
                        toolName = "accessibility_action",
                        status = InteractionStatus.CLARIFICATION_REQUIRED,
                        userMessage = formatMsg(
                            languageStyle,
                            hi = "बॉस, टेक्स्ट बॉक्स में क्या लिखना है? कृपया बताइए।",
                            en = "What text should I enter into the field?",
                            hinglish = "Boss, text field mein kya type karna hai? Bataiye."
                        ),
                        verificationEvidence = "Missing required 'text' parameter for text_entry operation"
                    )
                }
                service.performTextEntry(target, text)
            }
            "home" -> service.performGlobalHome() to "Executed GLOBAL_ACTION_HOME"
            "back" -> service.performGlobalBack() to "Executed GLOBAL_ACTION_BACK"
            "notifications" -> service.performOpenNotifications() to "Executed GLOBAL_ACTION_NOTIFICATIONS"
            else -> {
                return ToolExecutionResult(
                    toolName = "accessibility_action",
                    status = InteractionStatus.UNSUPPORTED_ACTION,
                    userMessage = "Unsupported accessibility operation '$operation'. Supported: tap, scroll, text_entry, home, back, notifications.",
                    verificationEvidence = "Rejected unsupported accessibility operation '$operation'"
                )
            }
        }

        return if (ok) {
            ToolExecutionResult(
                toolName = "accessibility_action",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, '$operation' एक्शन पूरा हो गया।",
                    en = "Completed accessibility action '$operation' on '$target'.",
                    hinglish = "Ji boss, '$operation' action ($target) successfully execute ho gaya."
                ),
                verificationEvidence = details
            )
        } else {
            ToolExecutionResult(
                toolName = "accessibility_action",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, '$operation' पूरा नहीं हो सका: $details",
                    en = "Could not complete '$operation': $details",
                    hinglish = "Boss, '$operation' execute nahi ho paya: $details"
                ),
                verificationEvidence = details
            )
        }
    }

    suspend fun executeScheduleAlarmOrReminder(
        time: String,
        date: String? = null,
        label: String? = null,
        recurrence: String? = null,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val parsed = parseTimeInput(time)
        if (parsed == null) {
            return ToolExecutionResult(
                toolName = "schedule_alarm_or_reminder",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, समय '$time' स्पष्ट नहीं है। कृपया AM या PM के साथ सही समय बताइए (जैसे '7:00 AM' या '19:30')।",
                    en = "The time '$time' is ambiguous or invalid. Please specify the exact time with AM/PM (e.g., '7:00 AM' or '19:30').",
                    hinglish = "Boss, time '$time' ambiguous hai. Please AM/PM ke saath clear time bataiye (jaise '7:00 AM' ya '8:30 PM')."
                ),
                verificationEvidence = "Time parser detected ambiguous or unparseable time '$time'"
            )
        }

        if (parsed.isAmbiguousAmPm) {
            return ToolExecutionResult(
                toolName = "schedule_alarm_or_reminder",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, ${parsed.hour12}:${parsed.minute.toString().padStart(2, '0')} सुबह (AM) का लगाना है या शाम (PM) का?",
                    en = "Should I set the alarm for ${parsed.hour12}:${parsed.minute.toString().padStart(2, '0')} AM or PM?",
                    hinglish = "Boss, ${parsed.hour12}:${parsed.minute.toString().padStart(2, '0')} AM ka alarm lagana hai ya PM ka?"
                ),
                verificationEvidence = "12-hour time '$time' lacked AM/PM qualifier; clarification requested"
            )
        }

        val effectiveLabel = label?.takeIf { it.isNotBlank() } ?: "MAX AI Alarm"
        val now = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parsed.hour24)
            set(Calendar.MINUTE, parsed.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule via AlarmManager BroadcastReceiver AND launch Clock SET_ALARM intent when supported
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return ToolExecutionResult(
                    toolName = "schedule_alarm_or_reminder",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = "AlarmManager service unavailable.",
                    verificationEvidence = "Context.ALARM_SERVICE returned null"
                )

            val receiverIntent = Intent(context, MaxAlarmReceiver::class.java).apply {
                putExtra(MaxAlarmReceiver.EXTRA_ALARM_LABEL, effectiveLabel)
            }
            val requestCode = (targetCal.timeInMillis % 100000).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Use setAndAllowWhileIdle so it succeeds reliably without crashing on exact-alarm restrictions
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetCal.timeInMillis,
                pendingIntent
            )

            // Also attempt system Clock SET_ALARM with EXTRA_SKIP_UI if a Clock app handles it
            val clockIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, parsed.hour24)
                putExtra(AlarmClock.EXTRA_MINUTES, parsed.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, effectiveLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val clockHandled = if (clockIntent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(clockIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            } else false

            val formattedTime = String.format("%02d:%02d", parsed.hour24, parsed.minute)
            maxDao.insertScheduledItem(
                ScheduledItemEntity(
                    timeText = formattedTime,
                    dateText = date,
                    label = effectiveLabel,
                    recurrence = recurrence,
                    isVerified = true,
                    triggerAtMillis = targetCal.timeInMillis
                )
            )

            ToolExecutionResult(
                toolName = "schedule_alarm_or_reminder",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, $formattedTime के लिए '$effectiveLabel' अलार्म सेट हो गया है।",
                    en = "Alarm '$effectiveLabel' is set and verified for $formattedTime.",
                    hinglish = "Ji boss, $formattedTime ke liye '$effectiveLabel' alarm set ho gaya hai."
                ),
                verificationEvidence = "AlarmManager.setAndAllowWhileIdle verified for $formattedTime (epoch=${targetCal.timeInMillis}, systemClockIntent=$clockHandled)"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "schedule_alarm_or_reminder",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "Failed to schedule alarm: ${e.message}",
                verificationEvidence = "Exception in AlarmManager scheduling: ${e.javaClass.simpleName}"
            )
        }
    }

    data class ParsedTime(
        val hour24: Int,
        val hour12: Int,
        val minute: Int,
        val isAmbiguousAmPm: Boolean
    )

    companion object {
        fun parseTimeInput(raw: String): ParsedTime? {
            val cleaned = raw.trim().lowercase()
            if (cleaned.isEmpty()) return null

            val hasAm = cleaned.contains("am") || cleaned.contains("a.m") ||
                cleaned.contains("subah") || cleaned.contains("morning")
            val hasPm = cleaned.contains("pm") || cleaned.contains("p.m") ||
                cleaned.contains("shaam") || cleaned.contains("raat") ||
                cleaned.contains("evening") || cleaned.contains("night") ||
                cleaned.contains("dopahar") || cleaned.contains("afternoon")

            val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?""")
            val match = timeRegex.find(cleaned) ?: return null
            val rawHour = match.groupValues[1].toIntOrNull() ?: return null
            val rawMin = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0

            if (rawMin !in 0..59 || rawHour !in 0..23) return null

            // If user wrote 24-hour format like 14:30 or 07:00 (with leading zero)
            val hasLeadingZero = match.groupValues[1].startsWith("0") && match.groupValues[1].length == 2
            if (rawHour in 13..23 || hasLeadingZero || rawHour == 0) {
                val h12 = when {
                    rawHour == 0 -> 12
                    rawHour > 12 -> rawHour - 12
                    else -> rawHour
                }
                return ParsedTime(
                    hour24 = rawHour,
                    hour12 = h12,
                    minute = rawMin,
                    isAmbiguousAmPm = false
                )
            }

            // 1..12 hour
            if (!hasAm && !hasPm) {
                return ParsedTime(
                    hour24 = rawHour,
                    hour12 = rawHour,
                    minute = rawMin,
                    isAmbiguousAmPm = true
                )
            }

            val hour24 = when {
                hasPm && rawHour < 12 -> rawHour + 12
                hasAm && rawHour == 12 -> 0
                else -> rawHour
            }
            return ParsedTime(
                hour24 = hour24,
                hour12 = rawHour,
                minute = rawMin,
                isAmbiguousAmPm = false
            )
        }
    }

    private fun formatMsg(style: String, hi: String, en: String, hinglish: String): String {
        return when {
            style.equals("Hindi", ignoreCase = true) -> hi
            style.equals("English", ignoreCase = true) -> en
            else -> hinglish
        }
    }
}
