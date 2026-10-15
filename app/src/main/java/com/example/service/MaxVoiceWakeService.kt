package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground microphone service for continuous hands-free wake-word detection ("Hey MAX", "OK MAX", "MAX").
 * Strictly enforces:
 * - Runtime RECORD_AUDIO permission check before starting.
 * - Single reused SpeechRecognizer instance on the Main Looper to avoid service binder churn.
 * - Phonetic & Devanagari-safe wake-word matching with partial-result inactivity fallback.
 * - Instant synchronous microphone release when handing off to the active command listener or TTS.
 */
class MaxVoiceWakeService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var configuredWakePhrases: List<String> = DEFAULT_WAKE_PHRASES
    private var preferredLocaleTag: String = "en-IN"
    private var isDestroyed = false
    private var isLoopPausedForCommand = false
    private var lastTriggerTimestampMs = 0L
    private var latestPartialWakeMatch: WakeDetectionEvent? = null

    private val restartRunnable = Runnable {
        startListeningCycle(recreateRecognizer = false)
    }

    private val partialCommitRunnable = Runnable {
        val pending = latestPartialWakeMatch
        if (pending != null && !isDestroyed && !isLoopPausedForCommand && _isWakeServiceActive.value) {
            latestPartialWakeMatch = null
            dispatchWakeEvent(pending)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instanceRef = this
        isDestroyed = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instanceRef = this
        when (intent?.action) {
            ACTION_STOP_WAKE_SERVICE -> {
                stopWakeDetection()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_WAKE_LOOP -> {
                pauseListeningNow()
                return START_STICKY
            }
            ACTION_RESUME_WAKE_LOOP -> {
                resumeListeningNow()
                return START_STICKY
            }
        }

        val phrasesCsv = intent?.getStringExtra(EXTRA_WAKE_PHRASES) ?: "Hey MAX,OK MAX,MAX"
        configuredWakePhrases = parseConfiguredWakePhrases(phrasesCsv)
        preferredLocaleTag = intent?.getStringExtra(EXTRA_SPEECH_LOCALE)?.takeIf { it.isNotBlank() } ?: "en-IN"

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPerm || !SpeechRecognizer.isRecognitionAvailable(this)) {
            _isWakeServiceActive.value = false
            _isWakeLoopPaused.value = false
            _wakePartialTranscript.value = ""
            _wakeServiceStatusMessage.value =
                if (!hasAudioPerm) "Stopped: Microphone permission required"
                else "Stopped: SpeechRecognizer not available on this device"
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isWakeServiceActive.value = true
            isLoopPausedForCommand = false
            _isWakeLoopPaused.value = false
            _wakePartialTranscript.value = ""
            _wakeServiceStatusMessage.value =
                "Active & Listening for \"Hey MAX\", \"OK MAX\", or \"MAX\"..."
            startListeningCycle(recreateRecognizer = speechRecognizer == null)
        } catch (e: Exception) {
            _isWakeServiceActive.value = false
            _isWakeLoopPaused.value = false
            _wakePartialTranscript.value = ""
            _wakeServiceStatusMessage.value = "Failed to start foreground microphone service: ${e.message}"
            stopSelf()
        }

        return START_STICKY
    }

    internal fun pauseListeningNow() {
        if (!_isWakeServiceActive.value) return
        isLoopPausedForCommand = true
        _isWakeLoopPaused.value = true
        latestPartialWakeMatch = null
        _wakePartialTranscript.value = ""
        _wakeServiceStatusMessage.value =
            "Paused while MAX listens to command / speaks response..."
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(partialCommitRunnable)
        val stopWork = Runnable {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopWork.run()
        } else {
            mainHandler.post(stopWork)
        }
    }

    internal fun resumeListeningNow() {
        if (!_isWakeServiceActive.value || isDestroyed) return
        isLoopPausedForCommand = false
        _isWakeLoopPaused.value = false
        latestPartialWakeMatch = null
        _wakePartialTranscript.value = ""
        _wakeServiceStatusMessage.value =
            "Active & Listening for \"Hey MAX\", \"OK MAX\", or \"MAX\"..."
        scheduleRestart(250L, recreate = false)
    }

    private fun destroySpeechRecognizerInstance() {
        mainHandler.removeCallbacksAndMessages(null)
        val cleanup = Runnable {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            } finally {
                speechRecognizer = null
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run()
        } else {
            mainHandler.post(cleanup)
        }
    }

    private fun scheduleRestart(delayMs: Long, recreate: Boolean = false) {
        if (isDestroyed || isLoopPausedForCommand || !_isWakeServiceActive.value) return
        mainHandler.removeCallbacks(restartRunnable)
        if (recreate) {
            mainHandler.postDelayed({
                startListeningCycle(recreateRecognizer = true)
            }, delayMs)
        } else {
            mainHandler.postDelayed(restartRunnable, delayMs)
        }
    }

    private fun startListeningCycle(recreateRecognizer: Boolean) {
        if (isDestroyed || isLoopPausedForCommand || !_isWakeServiceActive.value) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.post {
            if (isDestroyed || isLoopPausedForCommand || !_isWakeServiceActive.value) return@post
            try {
                if (recreateRecognizer || speechRecognizer == null) {
                    try {
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                    } catch (_: Exception) {
                    }
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(wakeRecognitionListener)
                    }
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {
                    }
                }

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                }
                latestPartialWakeMatch = null
                speechRecognizer?.startListening(recognizerIntent)
            } catch (_: Exception) {
                if (!isDestroyed && !isLoopPausedForCommand && _isWakeServiceActive.value) {
                    scheduleRestart(1200L, recreate = true)
                }
            }
        }
    }

    private val wakeRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (!isLoopPausedForCommand && _isWakeServiceActive.value) {
                _wakeServiceStatusMessage.value =
                    "Active & Listening for \"Hey MAX\", \"OK MAX\", or \"MAX\"..."
            }
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            mainHandler.removeCallbacks(partialCommitRunnable)
            // If we already captured a valid wake phrase in partial results before timeout/error, trigger it!
            val pending = latestPartialWakeMatch
            latestPartialWakeMatch = null
            _wakePartialTranscript.value = ""
            if (pending != null && !isLoopPausedForCommand && _isWakeServiceActive.value) {
                dispatchWakeEvent(pending)
                return
            }

            if (isDestroyed || isLoopPausedForCommand || !_isWakeServiceActive.value) return

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    _wakeServiceStatusMessage.value = "Stopped: Microphone permission required"
                    stopWakeDetection()
                    stopSelf()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    scheduleRestart(1300L, recreate = true)
                }
                SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_AUDIO -> {
                    scheduleRestart(750L, recreate = true)
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    scheduleRestart(250L, recreate = false)
                }
                else -> {
                    scheduleRestart(900L, recreate = false)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            mainHandler.removeCallbacks(partialCommitRunnable)
            latestPartialWakeMatch = null
            _wakePartialTranscript.value = ""
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val triggered = handleTranscriptMatches(matches)
            if (!triggered && !isDestroyed && !isLoopPausedForCommand && _isWakeServiceActive.value) {
                scheduleRestart(220L, recreate = false)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (isLoopPausedForCommand || !_isWakeServiceActive.value) return
            val matches =
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val best = matches.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                _wakePartialTranscript.value = best
            }

            for (candidate in matches) {
                val matched = matchWakePhrase(candidate, configuredWakePhrases)
                if (matched != null) {
                    latestPartialWakeMatch = matched
                    mainHandler.removeCallbacks(partialCommitRunnable)
                    if (matched.trailingCommand.isEmpty()) {
                        // Standalone wake phrase ("Hey MAX", "OK MAX", "MAX") -> trigger after a short 380ms pause
                        // so if the user pauses after "Hey MAX", we immediately switch to command capture,
                        // or if they keep speaking ("Hey MAX call Shivam"), partialCommitRunnable resets until they finish.
                        mainHandler.postDelayed(partialCommitRunnable, 380L)
                    } else {
                        // Wake phrase + trailing command -> wait 950ms of silence in case onResults takes long
                        mainHandler.postDelayed(partialCommitRunnable, 950L)
                    }
                    break
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleTranscriptMatches(matches: List<String>): Boolean {
        if (isLoopPausedForCommand) return false
        for (candidate in matches) {
            val matched = matchWakePhrase(candidate, configuredWakePhrases)
            if (matched != null) {
                return dispatchWakeEvent(matched)
            }
        }
        return false
    }

    private fun dispatchWakeEvent(matched: WakeDetectionEvent): Boolean {
        if (isLoopPausedForCommand) return false
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestampMs < 1200L) return false
        lastTriggerTimestampMs = now

        // Synchronously pause the wake recognizer so the foreground command recognizer can acquire the mic cleanly
        isLoopPausedForCommand = true
        _isWakeLoopPaused.value = true
        latestPartialWakeMatch = null
        _wakePartialTranscript.value = ""
        _wakeServiceStatusMessage.value =
            "Wake phrase \"${matched.wakePhrase.uppercase()}\" detected! Listening for command..."
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        _lastWakeDetectedEvent.value = WakeDetectionEvent(
            wakePhrase = matched.wakePhrase,
            trailingCommand = matched.trailingCommand,
            timestamp = now
        )
        // Bring MainActivity to foreground when wake phrase detected while app is in background
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TRIGGERED_BY_WAKE_WORD, true)
                putExtra(EXTRA_WAKE_TRAILING_COMMAND, matched.trailingCommand)
            }
            startActivity(launchIntent)
        } catch (_: Exception) {
        }
        return true
    }

    private fun stopWakeDetection() {
        _isWakeServiceActive.value = false
        isLoopPausedForCommand = false
        _isWakeLoopPaused.value = false
        latestPartialWakeMatch = null
        _wakePartialTranscript.value = ""
        _wakeServiceStatusMessage.value = "Inactive (Manual mic activation ready)"
        destroySpeechRecognizerInstance()
    }

    override fun onDestroy() {
        isDestroyed = true
        if (instanceRef === this) {
            instanceRef = null
        }
        stopWakeDetection()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(): Notification {
        val channelId = "max_ai_wake_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MaxVoiceWakeService::class.java).apply {
            action = ACTION_STOP_WAKE_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.voice_service_notification_title))
            .setContentText("Say \"Hey MAX\", \"OK MAX\", or \"MAX\"")
            .setContentIntent(pendingOpen)
            .addAction(0, "Stop Wake Monitor", pendingStop)
            .setOngoing(true)
            .build()
    }

    data class WakeDetectionEvent(
        val wakePhrase: String,
        val trailingCommand: String,
        val timestamp: Long
    )

    companion object {
        const val ACTION_STOP_WAKE_SERVICE = "com.example.action.STOP_WAKE_SERVICE"
        const val ACTION_PAUSE_WAKE_LOOP = "com.example.action.PAUSE_WAKE_LOOP"
        const val ACTION_RESUME_WAKE_LOOP = "com.example.action.RESUME_WAKE_LOOP"
        const val EXTRA_WAKE_PHRASES = "extra_wake_phrases"
        const val EXTRA_SPEECH_LOCALE = "extra_speech_locale"
        const val EXTRA_TRIGGERED_BY_WAKE_WORD = "extra_triggered_by_wake_word"
        const val EXTRA_WAKE_TRAILING_COMMAND = "extra_wake_trailing_command"
        private const val NOTIFICATION_ID = 4041

        @Volatile
        private var instanceRef: MaxVoiceWakeService? = null

        val DEFAULT_WAKE_PHRASES = listOf(
            "hey max",
            "ok max",
            "okay max",
            "hello max",
            "hi max",
            "yo max",
            "o max",
            "max",
            "hey maxi",
            "ok maxi",
            "okay maxi",
            "hello maxi",
            "maxi",
            // Common Android STT phonetic transcriptions for Indian English / Hinglish
            "hey macs",
            "ok macs",
            "okay macs",
            "hey mex",
            "ok mex",
            "he max",
            "a max",
            "हे मैक्स",
            "ओके मैक्स",
            "हेलो मैक्स",
            "हाय मैक्स",
            "मैक्स",
            "मैक्सी"
        )

        private val _isWakeServiceActive = MutableStateFlow(false)
        val isWakeServiceActive: StateFlow<Boolean> = _isWakeServiceActive.asStateFlow()

        private val _isWakeLoopPaused = MutableStateFlow(false)
        val isWakeLoopPaused: StateFlow<Boolean> = _isWakeLoopPaused.asStateFlow()

        private val _wakePartialTranscript = MutableStateFlow("")
        val wakePartialTranscript: StateFlow<String> = _wakePartialTranscript.asStateFlow()

        private val _wakeServiceStatusMessage =
            MutableStateFlow("Inactive (Manual microphone activation ready)")
        val wakeServiceStatusMessage: StateFlow<String> = _wakeServiceStatusMessage.asStateFlow()

        private val _lastWakeDetectedEvent = MutableStateFlow<WakeDetectionEvent?>(null)
        val lastWakeDetectedEvent: StateFlow<WakeDetectionEvent?> = _lastWakeDetectedEvent.asStateFlow()

        fun consumeWakeEvent() {
            _lastWakeDetectedEvent.value = null
        }

        fun pauseWakeLoopIfActive(context: Context) {
            if (!_isWakeServiceActive.value) return
            // Synchronously release the microphone in-process before starting any foreground command listener
            instanceRef?.pauseListeningNow()
            try {
                val intent = Intent(context, MaxVoiceWakeService::class.java).apply {
                    action = ACTION_PAUSE_WAKE_LOOP
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun resumeWakeLoopIfActive(context: Context) {
            if (!_isWakeServiceActive.value) return
            instanceRef?.resumeListeningNow()
            try {
                val intent = Intent(context, MaxVoiceWakeService::class.java).apply {
                    action = ACTION_RESUME_WAKE_LOOP
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun parseConfiguredWakePhrases(phrasesCsv: String): List<String> {
            val custom = phrasesCsv.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            return (custom + DEFAULT_WAKE_PHRASES)
                .distinct()
                .sortedByDescending { it.length } // Check longer multi-word phrases ("hey max", "ok max") before "max"
        }

        /**
         * Matches wake phrases ("Hey MAX", "OK MAX", "Okay MAX", "MAX", "हे मैक्स", "ओके मैक्स", "मैक्स")
         * using whitespace/punctuation boundaries so both Latin and Devanagari scripts match reliably,
         * while preventing partial words like "maximum" or "climax" from false-triggering.
         */
        fun matchWakePhrase(
            rawTranscript: String,
            configuredPhrases: List<String> = DEFAULT_WAKE_PHRASES
        ): WakeDetectionEvent? {
            val cleaned = rawTranscript.trim()
            if (cleaned.isEmpty()) return null

            val normalized = cleaned.lowercase()
                .replace(Regex("""[,!.?।;:'"\-_]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            // Strip common conversational filler prefixes if spoken before the wake phrase
            val politeStripped = normalized.replace(
                Regex("""^(?:uh|um|umm|acha|accha|arre|arey|sun|suno|please|bhai|अरे|सुनो)\s+"""),
                ""
            )

            val orderedPhrases = (configuredPhrases + DEFAULT_WAKE_PHRASES)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedByDescending { it.length }

            for (candidateInput in listOf(politeStripped, normalized)) {
                for (phrase in orderedPhrases) {
                    val escaped = Regex.escape(phrase)
                    // Use explicit start/whitespace boundary (?<=^|\s) and (?=\s|$) so Devanagari matras work properly
                    val regex = Regex("""(?:^|\s)$escaped(?:\s+(.*)|$)""", RegexOption.IGNORE_CASE)
                    val matchResult = regex.find(candidateInput)
                    if (matchResult != null) {
                        // Only trigger if the wake phrase is near the start of the utterance (within first 2 words)
                        val prefixBeforeWake = candidateInput.substring(0, matchResult.range.first).trim()
                        val prefixWordCount = if (prefixBeforeWake.isEmpty()) 0 else prefixBeforeWake.split(" ").size
                        if (prefixWordCount > 2) continue

                        val trailing = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
                        val canonicalPhrase = when (phrase) {
                            "okay max", "ok macs", "okay macs", "ok mex", "ओके मैक्स" -> "ok max"
                            "hey macs", "hey mex", "he max", "a max", "hi max", "hello max", "yo max", "o max", "हे मैक्स", "हेलो मैक्स", "हाय मैक्स" -> "hey max"
                            "okay maxi", "ok maxi" -> "ok maxi"
                            "hello maxi", "hey maxi" -> "hey maxi"
                            "मैक्स" -> "max"
                            "मैक्सी" -> "maxi"
                            else -> phrase
                        }
                        return WakeDetectionEvent(
                            wakePhrase = canonicalPhrase,
                            trailingCommand = trailing,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
            return null
        }
    }
}

