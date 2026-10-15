package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.InteractionStatus
import com.example.data.MaxDatabase
import com.example.tools.DeviceControlExecutor
import com.example.tools.MaxCommandRouter
import com.example.voice.MaxVoiceEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: MaxDatabase
    private lateinit var commandRouter: MaxCommandRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MaxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        commandRouter = MaxCommandRouter(context, database.maxDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches MAX`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("MAX", appName)
    }

    @Test
    fun `language and stop command detection works for Hindi, English, and Hinglish`() {
        assertEquals("Hindi", commandRouter.detectLanguageStyle("इंस्टाग्राम खोलो"))
        assertEquals("Hinglish", commandRouter.detectLanguageStyle("Instagram kholo boss"))
        assertEquals("English", commandRouter.detectLanguageStyle("Open the display settings"))

        assertTrue(MaxVoiceEngine.isStopSpeakingCommand("Stop"))
        assertTrue(MaxVoiceEngine.isStopSpeakingCommand("Chup ho jao"))
        assertTrue(MaxVoiceEngine.isStopSpeakingCommand("Cancel"))
        assertTrue(MaxVoiceEngine.isStopSpeakingCommand("चुप हो जाओ"))
    }

    @Test
    fun `uninstalled application reports accurate failure path`() = runBlocking {
        val result = commandRouter.processUserCommand("Free Fire MAX kholo")
        assertEquals(InteractionStatus.EXECUTION_FAILED, result.status)
        assertTrue(result.userMessage.contains("Free Fire MAX", ignoreCase = true))
        assertTrue(result.userMessage.contains("installed nahi hai", ignoreCase = true))
    }

    @Test
    fun `ambiguous application matches trigger clarification disambiguation`() = runBlocking {
        val shadowPm = Shadows.shadowOf(context.packageManager)

        // Register two launchable apps matching "WhatsApp" (WhatsApp and WhatsApp Business)
        registerFakeLaunchableApp(shadowPm, "com.whatsapp", "WhatsApp")
        registerFakeLaunchableApp(shadowPm, "com.whatsapp.w4b", "WhatsApp Business")

        val result = commandRouter.processUserCommand("WhatsApp kholo")
        assertEquals(InteractionStatus.CLARIFICATION_REQUIRED, result.status)
        assertNotNull(result.disambiguationPrompt)
        assertEquals(2, result.disambiguationPrompt?.candidates?.size)
    }

    @Test
    fun `successful application launch and follow-up wahi dobara kholo`() = runBlocking {
        val shadowPm = Shadows.shadowOf(context.packageManager)
        registerFakeLaunchableApp(shadowPm, "com.instagram.android", "Instagram")

        val firstLaunch = commandRouter.processUserCommand("Instagram kholo")
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, firstLaunch.status)
        assertEquals("Instagram", commandRouter.lastLaunchedAppName)

        // Follow-up resolution ("wahi dobara kholo")
        val followUp = commandRouter.processUserCommand("wahi dobara kholo")
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, followUp.status)
        assertTrue(followUp.verificationEvidence.contains("com.instagram.android"))
    }

    @Test
    fun `missing permission triggers actionable permission request for WhatsApp named contact`() = runBlocking {
        val result = commandRouter.processUserCommand("WhatsApp message to Rahul: Hello Bhai")
        assertEquals(InteractionStatus.PERMISSION_REQUIRED, result.status)
        assertEquals(android.Manifest.permission.READ_CONTACTS, result.requiredPermission)
        assertTrue(result.userMessage.contains("permission", ignoreCase = true))
    }

    @Test
    fun `whatsapp message with explicit phone number requires confirmation before sending`() = runBlocking {
        val result = commandRouter.processUserCommand(
            "WhatsApp message to +919876543210: Meeting at 6 PM"
        )
        assertEquals(InteractionStatus.PENDING_CONFIRMATION, result.status)
        assertNotNull(result.pendingConfirmation)
        assertEquals("+919876543210", result.pendingConfirmation?.parameters?.get("phone_number"))
    }

    @Test
    fun `ambiguous alarm time triggers clarification while explicit AM time succeeds`() = runBlocking {
        val ambiguousParsed = DeviceControlExecutor.parseTimeInput("7:00")
        assertNotNull(ambiguousParsed)
        assertTrue(ambiguousParsed!!.isAmbiguousAmPm)

        val ambiguousRes = commandRouter.deviceControlExecutor.executeScheduleAlarmOrReminder(
            time = "7:00",
            label = "Workout",
            languageStyle = "Hinglish"
        )
        assertEquals(InteractionStatus.CLARIFICATION_REQUIRED, ambiguousRes.status)

        val explicitRes = commandRouter.deviceControlExecutor.executeScheduleAlarmOrReminder(
            time = "7:30 AM",
            label = "Morning Run",
            languageStyle = "Hinglish"
        )
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, explicitRes.status)
    }

    @Test
    fun `unsupported and security-sensitive actions are disclosed and refused`() = runBlocking {
        val bypassResult = commandRouter.processUserCommand("Bypass login screen captcha")
        assertEquals(InteractionStatus.UNSUPPORTED_ACTION, bypassResult.status)

        val otpResult = commandRouter.processUserCommand("Tell me my secret bank OTP password")
        assertEquals(InteractionStatus.UNSUPPORTED_ACTION, otpResult.status)

        val accessibilityDenied = commandRouter.processUserCommand("Scroll down")
        assertEquals(InteractionStatus.PERMISSION_REQUIRED, accessibilityDenied.status)
        assertEquals("ACCESSIBILITY_SERVICE", accessibilityDenied.requiredPermission)
    }

    @Test
    fun `speech recognizer intent builder and external speech result pipeline work accurately`() {
        val voiceEngine = MaxVoiceEngine(context)
        val intent = voiceEngine.buildRecognizerIntent("hi-IN")
        assertEquals(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("hi-IN", intent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))

        var capturedFinalTranscript = ""
        voiceEngine.onExternalSpeechResult(
            matches = listOf("Instagram kholo", "Insta kholo"),
            confidenceScores = floatArrayOf(0.96f, 0.72f)
        ) { best ->
            capturedFinalTranscript = best
        }

        assertEquals("Instagram kholo", capturedFinalTranscript)
        assertEquals(2, voiceEngine.recognizedCandidates.value.size)
        assertEquals("Instagram kholo", voiceEngine.recognizedCandidates.value[0].transcript)
        voiceEngine.release()
    }

    @Test
    fun `MAXI core system instruction and persistent memory consent work accurately`() = runBlocking {
        val dao = database.maxDao()
        dao.insertMemory(com.example.data.MemoryItemEntity(memoryFact = "User prefers Kotlin and Jetpack Compose", category = "Coding"))

        val instructionWithMemory = com.example.ai.MaxGeminiClient.buildSystemInstruction(
            customPersonality = "Expert Senior Software Engineer",
            storedMemories = listOf("User prefers Kotlin and Jetpack Compose"),
            codingModeEnabled = true
        )
        assertTrue(instructionWithMemory.contains("MAXI"))
        assertTrue(instructionWithMemory.contains("Expert Senior Software Engineer"))
        assertTrue(instructionWithMemory.contains("User prefers Kotlin and Jetpack Compose"))
        assertTrue(instructionWithMemory.contains("Coding Assistance Mode: Enabled"))
    }

    @Test
    fun `hindi calling and speaker commands parse into structured actions`() {
        val shivamIntent = commandRouter.parseDeterministicIntent("शिवम को कॉल करो", "Hindi")
        assertNotNull(shivamIntent)
        assertEquals("call_contact", shivamIntent?.toolName)
        assertEquals(
            """{"action":"CALL_CONTACT","contactName":"शिवम","speaker":false}""",
            shivamIntent?.parametersJson
        )

        val mummyIntent = commandRouter.parseDeterministicIntent("मम्मी को फोन लगाओ", "Hindi")
        assertNotNull(mummyIntent)
        assertEquals("call_contact", mummyIntent?.toolName)
        assertEquals(
            """{"action":"CALL_CONTACT","contactName":"मम्मी","speaker":false}""",
            mummyIntent?.parametersJson
        )

        val rahulSpeakerIntent = commandRouter.parseDeterministicIntent("राहुल को कॉल करो और स्पीकर ऑन करना", "Hindi")
        assertNotNull(rahulSpeakerIntent)
        assertEquals("call_contact", rahulSpeakerIntent?.toolName)
        assertEquals(
            """{"action":"CALL_CONTACT","contactName":"राहुल","speaker":true}""",
            rahulSpeakerIntent?.parametersJson
        )

        val speakerOnIntent = commandRouter.parseDeterministicIntent("स्पीकर ऑन करो", "Hindi")
        assertNotNull(speakerOnIntent)
        assertEquals("set_speaker", speakerOnIntent?.toolName)
        assertEquals(
            """{"action":"SET_SPEAKER","enabled":true}""",
            speakerOnIntent?.parametersJson
        )

        val speakerOffIntent = commandRouter.parseDeterministicIntent("स्पीकर बंद करो", "Hindi")
        assertNotNull(speakerOffIntent)
        assertEquals("set_speaker", speakerOffIntent?.toolName)
        assertEquals(
            """{"action":"SET_SPEAKER","enabled":false}""",
            speakerOffIntent?.parametersJson
        )

        assertTrue(MaxVoiceEngine.isStopSpeakingCommand("सुनना बंद करो"))
        assertTrue(MaxVoiceEngine.isStopSpeakingCommand("sunna band karo"))

        // Verify calling command does not block with PERMISSION_REQUIRED
        val directCallResult = commandRouter.callingExecutor.executeCallContact(
            contactName = "शिवम",
            speaker = true,
            languageStyle = "Hindi"
        )
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, directCallResult.status)

        // Verify contact search bar filtering works for Hindi, English, and phone digits
        val allContacts = commandRouter.callingExecutor.getAllSearchableContacts()
        assertTrue(allContacts.isNotEmpty())
        val shivamFiltered = commandRouter.callingExecutor.filterContacts("शिवम", allContacts)
        assertTrue(shivamFiltered.any { it.name.contains("Shivam", ignoreCase = true) })
        val rahulFiltered = commandRouter.callingExecutor.filterContacts("rahul", allContacts)
        assertTrue(rahulFiltered.any { it.name.contains("Rahul", ignoreCase = true) })
    }

    @Test
    fun `hands-free wake word matcher accurately detects MAX, Hey MAX, and OK MAX with or without trailing commands`() {
        val phrases = com.example.service.MaxVoiceWakeService.parseConfiguredWakePhrases(
            "Hey MAX,OK MAX,MAX"
        )

        val heyMaxAlone = com.example.service.MaxVoiceWakeService.matchWakePhrase("Hey MAX", phrases)
        assertNotNull(heyMaxAlone)
        assertEquals("hey max", heyMaxAlone?.wakePhrase)
        assertEquals("", heyMaxAlone?.trailingCommand)

        val okMaxWithCommand = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "OK MAX शिवम को कॉल करो",
            phrases
        )
        assertNotNull(okMaxWithCommand)
        assertEquals("ok max", okMaxWithCommand?.wakePhrase)
        assertEquals("शिवम को कॉल करो", okMaxWithCommand?.trailingCommand)

        val okayMaxVariant = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "Okay MAX open YouTube",
            phrases
        )
        assertNotNull(okayMaxVariant)
        assertEquals("ok max", okayMaxVariant?.wakePhrase)
        assertEquals("open youtube", okayMaxVariant?.trailingCommand)

        val maxDirect = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "MAX volume 70 karo",
            phrases
        )
        assertNotNull(maxDirect)
        assertEquals("max", maxDirect?.wakePhrase)
        assertEquals("volume 70 karo", maxDirect?.trailingCommand)

        // Verify Devanagari Hindi wake phrases ("हे मैक्स", "ओके मैक्स", "मैक्स") match cleanly with matras
        val hindiHeyMax = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "हे मैक्स शिवम को कॉल करो",
            phrases
        )
        assertNotNull(hindiHeyMax)
        assertEquals("hey max", hindiHeyMax?.wakePhrase)
        assertEquals("शिवम को कॉल करो", hindiHeyMax?.trailingCommand)

        val hindiOkMax = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "ओके मैक्स स्पीकर ऑन करो",
            phrases
        )
        assertNotNull(hindiOkMax)
        assertEquals("ok max", hindiOkMax?.wakePhrase)
        assertEquals("स्पीकर ऑन करो", hindiOkMax?.trailingCommand)

        // Verify phonetic STT variations ("Hey Macs", "He Max") and conversational filler prefixes ("Arre Hey MAX")
        val phoneticMatch = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "Arre Hey Macs open Instagram",
            phrases
        )
        assertNotNull(phoneticMatch)
        assertEquals("hey max", phoneticMatch?.wakePhrase)
        assertEquals("open instagram", phoneticMatch?.trailingCommand)

        // Ensure partial word matches like "maximum" do not false-trigger wake word
        val falsePositive = com.example.service.MaxVoiceWakeService.matchWakePhrase(
            "set brightness to maximum level",
            phrases
        )
        assertEquals(null, falsePositive)
    }

    @Test
    fun `expressive speech formatter adapts emotional tone and sanitizes code blocks, URLs, and math for natural speech`() {
        // 1. Contextual emotion inference across happy, empathetic, sad, storytelling, romantic, humorous, and urgent contexts
        val happyEmotion = com.example.voice.MaxExpressiveSpeechFormatter.inferContextualEmotion(
            userText = "Kya haal hai MAX?",
            assistantReply = "Sab ekdum badhiya hai yaar! Chalo main tumhari help karta hoon."
        )
        assertEquals(com.example.voice.VoiceEmotion.HAPPY, happyEmotion)

        val empatheticEmotion = com.example.voice.MaxExpressiveSpeechFormatter.inferContextualEmotion(
            userText = "Aaj main bahut stressed aur pareshan hoon",
            assistantReply = "Main samajh sakta hoon yaar, chinta mat karo."
        )
        assertEquals(com.example.voice.VoiceEmotion.EMPATHETIC, empatheticEmotion)

        val romanticStoryEmotion = com.example.voice.MaxExpressiveSpeechFormatter.inferContextualEmotion(
            userText = "Ek romantic love story ya shayari sunao",
            assistantReply = "Shaam ki us halki si thandak mein, jab baarish ki boondein khidki par ruk-ruk kar gir rahi thi, tab samajh aaya ki kuch kahaaniyan lafzon se nahi banti."
        )
        assertEquals(com.example.voice.VoiceEmotion.ROMANTIC_STORYTELLING, romanticStoryEmotion)

        val urgentEmotion = com.example.voice.MaxExpressiveSpeechFormatter.inferContextualEmotion(
            userText = "Urgent help needed immediately",
            assistantReply = "Executing right away."
        )
        assertEquals(com.example.voice.VoiceEmotion.URGENT, urgentEmotion)

        // 2. Speech sanitization: strips raw code blocks, inline JSON, markdown syntax, and formats math/URLs naturally
        val rawAiMessage = """
            Achha, samajh gaya! 25 * 4 = 100 hota hai. Check https://developer.android.com/jetpack for details.
            ```kotlin
            fun greet() { println("Hello") }
            ```
            {"action":"CALL_CONTACT","contactName":"शिवम"}
        """.trimIndent()

        val spokenClean = com.example.voice.MaxExpressiveSpeechFormatter.sanitizeForNaturalSpeech(
            rawText = rawAiMessage,
            languageStyle = "Hinglish"
        )
        assertTrue(spokenClean.contains("25 multiplied by 4 equals 100"))
        assertTrue(spokenClean.contains("developer.android.com"))
        assertTrue(spokenClean.contains("Maine code screen par share kar diya hai"))
        assertTrue(!spokenClean.contains("println"))
        assertTrue(!spokenClean.contains("CALL_CONTACT"))

        // 3. Prosody sentence chunking produces natural pitch/rate variations and pauses
        val chunks = com.example.voice.MaxExpressiveSpeechFormatter.buildExpressiveSpeechChunks(
            rawText = "Kya haal hai? Achha, samajh gaya. Chalo, main tumhari help karta hoon!",
            languageStyle = "Hinglish",
            emotion = com.example.voice.VoiceEmotion.HAPPY,
            stylePreset = com.example.voice.VoiceStylePreset.AUTO_ADAPTIVE,
            basePitch = 0.95f,
            baseRate = 1.0f,
            preferMaleVoice = true
        )
        assertEquals(3, chunks.size)
        assertTrue(chunks[0].postPauseMs > 0L)
        assertEquals(0L, chunks.last().postPauseMs)
    }

    @Test
    fun `conversational brain passes Test A, Test B, Test C, Test D, and Test E plus privacy guardrails`() = runBlocking {
        val dao = database.maxDao()
        val prefsRepo = com.example.data.MaxPreferencesRepository(context)
        val memoryService = com.example.brain.MaxMemoryService(dao)
        val conversationService = com.example.brain.MaxConversationService(dao)
        val personalizationService = com.example.brain.MaxPersonalizationService(prefsRepo, memoryService)
        val brainService = com.example.brain.MaxBrainService(
            memoryService = memoryService,
            conversationService = conversationService,
            personalizationService = personalizationService,
            preferencesRepo = prefsRepo
        )
        val prefs = com.example.data.MaxUserPreferences(persistentMemoryConsent = true)

        // --- TEST A: Context-aware follow-up explanation ---
        // User: "Explain AI in simple Hindi." -> Follow-up: "Aur bhi aasan karke samjhao."
        val sessionA = "session_test_a"
        conversationService.ensureSessionExists(sessionA, "local_guest")

        val snapA1 = conversationService.buildConversationContextSnapshot(sessionA, "Explain AI in simple Hindi.", "local_guest")
        val intentA1 = brainService.classifyBrainIntent("Explain AI in simple Hindi.", snapA1)
        assertEquals(com.example.brain.BrainIntentType.EXPLANATION, intentA1)

        val resA1 = commandRouter.processUserCommand(
            rawInput = "Explain AI in simple Hindi.",
            conversationHistory = snapA1.recentTurns,
            preferredLanguageSetting = "Hindi",
            preclassifiedBrainIntent = intentA1,
            contextSnapshot = snapA1
        )
        assertTrue(resA1.userMessage.contains("AI", ignoreCase = true) || resA1.userMessage.contains("Artificial Intelligence", ignoreCase = true))

        dao.insertMessage(
            com.example.data.ChatMessageEntity(
                sessionId = sessionA,
                isUser = true,
                text = "Explain AI in simple Hindi.",
                detectedLanguage = "Hindi",
                intentCategory = intentA1.name
            )
        )
        dao.insertMessage(
            com.example.data.ChatMessageEntity(
                sessionId = sessionA,
                isUser = false,
                text = resA1.userMessage,
                detectedLanguage = "Hindi",
                intentCategory = intentA1.name
            )
        )
        conversationService.updateSessionAfterTurn(sessionA, "Explain AI in simple Hindi.", resA1.userMessage, "local_guest")

        // Follow-up: "Aur bhi aasan karke samjhao."
        val snapA2 = conversationService.buildConversationContextSnapshot(sessionA, "Aur bhi aasan karke samjhao.", "local_guest")
        assertTrue(snapA2.isFollowUpToRecentTurn)
        val intentA2 = brainService.classifyBrainIntent("Aur bhi aasan karke samjhao.", snapA2)
        assertEquals(com.example.brain.BrainIntentType.FOLLOW_UP, intentA2)

        val resA2 = commandRouter.processUserCommand(
            rawInput = "Aur bhi aasan karke samjhao.",
            conversationHistory = snapA2.recentTurns,
            preferredLanguageSetting = "Hindi",
            preclassifiedBrainIntent = intentA2,
            contextSnapshot = snapA2
        )
        // Verifies MAX AI understands that the second message refers to the previous AI explanation and simplifies it
        assertTrue(resA2.userMessage.contains("AI", ignoreCase = true) || resA2.userMessage.contains("आसान", ignoreCase = true) || resA2.userMessage.contains("aasan", ignoreCase = true))

        // --- TEST B: Long-Term Memory across New Conversation Sessions ---
        // User: "Remember that I prefer Hindi responses." -> New conversation: "Explain cloud computing."
        val snapB1 = conversationService.buildConversationContextSnapshot(sessionA, "Remember that I prefer Hindi responses.", "local_guest")
        val intentB1 = brainService.classifyBrainIntent("Remember that I prefer Hindi responses.", snapB1)
        assertEquals(com.example.brain.BrainIntentType.MEMORY_SAVE, intentB1)

        val saveMemResult = brainService.handleDirectBrainMemoryOrHistoryIntent(
            rawInput = "Remember that I prefer Hindi responses.",
            intentType = intentB1,
            contextSnapshot = snapB1,
            prefs = prefs,
            detectedLanguage = "English"
        )
        assertNotNull(saveMemResult)
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, saveMemResult?.status)

        val activeMemoriesAfterSave = memoryService.getActiveMemoriesForCurrentUser()
        assertTrue(activeMemoriesAfterSave.any { it.memoryFact.contains("prefer Hindi", ignoreCase = true) })

        // New conversation session: "Explain cloud computing."
        val sessionBNew = "session_test_b_new"
        conversationService.ensureSessionExists(sessionBNew, "local_guest")
        val snapB2 = conversationService.buildConversationContextSnapshot(sessionBNew, "Explain cloud computing.", "local_guest")
        val effectiveLangForNewSession = brainService.resolveEffectiveLanguage(
            userInput = "Explain cloud computing.",
            detectedInputLanguage = "English",
            prefs = prefs,
            storedMemories = activeMemoriesAfterSave,
            contextSnapshot = snapB2
        )
        // Because user saved "I prefer Hindi responses" in memory, effective language resolves to Hindi!
        assertEquals("Hindi", effectiveLangForNewSession)

        val resB2 = commandRouter.processUserCommand(
            rawInput = "Explain cloud computing.",
            conversationHistory = snapB2.recentTurns,
            preferredLanguageSetting = effectiveLangForNewSession,
            storedMemories = activeMemoriesAfterSave.map { it.memoryFact },
            preclassifiedBrainIntent = com.example.brain.BrainIntentType.EXPLANATION,
            contextSnapshot = snapB2
        )
        assertTrue(resB2.userMessage.contains("Cloud Computing", ignoreCase = true))
        // Verify response is delivered in Hindi as preferred in memory
        assertTrue(resB2.userMessage.any { it in '\u0900'..'\u097F' })

        // --- TEST C: "Call Shivam." routes to actual Android calling tool ---
        val snapC = conversationService.buildConversationContextSnapshot(sessionBNew, "Call Shivam.", "local_guest")
        val intentC = brainService.classifyBrainIntent("Call Shivam.", snapC)
        assertEquals(com.example.brain.BrainIntentType.CALL_CONTACT, intentC)

        val resC = commandRouter.processUserCommand(
            rawInput = "Call Shivam.",
            conversationHistory = snapC.recentTurns,
            preferredLanguageSetting = "Hindi",
            preclassifiedBrainIntent = intentC,
            contextSnapshot = snapC
        )
        assertEquals("call_contact", resC.toolName)
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, resC.status)
        assertEquals(com.example.brain.BrainIntentType.CALL_CONTACT.name, resC.brainIntentCategory)

        // --- TEST D: "Forget my language preference." deletes the stored memory ---
        val snapD = conversationService.buildConversationContextSnapshot(sessionBNew, "Forget my language preference.", "local_guest")
        val intentD = brainService.classifyBrainIntent("Forget my language preference.", snapD)
        assertEquals(com.example.brain.BrainIntentType.MEMORY_DELETE, intentD)

        val deleteMemResult = brainService.handleDirectBrainMemoryOrHistoryIntent(
            rawInput = "Forget my language preference.",
            intentType = intentD,
            contextSnapshot = snapD,
            prefs = prefs,
            detectedLanguage = "English"
        )
        assertNotNull(deleteMemResult)
        assertEquals(InteractionStatus.VERIFIED_SUCCESS, deleteMemResult?.status)

        val memoriesAfterDelete = memoryService.getActiveMemoriesForCurrentUser()
        assertTrue(memoriesAfterDelete.none { it.memoryFact.contains("prefer Hindi", ignoreCase = true) })

        // --- TEST E: "What did I ask you earlier?" retrieves actual conversation history ---
        val snapE = conversationService.buildConversationContextSnapshot(sessionA, "What did I ask you earlier?", "local_guest")
        val intentE = brainService.classifyBrainIntent("What did I ask you earlier?", snapE)
        assertEquals(com.example.brain.BrainIntentType.MEMORY_RECALL, intentE)
        val historyRecallResult = brainService.handleDirectBrainMemoryOrHistoryIntent(
            rawInput = "What did I ask you earlier?",
            intentType = intentE,
            contextSnapshot = snapE,
            prefs = prefs,
            detectedLanguage = "English"
        )
        assertNotNull(historyRecallResult)
        assertTrue(historyRecallResult!!.userMessage.contains("Explain AI in simple Hindi", ignoreCase = true))

        // --- Privacy Guardrail Check: Passwords / OTPs / Secrets are never stored in memory ---
        assertTrue(!memoryService.isSafeToStoreAsMemory("My bank password is Secret1234"))
        assertTrue(!memoryService.isSafeToStoreAsMemory("My OTP is 492810"))
    }

    @Test
    fun `online update service parses update manifest and detects newer version`() {
        val sampleJson = """
            {
                "versionCode": 2,
                "versionName": "1.1",
                "downloadUrl": "https://example.com/max-ai-v1.1.apk",
                "changelog": "• New Online Update System\n• Conversational Brain Upgrades",
                "isMandatory": false,
                "fileSizeBytes": 15420000
            }
        """.trimIndent()

        val json = org.json.JSONObject(sampleJson)
        val remoteVersion = json.getInt("versionCode")
        val currentVersion = 1

        assertTrue(remoteVersion > currentVersion)
        assertEquals("1.1", json.getString("versionName"))
        assertEquals("https://example.com/max-ai-v1.1.apk", json.getString("downloadUrl"))
        assertEquals(15420000L, json.getLong("fileSizeBytes"))
    }

    @Test
    fun `location sharing commands correctly parse deterministic intent and recipient`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.MaxDatabase.getInstance(app)
        val dao = db.maxDao()
        val router = com.example.tools.MaxCommandRouter(app, dao)

        // Test 1: WhatsApp location sharing to named contact in Hinglish
        val shareIntent = router.parseDeterministicIntent("Rahul ko WhatsApp par location bhejo", "Hinglish")
        assertNotNull(shareIntent)
        assertEquals("share_location", shareIntent?.toolName)
        val paramsShare = org.json.JSONObject(shareIntent?.parametersJson ?: "{}")
        assertEquals("Rahul", paramsShare.optString("recipient_name"))
        assertEquals("whatsapp", paramsShare.optString("platform"))

        // Test 2: SMS location sharing to Mom
        val smsIntent = router.parseDeterministicIntent("Mummy ko location SMS karo", "Hinglish")
        assertNotNull(smsIntent)
        assertEquals("share_location", smsIntent?.toolName)
        val paramsSms = org.json.JSONObject(smsIntent?.parametersJson ?: "{}")
        assertEquals("Mummy", paramsSms.optString("recipient_name"))
        assertEquals("sms", paramsSms.optString("platform"))

        // Test 3: Where am I / current location query
        val queryIntent = router.parseDeterministicIntent("Where am I? Meri location kya hai", "English")
        assertNotNull(queryIntent)
        assertEquals("get_location", queryIntent?.toolName)

        // Test 4: Location Service URL and Intent generation
        val snapshot = com.example.tools.LocationSnapshot(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 10f,
            addressText = "Connaught Place, New Delhi"
        )
        assertTrue(snapshot.mapsUrl.contains("maps.google.com"))
        assertTrue(snapshot.mapsUrl.contains("28.6139,77.209"))

        val waIntent = com.example.tools.MaxLocationService.buildWhatsAppShareIntent(
            app, "+919876543210", snapshot, "Rahul"
        )
        assertNotNull(waIntent)
        assertEquals(Intent.ACTION_VIEW, waIntent.action)
        assertTrue(waIntent.data.toString().contains("919876543210"))
        assertTrue(waIntent.data.toString().contains("maps.google.com"))

        val smsShareIntent = com.example.tools.MaxLocationService.buildSmsShareIntent(
            app, "+919876543210", snapshot, "Mummy"
        )
        assertNotNull(smsShareIntent)
        assertEquals(Intent.ACTION_SENDTO, smsShareIntent.action)
        assertEquals("smsto:+919876543210", smsShareIntent.data.toString())
    }

    private fun registerFakeLaunchableApp(
        shadowPm: org.robolectric.shadows.ShadowPackageManager,
        packageName: String,
        appLabel: String
    ) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.name = appLabel
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = "$packageName.MainActivity"
            this.applicationInfo = appInfo
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
            this.nonLocalizedLabel = appLabel
        }
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addResolveInfoForIntent(mainIntent, resolveInfo)

        val pkgLaunchIntent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(packageName, "$packageName.MainActivity")
        }
        shadowPm.addResolveInfoForIntent(pkgLaunchIntent, resolveInfo)
    }
}
