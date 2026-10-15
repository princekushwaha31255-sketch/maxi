package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ai.MaxGeminiClient
import com.example.data.MaxUserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class VoiceInfoItem(
    val name: String,
    val localeDisplay: String,
    val isMaleLikely: Boolean,
    val isNeural: Boolean = false
)

enum class SpeechRecognitionStage {
    IDLE,
    INITIALIZING,
    READY_FOR_SPEECH,
    SPEECH_DETECTED,
    PROCESSING_AUDIO,
    ERROR
}

data class RecognizedCandidate(
    val transcript: String,
    val confidence: Float? = null
)

class MaxVoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeAudioTrack: AudioTrack? = null
    private var activeSpeechSynthesisJob: Job? = null
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingUtteranceIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var pendingLaunchRecognizerRunnable: Runnable? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognitionStage = MutableStateFlow(SpeechRecognitionStage.IDLE)
    val recognitionStage: StateFlow<SpeechRecognitionStage> = _recognitionStage.asStateFlow()

    private val _isSpeechRecognizerAvailable = MutableStateFlow(false)
    val isSpeechRecognizerAvailable: StateFlow<Boolean> = _isSpeechRecognizerAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeVoiceEmotion = MutableStateFlow(VoiceEmotion.EVERYDAY_CONVERSATION)
    val activeVoiceEmotion: StateFlow<VoiceEmotion> = _activeVoiceEmotion.asStateFlow()

    private val _activeVoiceEngineLabel = MutableStateFlow("Android Neural TTS")
    val activeVoiceEngineLabel: StateFlow<String> = _activeVoiceEngineLabel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _recognizedCandidates = MutableStateFlow<List<RecognizedCandidate>>(emptyList())
    val recognizedCandidates: StateFlow<List<RecognizedCandidate>> = _recognizedCandidates.asStateFlow()

    private val _rmsDbLevel = MutableStateFlow(0f)
    val rmsDbLevel: StateFlow<Float> = _rmsDbLevel.asStateFlow()

    private val _waveformHistory = MutableStateFlow(List(20) { 0.1f })
    val waveformHistory: StateFlow<List<Float>> = _waveformHistory.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceInfoItem>>(emptyList())
    val availableVoices: StateFlow<List<VoiceInfoItem>> = _availableVoices.asStateFlow()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _activeLocaleTag = MutableStateFlow("en-IN")
    val activeLocaleTag: StateFlow<String> = _activeLocaleTag.asStateFlow()

    private var onFinalTranscriptCallback: ((String) -> Unit)? = null
    private var onSpeechSessionEndedCallback: (() -> Unit)? = null
    private var onTtsCompletedCallback: (() -> Unit)? = null
    private var busyRetryPending = false

    init {
        checkSpeechRecognizerSupport()
        initTextToSpeech()
    }

    fun setOnSpeechSessionEndedListener(listener: (() -> Unit)?) {
        onSpeechSessionEndedCallback = listener
    }

    fun setOnTtsCompletedListener(listener: (() -> Unit)?) {
        onTtsCompletedCallback = listener
    }

    fun checkSpeechRecognizerSupport(): Boolean {
        val available = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
        _isSpeechRecognizerAvailable.value = available
        return available
    }

    private fun initTextToSpeech() {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                _ttsReady.value = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null) {
                            pendingUtteranceIds.remove(utteranceId)
                        }
                        if (pendingUtteranceIds.isEmpty()) {
                            _isSpeaking.value = false
                            mainHandler.post { onTtsCompletedCallback?.invoke() }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != null) {
                            pendingUtteranceIds.remove(utteranceId)
                        }
                        if (pendingUtteranceIds.isEmpty()) {
                            _isSpeaking.value = false
                            mainHandler.post { onTtsCompletedCallback?.invoke() }
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId != null) {
                            pendingUtteranceIds.remove(utteranceId)
                        }
                        if (pendingUtteranceIds.isEmpty() || interrupted) {
                            pendingUtteranceIds.clear()
                            _isSpeaking.value = false
                            mainHandler.post { onTtsCompletedCallback?.invoke() }
                        }
                    }
                })
                refreshAvailableVoices()
            } else {
                _ttsReady.value = false
            }
        }

        try {
            // Attempt to bind to Google Speech Services for neural WaveNet voices, falling back cleanly to system default
            tts = try {
                TextToSpeech(context.applicationContext, listener, "com.google.android.tts")
            } catch (_: Exception) {
                TextToSpeech(context.applicationContext, listener)
            }
        } catch (e: Exception) {
            _ttsReady.value = false
        }
    }

    fun refreshAvailableVoices() {
        val engine = tts ?: return
        try {
            val voices = engine.voices?.filter { voice ->
                val lang = voice.locale.language.lowercase(Locale.US)
                lang == "en" || lang == "hi"
            }.orEmpty()

            val mapped = voices.map { v ->
                val lowerName = v.name.lowercase(Locale.US)
                val isMale = lowerName.contains("male") && !lowerName.contains("female") ||
                    lowerName.contains("iom") || lowerName.contains("hie") ||
                    lowerName.contains("end") || lowerName.contains("ene") ||
                    lowerName.contains("ind-network") || lowerName.contains("en-in-x-end") ||
                    lowerName.contains("hi-in-x-hie") || lowerName.contains("hi-in-x-hid")
                val isNeural = lowerName.contains("network") ||
                    lowerName.contains("neural") ||
                    lowerName.contains("wavenet") ||
                    v.isNetworkConnectionRequired
                VoiceInfoItem(
                    name = v.name,
                    localeDisplay = "${v.locale.displayLanguage} (${v.locale.displayCountry})",
                    isMaleLikely = isMale,
                    isNeural = isNeural
                )
            }.sortedWith(
                compareByDescending<VoiceInfoItem> { it.isNeural }
                    .thenByDescending { it.isMaleLikely }
                    .thenBy { it.name }
            )

            _availableVoices.value = mapped
        } catch (_: Exception) {
        }
    }

    /**
     * Speaks a response with human-like emotional expressiveness, natural rhythm,
     * contextual prosody modulation, and code/JSON/URL sanitization.
     *
     * Uses Gemini 2.5 Flash Preview TTS (`gemini-2.5-flash-preview-tts`) when enabled and configured,
     * and seamlessly falls back to expressive multi-chunk Android Neural TextToSpeech.
     */
    fun speak(
        text: String,
        prefs: MaxUserPreferences,
        detectedLanguage: String = "Hinglish",
        emotion: VoiceEmotion = VoiceEmotion.EVERYDAY_CONVERSATION,
        forceSpeakEvenIfMuted: Boolean = false
    ) {
        val sanitizedText = MaxExpressiveSpeechFormatter.sanitizeForNaturalSpeech(text, detectedLanguage)
        if ((!prefs.autoSpeakResponses && !forceSpeakEvenIfMuted) || sanitizedText.isBlank()) {
            mainHandler.post { onTtsCompletedCallback?.invoke() }
            return
        }

        // Cancel any previous ongoing speech before starting the new expressive utterance
        stopSpeaking()

        val stylePreset = VoiceStylePreset.fromId(prefs.voiceStylePreset)
        val effectiveEmotion = if (emotion == VoiceEmotion.EVERYDAY_CONVERSATION && stylePreset.defaultEmotionOverride != null) {
            stylePreset.defaultEmotionOverride
        } else {
            emotion
        }
        _activeVoiceEmotion.value = effectiveEmotion

        val geminiKey = BuildConfig.GEMINI_API_KEY.trim()
        val canTryGeminiNeuralTts = prefs.useGeminiNeuralTts &&
            geminiKey.isNotBlank() &&
            geminiKey != "MY_GEMINI_API_KEY" &&
            !geminiKey.startsWith("sk-", ignoreCase = true) &&
            sanitizedText.length <= 1200 // Streamlined ultra-natural Gemini audio coverage

        if (canTryGeminiNeuralTts) {
            _isSpeaking.value = true
            _activeVoiceEngineLabel.value = "Gemini Neural Voice (${prefs.geminiVoiceName.ifBlank { stylePreset.defaultGeminiVoice }})"
            activeSpeechSynthesisJob = engineScope.launch {
                val chosenGeminiVoice = prefs.geminiVoiceName.ifBlank { stylePreset.defaultGeminiVoice }
                val inlineAudio = try {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        MaxGeminiClient.synthesizeExpressiveSpeech(
                            apiKey = geminiKey,
                            spokenText = sanitizedText,
                            styleDirective = effectiveEmotion.geminiStyleDirective,
                            voiceName = chosenGeminiVoice
                        )
                    }
                } catch (_: Exception) {
                    null
                }

                if (inlineAudio != null && inlineAudio.data.isNotBlank()) {
                    val played = playGeminiInlinePcmAudio(inlineAudio.data, inlineAudio.mimeType)
                    if (played) {
                        _isSpeaking.value = false
                        mainHandler.post { onTtsCompletedCallback?.invoke() }
                        return@launch
                    }
                }
                // Fallback transparently to Expressive Android Neural TTS if Gemini TTS was unavailable, timed out, or rate-limited
                speakWithExpressiveAndroidTts(sanitizedText, prefs, detectedLanguage, effectiveEmotion, stylePreset)
            }
        } else {
            speakWithExpressiveAndroidTts(sanitizedText, prefs, detectedLanguage, effectiveEmotion, stylePreset)
        }
    }

    private suspend fun playGeminiInlinePcmAudio(base64Audio: String, mimeType: String): Boolean {
        return try {
            val rawBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            if (rawBytes.size < 64) return false

            val rateMatch = Regex("""rate=(\d+)""").find(mimeType)
            val sampleRate = rateMatch?.groupValues?.get(1)?.toIntOrNull() ?: 24000

            // Strip 44-byte WAV header if returned as audio/wav
            val pcmOffset = if (rawBytes.size > 44 &&
                rawBytes[0] == 'R'.code.toByte() &&
                rawBytes[1] == 'I'.code.toByte() &&
                rawBytes[2] == 'F'.code.toByte() &&
                rawBytes[3] == 'F'.code.toByte()
            ) 44 else 0

            val pcmLength = rawBytes.size - pcmOffset
            if (pcmLength <= 0) return false

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, pcmLength))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeAudioTrack = track
            track.write(rawBytes, pcmOffset, pcmLength)
            track.play()

            // Calculate playback duration in ms (16-bit mono = 2 bytes per sample)
            val durationMs = ((pcmLength / 2L) * 1000L / sampleRate).coerceIn(250L, 45_000L)
            var elapsed = 0L
            while (elapsed < durationMs && _isSpeaking.value && activeAudioTrack === track) {
                kotlinx.coroutines.delay(60L)
                elapsed += 60L
            }
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
            if (activeAudioTrack === track) {
                activeAudioTrack = null
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun speakWithExpressiveAndroidTts(
        sanitizedText: String,
        prefs: MaxUserPreferences,
        detectedLanguage: String,
        emotion: VoiceEmotion,
        stylePreset: VoiceStylePreset
    ) {
        val engine = tts
        if (engine == null || !_ttsReady.value) {
            _isSpeaking.value = false
            mainHandler.post { onTtsCompletedCallback?.invoke() }
            return
        }

        val hasDevanagari = sanitizedText.any { it in '\u0900'..'\u097F' }
        val isHindiOrHinglish = hasDevanagari ||
            detectedLanguage.contains("Hindi", ignoreCase = true) ||
            detectedLanguage.contains("Hinglish", ignoreCase = true)

        val processedText = if (isHindiOrHinglish) {
            MaxExpressiveSpeechFormatter.adaptForIndianNeuralVoice(sanitizedText)
        } else {
            sanitizedText
        }

        val chunks = MaxExpressiveSpeechFormatter.buildExpressiveSpeechChunks(
            rawText = processedText,
            languageStyle = detectedLanguage,
            emotion = emotion,
            stylePreset = stylePreset,
            basePitch = prefs.speechPitch,
            baseRate = prefs.speechRate,
            preferMaleVoice = prefs.preferMaleVoice
        )

        if (chunks.isEmpty()) {
            _isSpeaking.value = false
            mainHandler.post { onTtsCompletedCallback?.invoke() }
            return
        }

        mainHandler.post {
            try {
                _activeVoiceEngineLabel.value = "Expressive Neural TTS (${emotion.displayLabel})"
                val finalHasDevanagari = processedText.any { it in '\u0900'..'\u097F' }
                val targetLocale = when {
                    finalHasDevanagari || isHindiOrHinglish ->
                        Locale.forLanguageTag("hi-IN")
                    else ->
                        Locale.forLanguageTag("en-IN")
                }
                engine.language = targetLocale

                val voices = engine.voices.orEmpty()
                val chosenVoice: Voice? = selectHighestQualityVoice(
                    voices = voices,
                    targetLocale = targetLocale,
                    selectedVoiceName = prefs.selectedVoiceName,
                    preferMaleVoice = prefs.preferMaleVoice
                )

                if (chosenVoice != null) {
                    engine.voice = chosenVoice
                }

                pendingUtteranceIds.clear()
                _isSpeaking.value = true

                chunks.forEachIndexed { index, chunk ->
                    val chunkId = "max_chunk_${UUID.randomUUID()}"
                    pendingUtteranceIds.add(chunkId)

                    engine.setPitch(chunk.pitch)
                    engine.setSpeechRate(chunk.rate)

                    val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    engine.speak(chunk.text, queueMode, null, chunkId)

                    // Insert natural breathing pause between clauses and sentences
                    if (chunk.postPauseMs > 0L && index < chunks.lastIndex) {
                        val silenceId = "max_pause_${UUID.randomUUID()}"
                        pendingUtteranceIds.add(silenceId)
                        engine.playSilentUtterance(chunk.postPauseMs, TextToSpeech.QUEUE_ADD, silenceId)
                    }
                }
            } catch (e: Exception) {
                pendingUtteranceIds.clear()
                _isSpeaking.value = false
                mainHandler.post { onTtsCompletedCallback?.invoke() }
            }
        }
    }

    private fun selectHighestQualityVoice(
        voices: Set<Voice>,
        targetLocale: Locale,
        selectedVoiceName: String,
        preferMaleVoice: Boolean
    ): Voice? {
        if (selectedVoiceName.isNotBlank()) {
            voices.firstOrNull { it.name == selectedVoiceName }?.let { return it }
        }
        val matchingLocaleVoices = voices.filter { v ->
            v.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                (v.locale.country.equals("IN", ignoreCase = true) || v.locale.country.equals(targetLocale.country, ignoreCase = true))
        }.ifEmpty {
            voices.filter { it.locale.language.equals(targetLocale.language, ignoreCase = true) }
        }

        return matchingLocaleVoices.maxByOrNull { v ->
            var score = 0
            val n = v.name.lowercase(Locale.US)

            // 1. Highest tier: Google Network & WaveNet neural voices
            if (n.contains("network") || v.isNetworkConnectionRequired) score += 100
            if (n.contains("neural") || n.contains("wavenet")) score += 80

            // 2. Specific top-rated Indian Google neural voice codes (end, enc, ene, hie, hia, hic, hid)
            if (n.contains("x-end") || n.contains("x-enc") || n.contains("x-ene") ||
                n.contains("x-hie") || n.contains("x-hia") || n.contains("x-hic") || n.contains("x-hid")) {
                score += 60
            }

            // 3. Gender alignment matching user preference
            val isMale = (n.contains("male") && !n.contains("female")) ||
                n.contains("end") || n.contains("hie") || n.contains("hid") || n.contains("iom")
            if (isMale == preferMaleVoice) score += 40

            // 4. Exact Indian locale matching
            if (v.locale.country.equals("IN", ignoreCase = true)) score += 20
            score += (v.quality / 10)
            if (v.latency == Voice.LATENCY_NORMAL || v.latency == Voice.LATENCY_VERY_LOW) score += 10

            // 5. Heavy penalty for legacy robotic synthesizers
            if (n.contains("pico") || n.contains("espeak") || n.contains("compact")) score -= 120

            score
        }
    }

    fun stopSpeaking(): Boolean {
        val engine = tts
        val wasSpeaking = _isSpeaking.value || (engine?.isSpeaking == true) || activeAudioTrack != null
        try {
            activeSpeechSynthesisJob?.cancel()
            activeSpeechSynthesisJob = null
        } catch (_: Exception) {
        }
        try {
            activeAudioTrack?.let { track ->
                track.stop()
                track.release()
            }
            activeAudioTrack = null
        } catch (_: Exception) {
        }
        try {
            pendingUtteranceIds.clear()
            engine?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
        return wasSpeaking
    }

    fun buildRecognizerIntent(localeTag: String = "en-IN"): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
    }

    fun startListening(
        localeTag: String = "en-IN",
        startDelayMs: Long = 0L,
        onFinalTranscript: (String) -> Unit
    ) {
        _voiceError.value = null
        _activeLocaleTag.value = localeTag

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            _recognitionStage.value = SpeechRecognitionStage.ERROR
            _voiceError.value = "माइक्रोफ़ोन की अनुमति आवश्यक है (Microphone permission missing)। कृपया अनुमति दें।"
            return
        }

        if (!checkSpeechRecognizerSupport()) {
            _recognitionStage.value = SpeechRecognitionStage.ERROR
            _voiceError.value = "इस डिवाइस पर SpeechRecognition सेवा उपलब्ध नहीं है। कृपया सिस्टम वॉइस डायलॉग या टेक्स्ट इनपुट का उपयोग करें।"
            return
        }

        // Stop TTS immediately when user activates microphone
        stopSpeaking()
        this.onFinalTranscriptCallback = onFinalTranscript
        this.busyRetryPending = false

        _partialTranscript.value = ""
        _recognizedCandidates.value = emptyList()
        _waveformHistory.value = List(20) { 0.1f }
        _recognitionStage.value = SpeechRecognitionStage.INITIALIZING
        _isListening.value = true

        val launchRecognizer = Runnable {
            try {
                // Re-create SpeechRecognizer cleanly if needed to avoid ERROR_RECOGNIZER_BUSY
                speechRecognizer?.let {
                    try {
                        it.cancel()
                        it.destroy()
                    } catch (_: Exception) {
                    }
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }

                val intent = buildRecognizerIntent(localeTag)
                speechRecognizer?.startListening(intent)
                pendingLaunchRecognizerRunnable = null
            } catch (e: Exception) {
                pendingLaunchRecognizerRunnable = null
                _isListening.value = false
                _recognitionStage.value = SpeechRecognitionStage.ERROR
                _voiceError.value = "Could not start SpeechRecognizer: ${e.message}"
                mainHandler.post { onSpeechSessionEndedCallback?.invoke() }
            }
        }

        pendingLaunchRecognizerRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingLaunchRecognizerRunnable = launchRecognizer
        if (startDelayMs > 0L) {
            mainHandler.postDelayed(launchRecognizer, startDelayMs)
        } else {
            mainHandler.post(launchRecognizer)
        }
    }

    /**
     * Stops active audio capture and requests final transcript from SpeechRecognizer (`stopListening()`),
     * rather than discarding audio.
     */
    fun finishListeningAndProcess() {
        mainHandler.post {
            try {
                if (_isListening.value) {
                    _recognitionStage.value = SpeechRecognitionStage.PROCESSING_AUDIO
                    speechRecognizer?.stopListening()
                }
            } catch (_: Exception) {
                _isListening.value = false
                _recognitionStage.value = SpeechRecognitionStage.IDLE
            }
        }
    }

    fun stopListening() {
        pendingLaunchRecognizerRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingLaunchRecognizerRunnable = null
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            _isListening.value = false
            _recognitionStage.value = SpeechRecognitionStage.IDLE
            _rmsDbLevel.value = 0f
            _waveformHistory.value = List(20) { 0.1f }
        }
    }

    fun cancelAll() {
        stopSpeaking()
        stopListening()
        _partialTranscript.value = ""
    }

    fun clearError() {
        _voiceError.value = null
        if (_recognitionStage.value == SpeechRecognitionStage.ERROR) {
            _recognitionStage.value = SpeechRecognitionStage.IDLE
        }
    }

    /**
     * Allows unit/Robolectric tests or fallback RecognizerIntent activity results
     * to feed captured speech results directly into the voice engine pipeline.
     */
    fun onExternalSpeechResult(
        matches: List<String>,
        confidenceScores: FloatArray? = null,
        onTranscriptReady: ((String) -> Unit)? = null
    ) {
        _isListening.value = false
        _recognitionStage.value = SpeechRecognitionStage.IDLE
        _rmsDbLevel.value = 0f
        _partialTranscript.value = ""

        val cleaned = matches.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) {
            _voiceError.value = "Koi aawaz saaf sunai nahi di. Please try again."
            mainHandler.post { onSpeechSessionEndedCallback?.invoke() }
            return
        }

        val candidates = cleaned.mapIndexed { idx, text ->
            val conf = confidenceScores?.getOrNull(idx)?.takeIf { it >= 0f }
            RecognizedCandidate(transcript = text, confidence = conf)
        }
        _recognizedCandidates.value = candidates

        val best = cleaned.first()
        if (isStopSpeakingCommand(best)) {
            cancelAll()
        }
        (onTranscriptReady ?: onFinalTranscriptCallback)?.invoke(best)
    }

    private fun pushRmsSample(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
        _rmsDbLevel.value = rmsdB.coerceIn(0f, 12f)
        val current = _waveformHistory.value
        _waveformHistory.value = (current.drop(1) + normalized)
    }

    private val recognitionListener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            _recognitionStage.value = SpeechRecognitionStage.READY_FOR_SPEECH
        }

        override fun onBeginningOfSpeech() {
            _isListening.value = true
            _recognitionStage.value = SpeechRecognitionStage.SPEECH_DETECTED
        }

        override fun onRmsChanged(rmsdB: Float) {
            pushRmsSample(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _recognitionStage.value = SpeechRecognitionStage.PROCESSING_AUDIO
            _rmsDbLevel.value = 0f
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _rmsDbLevel.value = 0f
            _waveformHistory.value = List(20) { 0.1f }

            // If we already captured a valid partial transcript before timeout/error, use it!
            val partial = _partialTranscript.value.trim()
            if (partial.isNotEmpty()) {
                _partialTranscript.value = ""
                _recognitionStage.value = SpeechRecognitionStage.IDLE
                _recognizedCandidates.value = listOf(RecognizedCandidate(partial, null))
                onFinalTranscriptCallback?.invoke(partial)
                return
            }

            // If SpeechRecognizer was momentarily busy releasing the wake-word session, retry once after 300ms
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && !busyRetryPending) {
                busyRetryPending = true
                _isListening.value = true
                _recognitionStage.value = SpeechRecognitionStage.INITIALIZING
                mainHandler.postDelayed({
                    try {
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                    } catch (_: Exception) {
                    }
                    try {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(recognitionListener)
                        }
                        speechRecognizer?.startListening(buildRecognizerIntent(_activeLocaleTag.value))
                    } catch (_: Exception) {
                        _isListening.value = false
                        _recognitionStage.value = SpeechRecognitionStage.ERROR
                        mainHandler.post { onSpeechSessionEndedCallback?.invoke() }
                    }
                }, 300L)
                return
            }

            _recognitionStage.value = SpeechRecognitionStage.ERROR
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO ->
                    "माइक्रोफ़ोन ऑडियो कैप्चर में त्रुटि (ERROR_AUDIO)। कृपया माइक जांचें।"
                SpeechRecognizer.ERROR_CLIENT ->
                    "वॉइस सेशन रीसेट हो गया। दोबारा बोलने के लिए माइक बटन दबाएँ।"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "माइक्रोफ़ोन की अनुमति नहीं मिली। कृपया RECORD_AUDIO अनुमति दें।"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "आवाज़ पहचानने में नेटवर्क त्रुटि। कृपया पुनः प्रयास करें या नीचे टाइप करें।"
                SpeechRecognizer.ERROR_NO_MATCH ->
                    "कोई स्पष्ट आवाज़ या कमांड पहचानी नहीं गई। कृपया साफ़ बोलें (जैसे: 'शिवम को कॉल करो')।"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "SpeechRecognizer व्यस्त है। कृपया एक सेकंड बाद माइक दोबारा दबाएँ।"
                SpeechRecognizer.ERROR_SERVER ->
                    "वॉइस सर्वर में त्रुटि। कृपया दोबारा कोशिश करें।"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "समय सीमा के भीतर कोई आवाज़ नहीं सुनाई दी। बोलने के लिए माइक दबाएँ।"
                else ->
                    "आवाज़ पहचान रुक गई (कोड $error)। कृपया दोबारा प्रयास करें।"
            }
            _voiceError.value = msg
            mainHandler.post { onSpeechSessionEndedCallback?.invoke() }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            onExternalSpeechResult(matches, scores, onFinalTranscriptCallback)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches =
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val best = matches.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                _partialTranscript.value = best
                _recognitionStage.value = SpeechRecognitionStage.SPEECH_DETECTED
                if (isStopSpeakingCommand(best)) {
                    cancelAll()
                    onFinalTranscriptCallback?.invoke(best)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun release() {
        try {
            stopSpeaking()
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }

    companion object {
        private val STOP_PHRASES = setOf(
            "stop",
            "stop listening",
            "sunna band karo",
            "sunna band",
            "mic band karo",
            "chup ho jao",
            "chup",
            "cancel",
            "bas karo",
            "ruk jao",
            "shut up",
            "stop speaking",
            "सुनना बंद करो",
            "सुनना बंद",
            "माइक बंद करो",
            "चुप हो जाओ",
            "बस करो",
            "रुक जाओ"
        )

        fun isStopSpeakingCommand(input: String): Boolean {
            val normalized = input.trim().lowercase()
                .replace(Regex("""[.!?,।]"""), "")
                .trim()
            return STOP_PHRASES.contains(normalized) ||
                normalized == "max stop" ||
                normalized == "hey max stop" ||
                normalized == "max chup ho jao" ||
                normalized == "max sunna band karo" ||
                normalized == "मैक्स सुनना बंद करो"
        }
    }
}
