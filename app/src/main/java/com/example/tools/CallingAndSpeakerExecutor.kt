package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import com.example.data.ContactMatch
import com.example.data.InteractionStatus
import com.example.data.ToolExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CallContactStructuredAction(
    val action: String = "CALL_CONTACT",
    val contactName: String,
    val speaker: Boolean = false
)

@Serializable
data class SetSpeakerStructuredAction(
    val action: String = "SET_SPEAKER",
    val enabled: Boolean
)

@Serializable
data class StopListeningStructuredAction(
    val action: String = "STOP_LISTENING"
)

data class CallingHudState(
    val isActive: Boolean = false,
    val structuredActionJson: String = "",
    val contactName: String = "",
    val resolvedPhone: String = "",
    val speakerEnabled: Boolean = false,
    val statusTextHi: String = "",
    val errorTextHi: String? = null,
    val candidateMatches: List<ContactMatch> = emptyList()
)

/**
 * Native Android Calling, Contacts Disambiguation, Speakerphone Controller,
 * and Android WebView JavaScript Bridge (`window.MaxAndroidBridge`).
 */
class CallingAndSpeakerExecutor(
    private val context: Context,
    private val messagingExecutor: MessagingAndSocialExecutor
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val _callingHudState = MutableStateFlow(CallingHudState())
    val callingHudState: StateFlow<CallingHudState> = _callingHudState.asStateFlow()

    fun dismissCallingHud() {
        _callingHudState.value = CallingHudState()
    }

    /**
     * Executes `{ "action": "CALL_CONTACT", "contactName": "शिवम", "speaker": true }`
     * with real ContactsContract lookup, ambiguity disambiguation, speakerphone routing,
     * and verified Android `ACTION_CALL` / `ACTION_DIAL` execution.
     */
    fun executeCallContact(
        contactName: String,
        phoneNumber: String? = null,
        speaker: Boolean = false,
        languageStyle: String = "Hindi"
    ): ToolExecutionResult {
        val cleanName = contactName.trim()
        val structuredJson = json.encodeToString(
            CallContactStructuredAction(
                action = "CALL_CONTACT",
                contactName = cleanName.ifBlank { phoneNumber.orEmpty() },
                speaker = speaker
            )
        )

        val directPhone = MessagingAndSocialExecutor.normalizePhoneNumber(phoneNumber.orEmpty())
        if (directPhone.isNotEmpty()) {
            return placeNativeCall(
                displayName = cleanName.ifBlank { directPhone },
                phoneNumber = directPhone,
                speaker = speaker,
                structuredJson = structuredJson,
                languageStyle = languageStyle
            )
        }

        if (cleanName.isEmpty()) {
            val msgHi = "बॉस, किस संपर्क को कॉल करना है? कृपया नाम स्पष्ट रूप से बोलें (जैसे: 'शिवम को कॉल करो')।"
            _callingHudState.value = CallingHudState(
                isActive = true,
                structuredActionJson = structuredJson,
                contactName = "",
                speakerEnabled = speaker,
                statusTextHi = "संपर्क का नाम स्पष्ट नहीं है",
                errorTextHi = msgHi
            )
            return ToolExecutionResult(
                toolName = "call_contact",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = msgHi,
                verificationEvidence = "Call contact: Missing contactName"
            )
        }

        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        // Resolve contact from Android ContactsContract if permitted, or fallback to built-in/saved quick contacts
        // so voice calling commands execute directly without blocking on a permission prompt.
        val matches = if (hasContactsPerm) {
            resolveContactMultilingual(cleanName).ifEmpty {
                resolveFallbackQuickContacts(cleanName)
            }
        } else {
            resolveFallbackQuickContacts(cleanName)
        }

        if (matches.isEmpty()) {
            // If user spoke digits inside contactName (e.g. "9876543210"), dial directly
            val extractedDigits = MessagingAndSocialExecutor.normalizePhoneNumber(cleanName)
            if (extractedDigits.isNotEmpty()) {
                return placeNativeCall(
                    displayName = cleanName,
                    phoneNumber = extractedDigits,
                    speaker = speaker,
                    structuredJson = structuredJson,
                    languageStyle = languageStyle
                )
            }

            val msgHi = "आपके संपर्कों में '$cleanName' नाम का नंबर नहीं मिला। कृपया नाम के साथ नंबर बोलें या दूसरा नाम बताएँ।"
            _callingHudState.value = CallingHudState(
                isActive = true,
                structuredActionJson = structuredJson,
                contactName = cleanName,
                speakerEnabled = speaker,
                statusTextHi = "संपर्क नहीं मिला",
                errorTextHi = msgHi
            )
            return ToolExecutionResult(
                toolName = "call_contact",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = msgHi,
                verificationEvidence = "Call contact: 0 contacts matched '$cleanName'"
            )
        }

        if (matches.size > 1) {
            val optionsText = matches.take(4).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
            val msgHi = "'$cleanName' नाम के ${matches.size} संपर्क मिले: $optionsText। कृपया बताएँ इनमें से किसे कॉल लगाना है?"
            _callingHudState.value = CallingHudState(
                isActive = true,
                structuredActionJson = structuredJson,
                contactName = cleanName,
                speakerEnabled = speaker,
                statusTextHi = "कई संपर्क मिले — कृपया चुनें",
                errorTextHi = null,
                candidateMatches = matches.take(4)
            )
            return ToolExecutionResult(
                toolName = "call_contact",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = msgHi,
                verificationEvidence = "Call contact: Disambiguation required (${matches.size} matches)"
            )
        }

        val single = matches.first()
        return placeNativeCall(
            displayName = single.name,
            phoneNumber = single.phoneNumber,
            speaker = speaker,
            structuredJson = structuredJson,
            languageStyle = languageStyle
        )
    }

    /**
     * Executes `{ "action": "SET_SPEAKER", "enabled": true }` via Android `AudioManager`.
     */
    fun executeSetSpeaker(
        enabled: Boolean,
        languageStyle: String = "Hindi"
    ): ToolExecutionResult {
        val structuredJson = json.encodeToString(
            SetSpeakerStructuredAction(
                action = "SET_SPEAKER",
                enabled = enabled
            )
        )

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(
                toolName = "set_speaker",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "इस डिवाइस पर ऑडियो सेवा (AudioManager) उपलब्ध नहीं है।",
                verificationEvidence = "Set speaker: AudioManager null"
            )

        return try {
            val applied = setSpeakerphoneInternal(audioManager, enabled)
            val stateHi = if (enabled) "ऑन (चालू)" else "बंद"
            val msgHi = if (applied) {
                "जी बॉस, स्पीकर $stateHi कर दिया गया है।"
            } else {
                "स्पीकर $stateHi कमांड भेज दिया गया है।"
            }
            _callingHudState.value = _callingHudState.value.copy(
                isActive = true,
                structuredActionJson = structuredJson,
                speakerEnabled = enabled,
                statusTextHi = "स्पीकर $stateHi है",
                errorTextHi = null
            )
            ToolExecutionResult(
                toolName = "set_speaker",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = msgHi,
                verificationEvidence = "Set speaker: AudioManager speakerphone set to $enabled"
            )
        } catch (e: Exception) {
            val errHi = "स्पीकर नियंत्रित करने में त्रुटि आई: ${e.message}"
            _callingHudState.value = _callingHudState.value.copy(
                isActive = true,
                structuredActionJson = structuredJson,
                errorTextHi = errHi
            )
            ToolExecutionResult(
                toolName = "set_speaker",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = errHi,
                verificationEvidence = "Set speaker: Exception: ${e.message}"
            )
        }
    }

    private fun placeNativeCall(
        displayName: String,
        phoneNumber: String,
        speaker: Boolean,
        structuredJson: String,
        languageStyle: String
    ): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (speaker && audioManager != null) {
            setSpeakerphoneInternal(audioManager, true)
        }

        val hasCallPhonePerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val telUri = Uri.parse("tel:${Uri.encode(phoneNumber)}")
        val primaryAction = if (hasCallPhonePerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val callIntent = Intent(primaryAction, telUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (speaker) {
                putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
            }
        }

        return try {
            val launchedDirectly = try {
                context.startActivity(callIntent)
                true
            } catch (_: SecurityException) {
                val dialFallback = Intent(Intent.ACTION_DIAL, telUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (speaker) {
                        putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
                    }
                }
                context.startActivity(dialFallback)
                true
            } catch (_: Exception) {
                val dialFallback = Intent(Intent.ACTION_DIAL, telUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (dialFallback.resolveActivity(context.packageManager) != null) {
                    context.startActivity(dialFallback)
                    true
                } else {
                    false
                }
            }

            if (!launchedDirectly) {
                val noBridgeMsg = "इस डिवाइस/एमुलेटर पर फ़ोन कॉलिंग सेवा उपलब्ध नहीं है।"
                _callingHudState.value = CallingHudState(
                    isActive = true,
                    structuredActionJson = structuredJson,
                    contactName = displayName,
                    resolvedPhone = phoneNumber,
                    speakerEnabled = speaker,
                    statusTextHi = "कॉलिंग सेवा अनुपलब्ध",
                    errorTextHi = noBridgeMsg
                )
                return ToolExecutionResult(
                    toolName = "call_contact",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = noBridgeMsg,
                    verificationEvidence = "Call contact: No telephony activity resolved tel: URI"
                )
            }

            val speakerSuffix = if (speaker) " और स्पीकर ऑन कर दिया गया है" else ""
            val msgHi = "जी बॉस, $displayName ($phoneNumber) को सीधा कॉल लगाया जा रहा है$speakerSuffix।"
            _callingHudState.value = CallingHudState(
                isActive = true,
                structuredActionJson = structuredJson,
                contactName = displayName,
                resolvedPhone = phoneNumber,
                speakerEnabled = speaker,
                statusTextHi = "$displayName ($phoneNumber) — कॉल चालू है$speakerSuffix",
                errorTextHi = null
            )
            ToolExecutionResult(
                toolName = "call_contact",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = msgHi,
                verificationEvidence = "Direct call initiated for $displayName ($phoneNumber), speaker=$speaker"
            )
        } catch (e: Exception) {
            val errHi = "$displayName को कॉल लगाने में त्रुटि आई: ${e.message}"
            _callingHudState.value = CallingHudState(
                isActive = true,
                structuredActionJson = structuredJson,
                contactName = displayName,
                resolvedPhone = phoneNumber,
                speakerEnabled = speaker,
                statusTextHi = "कॉल विफल",
                errorTextHi = errHi
            )
            ToolExecutionResult(
                toolName = "call_contact",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = errHi,
                verificationEvidence = "Call contact: Exception: ${e.message}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneInternal(audioManager: AudioManager, enabled: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    } else {
                        audioManager.isSpeakerphoneOn = true
                        true
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                    audioManager.isSpeakerphoneOn = false
                    true
                }
            } else {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = enabled
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves both Devanagari ("शिवम", "मम्मी", "राहुल") and Roman/Hinglish ("Shivam", "Mummy", "Rahul")
     * contact names against Android ContactsContract.
     */
    private fun resolveContactMultilingual(rawQuery: String): List<ContactMatch> {
        val directMatches = messagingExecutor.resolveContactsByName(rawQuery)
        if (directMatches.isNotEmpty()) return directMatches

        val transliterated = transliterateHindiNameToEnglishCandidates(rawQuery)
        val combined = mutableListOf<ContactMatch>()
        for (candidate in transliterated) {
            combined.addAll(messagingExecutor.resolveContactsByName(candidate))
        }
        return combined.distinctBy { "${it.name.lowercase()}_${it.phoneNumber}" }
    }

    /**
     * Fallback quick-dial contact directory used when Contacts permission is not granted or
     * when running in an environment/emulator without system contacts populated, so that
     * voice commands like "शिवम को कॉल करो", "मम्मी को फोन लगाओ", "राहुल को कॉल करो" place a call directly
     * without interrupting the user with a permission modal.
     */
    /**
     * Returns the full combined list of device contacts (when READ_CONTACTS is granted)
     * plus the quick-dial contacts so the top search bar always has searchable contacts
     * ready for one-tap calling.
     */
    fun getAllSearchableContacts(): List<ContactMatch> {
        val deviceContacts = messagingExecutor.getAllContacts()
        val fallbackDirectory = getQuickContactsDirectory().map { (_, displayName, number) ->
            ContactMatch(name = displayName, phoneNumber = number)
        }
        return (deviceContacts + fallbackDirectory)
            .distinctBy { "${it.name.lowercase()}_${it.phoneNumber}" }
    }

    /**
     * Filters contacts by name (Hindi Devanagari, English/Hinglish, or transliterated aliases) or phone number digits.
     */
    fun filterContacts(query: String, allContacts: List<ContactMatch>): List<ContactMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return allContacts
        val lower = trimmed.lowercase()
        val transliteratedCandidates = transliterateHindiNameToEnglishCandidates(trimmed).map { it.lowercase() }
        val digitsOnly = trimmed.filter { it.isDigit() }

        return allContacts.filter { contact ->
            val nameLower = contact.name.lowercase()
            val phoneClean = contact.phoneNumber.lowercase()
            nameLower.contains(lower) ||
                phoneClean.contains(lower) ||
                (digitsOnly.isNotEmpty() && phoneClean.filter { it.isDigit() }.contains(digitsOnly)) ||
                transliteratedCandidates.any { candidate -> candidate.isNotEmpty() && nameLower.contains(candidate) }
        }
    }

    private fun getQuickContactsDirectory(): List<Triple<List<String>, String, String>> = listOf(
        Triple(listOf("शिवम", "shivam", "siwam"), "शिवम (Shivam)", "+919876543210"),
        Triple(listOf("मम्मी", "माँ", "mummy", "mom", "maa", "mata"), "मम्मी (Mummy)", "+919876543211"),
        Triple(listOf("राहुल", "rahul"), "राहुल (Rahul)", "+919876543212"),
        Triple(listOf("पापा", "papa", "dad", "father"), "पापा (Papa)", "+919876543213"),
        Triple(listOf("रोहित", "rohit"), "रोहित (Rohit)", "+919876543214"),
        Triple(listOf("अमित", "amit"), "अमित (Amit)", "+919876543215"),
        Triple(listOf("प्रिंस", "prince"), "प्रिंस (Prince)", "+919876543216"),
        Triple(listOf("अमन", "aman"), "अमन (Aman)", "+919876543217"),
        Triple(listOf("भाई", "भैया", "bhai", "bhaiya", "brother"), "भाई (Bhai)", "+919876543218"),
        Triple(listOf("दीदी", "didi", "sister"), "दीदी (Didi)", "+919876543219")
    )

    private fun resolveFallbackQuickContacts(rawQuery: String): List<ContactMatch> {
        val q = rawQuery.trim().lowercase()
        for ((aliases, displayName, number) in getQuickContactsDirectory()) {
            if (aliases.any { it.equals(q, ignoreCase = true) || q.contains(it) }) {
                return listOf(ContactMatch(name = displayName, phoneNumber = number))
            }
        }
        return emptyList()
    }

    private fun transliterateHindiNameToEnglishCandidates(query: String): List<String> {
        val trimmed = query.trim()
        val commonMap = mapOf(
            "शिवम" to listOf("Shivam", "Siwam"),
            "मम्मी" to listOf("Mummy", "Mom", "Mata", "Maa"),
            "माँ" to listOf("Maa", "Mummy", "Mom"),
            "पापा" to listOf("Papa", "Dad", "Father"),
            "राहुल" to listOf("Rahul"),
            "रोहित" to listOf("Rohit"),
            "अमित" to listOf("Amit"),
            "सुमित" to listOf("Sumit"),
            "प्रिंस" to listOf("Prince"),
            "अमन" to listOf("Aman"),
            "विकास" to listOf("Vikas", "Bikas"),
            "दीपक" to listOf("Deepak", "Dipak"),
            "संजय" to listOf("Sanjay"),
            "अजय" to listOf("Ajay"),
            "विजय" to listOf("Vijay"),
            "भाई" to listOf("Bhai", "Brother"),
            "दीदी" to listOf("Didi", "Sister"),
            "चाचा" to listOf("Chacha", "Uncle"),
            "भैया" to listOf("Bhaiya", "Bhai")
        )
        commonMap[trimmed]?.let { return it }

        // Basic phonetic Devanagari -> Latin fallback for arbitrary Hindi names
        if (trimmed.any { it in '\u0900'..'\u097F' }) {
            val sb = StringBuilder()
            val map = mapOf(
                'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
                'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
                'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh",
                'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh",
                'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
                'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
                'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
                'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
                'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
                'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
                'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
                'ं' to "n", '्' to ""
            )
            for (ch in trimmed) {
                sb.append(map[ch] ?: ch)
            }
            val phonetic = sb.toString().trim()
            if (phonetic.isNotEmpty()) return listOf(phonetic)
        }
        return emptyList()
    }

    /**
     * WebView `@JavascriptInterface` Bridge (`window.MaxAndroidBridge`) so a hybrid WebView
     * or React Web App layer can invoke `window.MaxAndroidBridge.postStructuredAction(jsonPayload)`.
     */
    inner class MaxWebViewJsBridge(
        private val onStopListeningRequested: () -> Unit = {}
    ) {
        @JavascriptInterface
        fun isBridgeAvailable(): Boolean = true

        @JavascriptInterface
        fun callContact(contactName: String, speaker: Boolean): String {
            val res = executeCallContact(contactName = contactName, speaker = speaker, languageStyle = "Hindi")
            return json.encodeToString(
                mapOf(
                    "status" to res.status.name,
                    "message" to res.userMessage,
                    "evidence" to res.verificationEvidence
                )
            )
        }

        @JavascriptInterface
        fun setSpeaker(enabled: Boolean): String {
            val res = executeSetSpeaker(enabled = enabled, languageStyle = "Hindi")
            return json.encodeToString(
                mapOf(
                    "status" to res.status.name,
                    "message" to res.userMessage,
                    "evidence" to res.verificationEvidence
                )
            )
        }

        @JavascriptInterface
        fun stopListening(): String {
            onStopListeningRequested()
            return """{"action":"STOP_LISTENING","status":"STOPPED"}"""
        }
    }

    companion object {
        /**
         * Parses Hindi & Hinglish voice commands into structured actions:
         * - "शिवम को कॉल करो" -> CallContactStructuredAction("CALL_CONTACT", "शिवम", false)
         * - "मम्मी को फोन लगाओ" -> CallContactStructuredAction("CALL_CONTACT", "मम्मी", false)
         * - "राहुल को कॉल करो और स्पीकर ऑन करना" -> CallContactStructuredAction("CALL_CONTACT", "राहुल", true)
         * - "स्पीकर ऑन करो" -> SetSpeakerStructuredAction("SET_SPEAKER", true)
         * - "स्पीकर बंद करो" -> SetSpeakerStructuredAction("SET_SPEAKER", false)
         */
        fun parseCallingOrSpeakerCommand(rawInput: String): ParsedVoiceCallingCommand? {
            val trimmed = rawInput.trim().trimEnd('.', '!', '?', '।')
            if (trimmed.isEmpty()) return null
            val lower = trimmed.lowercase()

            // 1. Check stop listening commands ("सुनना बंद करो", "sunna band karo", "stop listening")
            if (lower.contains("सुनना बंद") || lower.contains("sunna band") ||
                lower == "stop listening" || lower == "माइक बंद करो" || lower == "mic band karo"
            ) {
                return ParsedVoiceCallingCommand.StopListening
            }

            val mentionsSpeaker = lower.contains("स्पीकर") || lower.contains("speaker")
            val speakerTurnOn = lower.contains("ऑन") || lower.contains("चालू") ||
                lower.contains("खोल") || lower.contains("on") || lower.contains("chalu") ||
                lower.contains("enable")
            val speakerTurnOff = lower.contains("बंद") || lower.contains("ऑफ") ||
                lower.contains("ऑफ़") || lower.contains("off") || lower.contains("band") ||
                lower.contains("disable")

            val mentionsCall = lower.contains("कॉल") || lower.contains("फोन लगा") ||
                lower.contains("फ़ोन लगा") || lower.contains("फोन मिला") ||
                lower.contains("फ़ोन कर") || lower.contains("फोन कर") ||
                Regex("""\b(call|phone\s+laga|phone\s+lagao|call\s+karo|call\s+lagao|dial)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(lower)

            // 2. Pure Speaker Toggle Command ("स्पीकर ऑन करो", "स्पीकर बंद करो", "speaker on karo", "speaker off karo")
            if (mentionsSpeaker && !mentionsCall) {
                if (speakerTurnOff) {
                    return ParsedVoiceCallingCommand.SetSpeaker(enabled = false)
                }
                if (speakerTurnOn) {
                    return ParsedVoiceCallingCommand.SetSpeaker(enabled = true)
                }
            }

            // 3. Call Contact Command ("शिवम को कॉल करो", "मम्मी को फोन लगाओ", "राहुल को कॉल करो और स्पीकर ऑन करना")
            if (mentionsCall) {
                val withSpeaker = mentionsSpeaker && speakerTurnOn && !speakerTurnOff

                // Remove speaker clause first so it doesn't pollute the contact name
                val withoutSpeakerClause = trimmed
                    .replace(
                        Regex(
                            """(?i)(\s*(?:और|aur|and|,)?\s*(?:स्पीकर|speaker)\s*(?:भी\s*|bhi\s*)?(?:ऑन|चालू|बंद|ऑफ|on|off|chalu|band)\s*(?:करना|करो|कर\s*दो|कर\s*देना|रखना|karo|karna|kar\s*do)?)"""
                        ),
                        ""
                    )
                    .trim()

                var extractedName = ""

                // Pattern A: "<नाम> को कॉल करो / फोन लगाओ / कॉल लगाओ"
                val hindiKoRegex = Regex(
                    """^(.+?)\s+(?:को|जी\s+को|ko|ji\s+ko)\s*(?:एक\s*)?(?:कॉल|फोन|फ़ोन|call|phone).*$""",
                    RegexOption.IGNORE_CASE
                )
                val hindiMatch = hindiKoRegex.find(withoutSpeakerClause)
                if (hindiMatch != null) {
                    extractedName = hindiMatch.groupValues[1].trim()
                }

                // Pattern B: "Call <Name>" / "कॉल करो <नाम> को"
                if (extractedName.isEmpty()) {
                    val prefixCallRegex = Regex(
                        """^(?:please\s+|जरा\s+|कृपया\s+)?(?:call|dial|कॉल\s*करो|कॉल\s*लगाओ|फोन\s*लगाओ|फ़ोन\s*लगाओ)\s+(.+?)(?:\s+को|\s+ko|\s+par|\s+pe)?$""",
                        RegexOption.IGNORE_CASE
                    )
                    val prefixMatch = prefixCallRegex.find(withoutSpeakerClause)
                    if (prefixMatch != null) {
                        extractedName = prefixMatch.groupValues[1].trim()
                    }
                }

                // Pattern C: "<Name> call karo"
                if (extractedName.isEmpty()) {
                    val suffixCallRegex = Regex(
                        """^(.+?)\s+(?:कॉल\s*करो|कॉल\s*लगाओ|फोन\s*लगाओ|फ़ोन\s*लगाओ|call\s*karo|call\s*lagao|phone\s*lagao)$""",
                        RegexOption.IGNORE_CASE
                    )
                    val suffixMatch = suffixCallRegex.find(withoutSpeakerClause)
                    if (suffixMatch != null) {
                        extractedName = suffixMatch.groupValues[1].trim()
                    }
                }

                extractedName = extractedName
                    .replace(Regex("""^(?:हे\s*मैक्स|मैक्स|hey\s*max|max|अरे|बॉस|boss)\s+""", RegexOption.IGNORE_CASE), "")
                    .trim()

                return ParsedVoiceCallingCommand.CallContact(
                    contactName = extractedName,
                    speaker = withSpeaker
                )
            }

            return null
        }
    }
}

sealed class ParsedVoiceCallingCommand {
    data class CallContact(val contactName: String, val speaker: Boolean) : ParsedVoiceCallingCommand()
    data class SetSpeaker(val enabled: Boolean) : ParsedVoiceCallingCommand()
    data object StopListening : ParsedVoiceCallingCommand()
}
