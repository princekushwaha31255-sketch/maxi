package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "max_ai_preferences")

data class MaxUserPreferences(
    val preferredLanguageStyle: String = "Auto (Match User)", // Auto, Hinglish, Hindi, English
    val speechRecognitionLocale: String = "en-IN", // en-IN, hi-IN, en-US
    val preferMaleVoice: Boolean = true,
    val selectedVoiceName: String = "",
    val speechRate: Float = 0.98f, // Ultra-natural human conversational rate
    val speechPitch: Float = 1.00f, // Balanced, warm, resonant natural frequency
    val autoSpeakResponses: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val wakePhrasesCsv: String = "Hey MAX,OK MAX,MAX,Hey MAXI,MAXI",
    val darkMode: Boolean = true,
    val hideSecurityBanner: Boolean = false,
    val assistantName: String = "MAXI",
    val assistantPersonality: String = "Natural, respectful, concise, confident, helpful, and practical",
    val persistentMemoryConsent: Boolean = true,
    val webSearchGroundingEnabled: Boolean = true,
    val codingAssistantMode: Boolean = true,
    val voiceStylePreset: String = "Auto (Context-Aware)", // Auto (Context-Aware), Warm Companion, Calm & Composed, Energetic & Lively, Expressive Storyteller
    val useGeminiNeuralTts: Boolean = false, // Expressive Android Neural TTS is the default for zero latency; Gemini TTS can be enabled in settings
    val geminiVoiceName: String = "Puck", // Puck, Aoede, Charon, Fenrir, Kore
    val explanationLevel: String = "Adaptive", // Adaptive, Beginner-Friendly, Detailed Step-by-Step, Concise Direct
    val adaptiveLearningEnabled: Boolean = true,
    val activeSessionId: String = "default_session",
    val autoCheckUpdatesOnStartup: Boolean = true,
    val updateManifestUrl: String = "https://raw.githubusercontent.com/princekushwaha/max-ai/main/version.json",
    val lastUpdateCheckTimestamp: Long = 0L
)

class MaxPreferencesRepository(private val context: Context) {

    private object Keys {
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val SPEECH_LOCALE = stringPreferencesKey("speech_locale")
        val PREFER_MALE_VOICE = booleanPreferencesKey("prefer_male_voice")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_PHRASES = stringPreferencesKey("wake_phrases")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val HIDE_SECURITY_BANNER = booleanPreferencesKey("hide_security_banner")
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val ASSISTANT_PERSONALITY = stringPreferencesKey("assistant_personality")
        val PERSISTENT_MEMORY_CONSENT = booleanPreferencesKey("persistent_memory_consent")
        val WEB_SEARCH_GROUNDING = booleanPreferencesKey("web_search_grounding")
        val CODING_ASSISTANT_MODE = booleanPreferencesKey("coding_assistant_mode")
        val VOICE_STYLE_PRESET = stringPreferencesKey("voice_style_preset")
        val USE_GEMINI_NEURAL_TTS = booleanPreferencesKey("use_gemini_neural_tts")
        val GEMINI_VOICE_NAME = stringPreferencesKey("gemini_voice_name")
        val EXPLANATION_LEVEL = stringPreferencesKey("explanation_level")
        val ADAPTIVE_LEARNING_ENABLED = booleanPreferencesKey("adaptive_learning_enabled")
        val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val UPDATE_MANIFEST_URL = stringPreferencesKey("update_manifest_url")
        val LAST_UPDATE_CHECK_TIMESTAMP = longPreferencesKey("last_update_check_timestamp")
    }

    val preferencesFlow: Flow<MaxUserPreferences> = context.dataStore.data.map { prefs ->
        MaxUserPreferences(
            preferredLanguageStyle = prefs[Keys.PREFERRED_LANGUAGE] ?: "Auto (Match User)",
            speechRecognitionLocale = prefs[Keys.SPEECH_LOCALE] ?: "en-IN",
            preferMaleVoice = prefs[Keys.PREFER_MALE_VOICE] ?: true,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME] ?: "",
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 0.92f,
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakePhrasesCsv = (prefs[Keys.WAKE_PHRASES] ?: "Hey MAX,OK MAX,MAX,Hey MAXI,MAXI").let { saved ->
                if (!saved.contains("ok max", ignoreCase = true)) "$saved,OK MAX" else saved
            },
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            hideSecurityBanner = prefs[Keys.HIDE_SECURITY_BANNER] ?: false,
            assistantName = prefs[Keys.ASSISTANT_NAME] ?: "MAXI",
            assistantPersonality = prefs[Keys.ASSISTANT_PERSONALITY]
                ?: "Natural, respectful, concise, confident, helpful, and practical",
            persistentMemoryConsent = prefs[Keys.PERSISTENT_MEMORY_CONSENT] ?: true,
            webSearchGroundingEnabled = prefs[Keys.WEB_SEARCH_GROUNDING] ?: true,
            codingAssistantMode = prefs[Keys.CODING_ASSISTANT_MODE] ?: true,
            voiceStylePreset = prefs[Keys.VOICE_STYLE_PRESET] ?: "Auto (Context-Aware)",
            useGeminiNeuralTts = prefs[Keys.USE_GEMINI_NEURAL_TTS] ?: true,
            geminiVoiceName = prefs[Keys.GEMINI_VOICE_NAME] ?: "Puck",
            explanationLevel = prefs[Keys.EXPLANATION_LEVEL] ?: "Adaptive",
            adaptiveLearningEnabled = prefs[Keys.ADAPTIVE_LEARNING_ENABLED] ?: true,
            activeSessionId = prefs[Keys.ACTIVE_SESSION_ID] ?: "default_session",
            autoCheckUpdatesOnStartup = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            updateManifestUrl = prefs[Keys.UPDATE_MANIFEST_URL]
                ?: "https://raw.githubusercontent.com/princekushwaha/max-ai/main/version.json",
            lastUpdateCheckTimestamp = prefs[Keys.LAST_UPDATE_CHECK_TIMESTAMP] ?: 0L
        )
    }

    suspend fun updatePreferredLanguage(style: String) {
        context.dataStore.edit { it[Keys.PREFERRED_LANGUAGE] = style }
    }

    suspend fun updateSpeechLocale(locale: String) {
        context.dataStore.edit { it[Keys.SPEECH_LOCALE] = locale }
    }

    suspend fun updatePreferMaleVoice(preferMale: Boolean) {
        context.dataStore.edit { it[Keys.PREFER_MALE_VOICE] = preferMale }
    }

    suspend fun updateSelectedVoiceName(voiceName: String) {
        context.dataStore.edit { it[Keys.SELECTED_VOICE_NAME] = voiceName }
    }

    suspend fun updateSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f) }
    }

    suspend fun updateSpeechPitch(pitch: Float) {
        context.dataStore.edit { it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.5f, 1.8f) }
    }

    suspend fun updateAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun updateWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun updateWakePhrases(phrasesCsv: String) {
        context.dataStore.edit { it[Keys.WAKE_PHRASES] = phrasesCsv }
    }

    suspend fun updateDarkMode(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = dark }
    }

    suspend fun dismissSecurityBanner() {
        context.dataStore.edit { it[Keys.HIDE_SECURITY_BANNER] = true }
    }

    suspend fun updateAssistantPersonality(personality: String) {
        context.dataStore.edit { it[Keys.ASSISTANT_PERSONALITY] = personality }
    }

    suspend fun updatePersistentMemoryConsent(consented: Boolean) {
        context.dataStore.edit { it[Keys.PERSISTENT_MEMORY_CONSENT] = consented }
    }

    suspend fun updateWebSearchGrounding(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEB_SEARCH_GROUNDING] = enabled }
    }

    suspend fun updateCodingAssistantMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CODING_ASSISTANT_MODE] = enabled }
    }

    suspend fun updateVoiceStylePreset(preset: String) {
        context.dataStore.edit { it[Keys.VOICE_STYLE_PRESET] = preset }
    }

    suspend fun updateUseGeminiNeuralTts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_GEMINI_NEURAL_TTS] = enabled }
    }

    suspend fun updateGeminiVoiceName(voiceName: String) {
        context.dataStore.edit { it[Keys.GEMINI_VOICE_NAME] = voiceName }
    }

    suspend fun resetVoiceToNaturalDefaults() {
        context.dataStore.edit {
            it[Keys.SPEECH_RATE] = 0.98f
            it[Keys.SPEECH_PITCH] = 1.00f
            it[Keys.VOICE_STYLE_PRESET] = "Auto (Context-Aware)"
            it[Keys.USE_GEMINI_NEURAL_TTS] = true
            it[Keys.GEMINI_VOICE_NAME] = "Puck"
            it[Keys.SELECTED_VOICE_NAME] = ""
        }
    }

    suspend fun updateExplanationLevel(level: String) {
        context.dataStore.edit { it[Keys.EXPLANATION_LEVEL] = level }
    }

    suspend fun updateAdaptiveLearningEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ADAPTIVE_LEARNING_ENABLED] = enabled }
    }

    suspend fun updateActiveSessionId(sessionId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_SESSION_ID] = sessionId }
    }

    suspend fun updateAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun updateManifestUrl(url: String) {
        context.dataStore.edit { it[Keys.UPDATE_MANIFEST_URL] = url.trim() }
    }

    suspend fun recordUpdateCheckTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_TIMESTAMP] = timestamp }
    }

    suspend fun resetPersonalizationProfile() {
        context.dataStore.edit {
            it[Keys.PREFERRED_LANGUAGE] = "Auto (Match User)"
            it[Keys.EXPLANATION_LEVEL] = "Adaptive"
            it[Keys.ASSISTANT_PERSONALITY] = "Natural, respectful, concise, confident, helpful, and practical"
            it[Keys.ADAPTIVE_LEARNING_ENABLED] = true
        }
    }
}
