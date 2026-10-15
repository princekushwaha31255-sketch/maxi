package com.example.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brain.BrainIntentType
import com.example.brain.BrainService
import com.example.brain.MaxBrainService
import com.example.brain.MaxConversationService
import com.example.brain.MaxMemoryService
import com.example.brain.MaxPersonalizationService
import com.example.data.ActionLogEntity
import com.example.data.AppCandidate
import com.example.data.AssistantState
import com.example.data.AttachmentPayload
import com.example.data.ChatMessageEntity
import com.example.data.ConversationSessionEntity
import com.example.data.DisambiguationPrompt
import com.example.data.InteractionStatus
import com.example.data.MaxCloudRepository
import com.example.data.MaxDatabase
import com.example.data.MaxPreferencesRepository
import com.example.data.MaxUserPreferences
import com.example.data.MemoryItemEntity
import com.example.data.PendingConfirmation
import com.example.data.ScheduledItemEntity
import com.example.data.ToolExecutionResult
import com.example.data.TrustedRuleEntity
import com.example.service.AppUpdateInfo
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationListenerService
import com.example.service.MaxUpdateService
import com.example.service.MaxVoiceWakeService
import com.example.service.UpdateUiState
import java.io.File
import com.example.tools.MaxCommandRouter
import com.example.tools.MaxFileAttachmentProcessor
import com.example.voice.MaxVoiceEngine
import com.example.voice.VoiceInfoItem
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatusSnapshot(
    val microphoneGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val notificationListenerEnabled: Boolean = false,
    val accessibilityServiceEnabled: Boolean = false,
    val wakeWordServiceOperational: Boolean = false,
    val locationGranted: Boolean = false
)

class MaxViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val database = MaxDatabase.getInstance(appContext)
    private val dao = database.maxDao()
    val preferencesRepo = MaxPreferencesRepository(appContext)
    private val cloudRepository: MaxCloudRepository? = try {
        MaxCloudRepository(appContext)
    } catch (_: Exception) {
        null
    }
    val memoryService = MaxMemoryService(dao, cloudRepository)
    val conversationService = MaxConversationService(dao)
    val personalizationService = MaxPersonalizationService(preferencesRepo, memoryService)
    val brainService = BrainService(
        memoryService = memoryService,
        conversationService = conversationService,
        personalizationService = personalizationService,
        preferencesRepo = preferencesRepo
    )
    val commandRouter = MaxCommandRouter(
        context = appContext,
        maxDao = dao,
        memoryService = memoryService
    )
    val voiceEngine = MaxVoiceEngine(appContext)

    private val _activeSessionId = MutableStateFlow("default_session")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    val conversationSessions: StateFlow<List<ConversationSessionEntity>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { sessionId -> dao.getMessagesBySession(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actionLogs: StateFlow<List<ActionLogEntity>> = dao.getAllActionLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedRules: StateFlow<List<TrustedRuleEntity>> = dao.getAllTrustedRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledItems: StateFlow<List<ScheduledItemEntity>> = dao.getAllScheduledItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userMemories: StateFlow<List<MemoryItemEntity>> = dao.getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<MaxUserPreferences> = preferencesRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaxUserPreferences())

    private val _pendingAttachment = MutableStateFlow<AttachmentPayload?>(null)
    val pendingAttachment: StateFlow<AttachmentPayload?> = _pendingAttachment.asStateFlow()

    val callingHudState = commandRouter.callingExecutor.callingHudState

    private val _isProcessing = MutableStateFlow(false)
    private val _wasCancelled = MutableStateFlow(false)

    val assistantState: StateFlow<AssistantState> = combine(
        voiceEngine.isListening,
        _isProcessing,
        voiceEngine.isSpeaking,
        _wasCancelled
    ) { listening, processing, speaking, cancelled ->
        when {
            listening -> AssistantState.LISTENING
            processing -> AssistantState.PROCESSING
            speaking -> AssistantState.SPEAKING
            cancelled -> AssistantState.CANCELLED
            else -> AssistantState.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantState.IDLE)

    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val rmsDbLevel: StateFlow<Float> = voiceEngine.rmsDbLevel
    val waveformHistory: StateFlow<List<Float>> = voiceEngine.waveformHistory
    val recognitionStage: StateFlow<com.example.voice.SpeechRecognitionStage> = voiceEngine.recognitionStage
    val recognizedCandidates: StateFlow<List<com.example.voice.RecognizedCandidate>> = voiceEngine.recognizedCandidates
    val isSpeechRecognizerAvailable: StateFlow<Boolean> = voiceEngine.isSpeechRecognizerAvailable
    val availableVoices: StateFlow<List<VoiceInfoItem>> = voiceEngine.availableVoices
    val voiceError: StateFlow<String?> = voiceEngine.voiceError
    val activeVoiceEmotion: StateFlow<com.example.voice.VoiceEmotion> = voiceEngine.activeVoiceEmotion
    val activeVoiceEngineLabel: StateFlow<String> = voiceEngine.activeVoiceEngineLabel

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _disambiguationPrompt = MutableStateFlow<DisambiguationPrompt?>(null)
    val disambiguationPrompt: StateFlow<DisambiguationPrompt?> = _disambiguationPrompt.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppCandidate>>(emptyList())
    val installedApps: StateFlow<List<AppCandidate>> = _installedApps.asStateFlow()

    private val _allContacts = MutableStateFlow<List<com.example.data.ContactMatch>>(emptyList())
    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()

    val filteredContacts: StateFlow<List<com.example.data.ContactMatch>> = combine(
        _contactSearchQuery,
        _allContacts
    ) { query, contacts ->
        commandRouter.callingExecutor.filterContacts(query, contacts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateContactSearchQuery(query: String) {
        _contactSearchQuery.value = query
    }

    private val _permissionSnapshot = MutableStateFlow(PermissionStatusSnapshot())
    val permissionSnapshot: StateFlow<PermissionStatusSnapshot> = _permissionSnapshot.asStateFlow()

    private val _permissionRationaleDialog = MutableStateFlow<PermissionRationaleRequest?>(null)
    val permissionRationaleDialog: StateFlow<PermissionRationaleRequest?> =
        _permissionRationaleDialog.asStateFlow()

    val wakeServiceActive: StateFlow<Boolean> = MaxVoiceWakeService.isWakeServiceActive
    val wakeServiceStatusMessage: StateFlow<String> = MaxVoiceWakeService.wakeServiceStatusMessage
    val wakeLoopPaused: StateFlow<Boolean> = MaxVoiceWakeService.isWakeLoopPaused
    val wakePartialTranscript: StateFlow<String> = MaxVoiceWakeService.wakePartialTranscript

    private val _lastActivatedWakePhrase = MutableStateFlow<String?>(null)
    val lastActivatedWakePhrase: StateFlow<String?> = _lastActivatedWakePhrase.asStateFlow()

    private val _updateUiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private var activeCommandJob: Job? = null
    private var pendingWakeWordEnableAfterPermission = false

    data class PermissionRationaleRequest(
        val permissionKey: String,
        val title: String,
        val explanation: String,
        val isSystemSettingsIntent: Boolean = false
    )

    init {
        refreshPermissionsAndApps()
        seedWelcomeMessageIfNeeded()
        setupVoiceEngineWakeCoordination()
        observeWakeEvents()
        restoreWakeWordServiceIfEnabled()
        checkStartupUpdatesIfEnabled()
    }

    private fun checkStartupUpdatesIfEnabled() {
        viewModelScope.launch {
            val savedPrefs = preferencesRepo.preferencesFlow.first()
            if (savedPrefs.autoCheckUpdatesOnStartup) {
                checkForUpdates(isManual = false)
            }
        }
    }

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            _updateUiState.value = UpdateUiState.Checking
            val prefs = preferencesRepo.preferencesFlow.first()
            val result = MaxUpdateService.checkRemoteUpdateManifest(
                manifestUrl = prefs.updateManifestUrl
            )
            preferencesRepo.recordUpdateCheckTimestamp()
            result.onSuccess { updateInfo ->
                if (updateInfo != null) {
                    _updateUiState.value = UpdateUiState.UpdateAvailable(updateInfo)
                } else {
                    if (isManual) {
                        _updateUiState.value = UpdateUiState.UpToDate()
                    } else {
                        _updateUiState.value = UpdateUiState.Idle
                    }
                }
            }.onFailure { err ->
                if (isManual) {
                    _updateUiState.value = UpdateUiState.Error(
                        err.message ?: "Failed to check for updates"
                    )
                } else {
                    _updateUiState.value = UpdateUiState.Idle
                }
            }
        }
    }

    fun downloadAndInstallUpdate(info: AppUpdateInfo) {
        viewModelScope.launch {
            _updateUiState.value = UpdateUiState.Downloading(
                info = info,
                progressPercent = 0,
                bytesDownloaded = 0L,
                totalBytes = info.fileSizeBytes
            )
            val downloadResult = MaxUpdateService.downloadApk(
                context = appContext,
                updateInfo = info,
                onProgress = { percent, downloaded, total ->
                    _updateUiState.value = UpdateUiState.Downloading(
                        info = info,
                        progressPercent = percent,
                        bytesDownloaded = downloaded,
                        totalBytes = total
                    )
                }
            )
            downloadResult.onSuccess { apkFile ->
                _updateUiState.value = UpdateUiState.ReadyToInstall(info, apkFile)
                MaxUpdateService.launchApkInstallation(appContext, apkFile)
            }.onFailure { err ->
                _updateUiState.value = UpdateUiState.Error(
                    err.message ?: "APK download failed. Please check internet connection."
                )
            }
        }
    }

    fun triggerInstallAgain(apkFile: File) {
        MaxUpdateService.launchApkInstallation(appContext, apkFile)
    }

    fun dismissUpdateDialog() {
        _updateUiState.value = UpdateUiState.Idle
    }

    private fun restoreWakeWordServiceIfEnabled() {
        viewModelScope.launch {
            val savedPrefs = preferencesRepo.preferencesFlow.first()
            if (savedPrefs.wakeWordEnabled && !MaxVoiceWakeService.isWakeServiceActive.value) {
                val hasMic = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    toggleWakeWordService(true)
                }
            }
        }
    }

    private fun setupVoiceEngineWakeCoordination() {
        // When TTS finishes speaking the AI response (or if TTS is disabled), automatically resume wake-word detection!
        voiceEngine.setOnTtsCompletedListener {
            if (!voiceEngine.isListening.value && !_isProcessing.value) {
                _lastActivatedWakePhrase.value = null
                if (userPreferences.value.wakeWordEnabled || MaxVoiceWakeService.isWakeServiceActive.value) {
                    MaxVoiceWakeService.resumeWakeLoopIfActive(appContext)
                }
            }
        }
        // When SpeechRecognizer ends with timeout/error and no command is processing, resume wake-word detection!
        voiceEngine.setOnSpeechSessionEndedListener {
            if (!_isProcessing.value && !voiceEngine.isSpeaking.value) {
                _lastActivatedWakePhrase.value = null
                if (userPreferences.value.wakeWordEnabled || MaxVoiceWakeService.isWakeServiceActive.value) {
                    MaxVoiceWakeService.resumeWakeLoopIfActive(appContext)
                }
            }
        }
    }

    private fun seedWelcomeMessageIfNeeded() {
        viewModelScope.launch {
            val currentSessionId = _activeSessionId.value
            conversationService.ensureSessionExists(currentSessionId, memoryService.currentAccountKey())
            val existing = dao.getMessagesBySessionSnapshot(currentSessionId)
            if (existing.isEmpty()) {
                dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = currentSessionId,
                        isUser = false,
                        text = "Namaste Boss! Main MAXI hoon (MAX AI Conversational Brain) — aapka context-aware multilingual AI & Android assistant.\n\nAap bolkar, type karke, ya Image/PDF/Code file attach karke baat kar sakte hain:\n• Context-Aware Chat, Follow-Ups (\"Aur bhi aasan karke samjhao\"), Long-Term Memory (\"Remember that I prefer Hindi\")\n• \"Shivam ko call karo\" ya \"Instagram kholo\"\n• \"Volume 70 karo\" ya \"7:00 AM ka alarm lagao\"\n• \"Stop\" ya \"Chup ho jao\" bolkar meri aawaz turant rok sakte hain.",
                        detectedLanguage = "Hinglish",
                        intentCategory = BrainIntentType.GENERAL_CHAT.name,
                        toolInvoked = "system_init",
                        status = InteractionStatus.INFO_RESPONSE.name,
                        verificationEvidence = "MAX AI Conversational Brain, Context Engine, Long-Term Memory, SpeechRecognizer, TextToSpeech, and Command Router initialized."
                    )
                )
            }
        }
    }

    private fun observeWakeEvents() {
        viewModelScope.launch {
            MaxVoiceWakeService.lastWakeDetectedEvent.collect { event ->
                if (event != null) {
                    MaxVoiceWakeService.consumeWakeEvent()
                    _lastActivatedWakePhrase.value = event.wakePhrase.uppercase()
                    MaxVoiceWakeService.pauseWakeLoopIfActive(appContext)
                    if (event.trailingCommand.isNotBlank()) {
                        submitUserInput(event.trailingCommand)
                    } else {
                        startVoiceListening(triggeredByWakePhrase = event.wakePhrase.uppercase())
                    }
                }
            }
        }
    }

    fun refreshPermissionsSnapshotOnly() {
        val mic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val contacts = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notifListener = MaxNotificationListenerService.isNotificationAccessEnabled(appContext)
        val a11y = MaxAccessibilityService.isEnabledInSettings(appContext)
        val wakeActive = MaxVoiceWakeService.isWakeServiceActive.value
        val locGranted = com.example.tools.MaxLocationService.hasLocationPermission(appContext)

        _permissionSnapshot.value = PermissionStatusSnapshot(
            microphoneGranted = mic,
            contactsGranted = contacts,
            postNotificationsGranted = postNotif,
            notificationListenerEnabled = notifListener,
            accessibilityServiceEnabled = a11y,
            wakeWordServiceOperational = wakeActive,
            locationGranted = locGranted
        )
    }

    fun refreshPermissionsAndApps(forceReloadAppsAndContacts: Boolean = false) {
        refreshPermissionsSnapshotOnly()

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (forceReloadAppsAndContacts || _installedApps.value.isEmpty()) {
                val apps = commandRouter.appLaunchManager.getInstalledLaunchableApps()
                _installedApps.value = apps
            }
            if (forceReloadAppsAndContacts || _allContacts.value.isEmpty()) {
                val contacts = commandRouter.callingExecutor.getAllSearchableContacts()
                _allContacts.value = contacts
            }
        }
    }

    fun attachUri(uri: Uri) {
        viewModelScope.launch {
            val payload = MaxFileAttachmentProcessor.processUri(appContext, uri)
            _pendingAttachment.value = payload
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun addManualMemoryFact(fact: String, category: String = "User Preference") {
        val clean = fact.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            memoryService.saveOrUpdateMemory(
                factText = clean,
                explicitCategory = category,
                sourceType = "Manual"
            )
        }
    }

    fun editMemoryFact(id: Long, updatedFact: String, category: String) {
        val clean = updatedFact.trim()
        if (clean.isEmpty() || !memoryService.isSafeToStoreAsMemory(clean)) return
        viewModelScope.launch {
            dao.updateMemory(id = id, updatedFact = clean, category = category)
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch {
            dao.deleteMemory(id)
        }
    }

    fun clearAllMemoryFacts() {
        viewModelScope.launch {
            dao.clearAllMemories()
        }
    }

    fun submitMessageFeedback(message: ChatMessageEntity, feedbackType: String) {
        viewModelScope.launch {
            val newFeedback = if (message.userFeedback == feedbackType) null else feedbackType
            dao.updateMessageFeedback(message.id, newFeedback)
            if (newFeedback != null) {
                personalizationService.recordResponseFeedback(
                    messageText = message.text,
                    feedbackType = newFeedback,
                    prefs = userPreferences.value
                )
            }
        }
    }

    fun startNewConversationSession() {
        viewModelScope.launch {
            val newSessionId = "session_${System.currentTimeMillis()}"
            _activeSessionId.value = newSessionId
            preferencesRepo.updateActiveSessionId(newSessionId)
            conversationService.ensureSessionExists(newSessionId, memoryService.currentAccountKey())
            seedWelcomeMessageIfNeeded()
        }
    }

    fun switchConversationSession(sessionId: String) {
        viewModelScope.launch {
            _activeSessionId.value = sessionId
            preferencesRepo.updateActiveSessionId(sessionId)
            conversationService.ensureSessionExists(sessionId, memoryService.currentAccountKey())
        }
    }

    fun deleteConversationSession(sessionId: String) {
        viewModelScope.launch {
            dao.clearSessionMessages(sessionId)
            dao.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                val remainingSessions = dao.getAllSessionsSnapshot()
                val nextSession = remainingSessions.firstOrNull { it.sessionId != sessionId }
                if (nextSession != null) {
                    _activeSessionId.value = nextSession.sessionId
                    preferencesRepo.updateActiveSessionId(nextSession.sessionId)
                } else {
                    _activeSessionId.value = "default_session"
                    preferencesRepo.updateActiveSessionId("default_session")
                    seedWelcomeMessageIfNeeded()
                }
            }
        }
    }

    fun deleteAllConversationSessions() {
        viewModelScope.launch {
            dao.clearAllMessages()
            dao.clearAllSessions()
            _activeSessionId.value = "default_session"
            preferencesRepo.updateActiveSessionId("default_session")
            seedWelcomeMessageIfNeeded()
        }
    }

    fun submitUserInput(rawText: String) {
        val trimmed = rawText.trim()
        val currentAttachment = _pendingAttachment.value
        if (trimmed.isEmpty() && currentAttachment == null) {
            if (userPreferences.value.wakeWordEnabled || MaxVoiceWakeService.isWakeServiceActive.value) {
                MaxVoiceWakeService.resumeWakeLoopIfActive(appContext)
            }
            return
        }

        // Section 3.2: Immediate Stop-Speaking / Cancellation commands ("Stop", "Chup ho jao", "Cancel")
        if (currentAttachment == null && MaxVoiceEngine.isStopSpeakingCommand(trimmed)) {
            stopOrCancelEverything(triggeredByCommand = trimmed)
            return
        }

        _pendingAttachment.value = null
        _wasCancelled.value = false
        voiceEngine.stopSpeaking()

        activeCommandJob?.cancel()
        activeCommandJob = viewModelScope.launch {
            val currentSessionId = _activeSessionId.value
            val accountKey = memoryService.currentAccountKey()
            val prefs = preferencesRepo.preferencesFlow.first()

            // Step 1 & 2: Build Conversation Context Snapshot (prior history + rolling summary + follow-up detection) BEFORE inserting new message
            val contextSnapshot = conversationService.buildConversationContextSnapshot(
                sessionId = currentSessionId,
                currentUserMessage = trimmed,
                accountKey = accountKey
            )

            // Step 3: Retrieve active long-term memories for current user account
            val activeMemories = memoryService.getActiveMemoriesForCurrentUser()

            // Step 4: Observe & learn from user preferences / corrections
            personalizationService.observeAndLearnFromUserInput(trimmed, prefs)

            // Step 5: Classify Brain Intent across the 15 structured categories
            val brainIntent = brainService.classifyBrainIntent(
                rawInput = trimmed,
                contextSnapshot = contextSnapshot,
                attachment = currentAttachment
            )

            // Resolve effective response language (explicit request -> follow-up continuity -> stored memory preference -> settings -> input script)
            val detectedInputStyle = commandRouter.detectLanguageStyle(
                trimmed.ifBlank { "Analyze ${currentAttachment?.fileName}" },
                "Auto (Match User)"
            )
            val effectiveLanguage = brainService.resolveEffectiveLanguage(
                userInput = trimmed,
                detectedInputLanguage = detectedInputStyle,
                prefs = prefs,
                storedMemories = activeMemories,
                contextSnapshot = contextSnapshot
            )

            val displayUserText = if (currentAttachment != null) {
                val prefix = "📎 ${currentAttachment.fileName} (${currentAttachment.mimeType})"
                if (trimmed.isNotBlank()) "$prefix\n$trimmed" else "$prefix\nAnalyze this file."
            } else {
                trimmed
            }

            // Save user message in active session
            dao.insertMessage(
                ChatMessageEntity(
                    sessionId = currentSessionId,
                    isUser = true,
                    text = displayUserText,
                    detectedLanguage = effectiveLanguage,
                    intentCategory = brainIntent.name
                )
            )
            syncChatMessageToCloudIfSignedIn(
                isUser = true,
                text = displayUserText,
                detectedLanguage = effectiveLanguage,
                toolInvoked = if (currentAttachment != null) "multimodal_attachment" else "",
                status = InteractionStatus.INFO_RESPONSE.name,
                verificationEvidence = currentAttachment?.fileName.orEmpty()
            )

            _isProcessing.value = true
            try {
                // Step 5b: Check if Brain handles direct memory/history intents (MEMORY_SAVE, MEMORY_DELETE, MEMORY_RECALL)
                val directBrainResult = if (currentAttachment == null) {
                    brainService.handleDirectBrainMemoryOrHistoryIntent(
                        rawInput = trimmed,
                        intentType = brainIntent,
                        contextSnapshot = contextSnapshot,
                        prefs = prefs,
                        detectedLanguage = effectiveLanguage
                    )
                } else {
                    null
                }

                val result = if (directBrainResult != null) {
                    commandRouter.logAction(directBrainResult)
                    directBrainResult
                } else {
                    val refreshedMemories = memoryService.getActiveMemoriesForCurrentUser()
                    val personalizationDirective = personalizationService.buildPersonalizationPromptDirective(
                        prefs = prefs,
                        memories = refreshedMemories,
                        effectiveLanguage = effectiveLanguage
                    )
                    commandRouter.processUserCommand(
                        rawInput = trimmed,
                        conversationHistory = contextSnapshot.recentTurns,
                        preferredLanguageSetting = effectiveLanguage,
                        attachment = currentAttachment,
                        customPersonality = prefs.assistantPersonality,
                        persistentMemoryConsent = prefs.persistentMemoryConsent,
                        storedMemories = refreshedMemories.map { "[${it.category}] ${it.memoryFact}" },
                        webSearchGroundingEnabled = prefs.webSearchGroundingEnabled,
                        codingAssistantMode = prefs.codingAssistantMode,
                        voiceStylePreset = prefs.voiceStylePreset,
                        personalizationDirective = personalizationDirective,
                        preclassifiedBrainIntent = brainIntent,
                        contextSnapshot = contextSnapshot
                    )
                }

                _isProcessing.value = false

                // Step 6 & 7: Save assistant response and update conversation session metadata
                handleToolExecutionResult(result, effectiveLanguage, currentSessionId)
                conversationService.updateSessionAfterTurn(
                    sessionId = currentSessionId,
                    userMessage = displayUserText,
                    assistantReply = result.userMessage,
                    accountKey = accountKey
                )
            } catch (e: Exception) {
                _isProcessing.value = false
                val fallbackResult = ToolExecutionResult(
                    toolName = "error_fallback",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = "Boss, processing ke dauran ek dikkat aayi (${e.localizedMessage ?: "Unknown error"}). Kripya dubara boliye ya type kijiye.",
                    spokenSummary = "Boss, request poori karne mein dikkat aayi. Kripya dubara koshish karein.",
                    verificationEvidence = "Exception in submitUserInput: ${e.message}"
                )
                handleToolExecutionResult(fallbackResult, effectiveLanguage, currentSessionId)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun syncChatMessageToCloudIfSignedIn(
        isUser: Boolean,
        text: String,
        detectedLanguage: String,
        toolInvoked: String,
        status: String,
        verificationEvidence: String
    ) {
        viewModelScope.launch {
            try {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    cloudRepository?.createChatMessage(
                        isUser = isUser,
                        text = text,
                        detectedLanguage = detectedLanguage,
                        toolInvoked = toolInvoked,
                        status = status,
                        verificationEvidence = verificationEvidence
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun syncActionLogToCloudIfSignedIn(result: ToolExecutionResult) {
        if (result.toolName == "chat_response") return
        viewModelScope.launch {
            try {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    cloudRepository?.createActionLog(
                        toolName = result.toolName,
                        sanitizedSummary = result.userMessage.take(300),
                        status = result.status.name,
                        verificationDetails = result.verificationEvidence
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun handleToolExecutionResult(
        result: ToolExecutionResult,
        detectedStyle: String,
        sessionId: String = _activeSessionId.value
    ) {
        _pendingConfirmation.value = result.pendingConfirmation
        _disambiguationPrompt.value = result.disambiguationPrompt

        // Check if missing permission requires showing the setup rationale prompt
        // (Never prompt permission modal on call_contact commands — execute direct call immediately)
        if (result.status == InteractionStatus.PERMISSION_REQUIRED &&
            result.requiredPermission != null &&
            result.toolName != "call_contact"
        ) {
            promptPermissionRationaleForKey(result.requiredPermission)
        }

        dao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                isUser = false,
                text = result.userMessage,
                detectedLanguage = detectedStyle,
                intentCategory = result.brainIntentCategory,
                toolInvoked = result.toolName,
                status = result.status.name,
                verificationEvidence = result.verificationEvidence
            )
        )
        syncChatMessageToCloudIfSignedIn(
            isUser = false,
            text = result.userMessage,
            detectedLanguage = detectedStyle,
            toolInvoked = result.toolName,
            status = result.status.name,
            verificationEvidence = result.verificationEvidence
        )
        syncActionLogToCloudIfSignedIn(result)

        val prefs = userPreferences.value
        val spokenText = result.spokenSummary?.takeIf { it.isNotBlank() } ?: result.userMessage
        val emotion = com.example.voice.VoiceEmotion.fromId(result.emotionalTone)
        voiceEngine.speak(
            text = spokenText,
            prefs = prefs,
            detectedLanguage = detectedStyle,
            emotion = emotion
        )
        refreshPermissionsSnapshotOnly()
    }

    fun speakMessageOnDemand(message: ChatMessageEntity) {
        val prefs = userPreferences.value
        val emotion = com.example.voice.MaxExpressiveSpeechFormatter.inferContextualEmotion(
            userText = "",
            assistantReply = message.text,
            stylePreset = com.example.voice.VoiceStylePreset.fromId(prefs.voiceStylePreset)
        )
        voiceEngine.speak(
            text = message.text,
            prefs = prefs,
            detectedLanguage = message.detectedLanguage,
            emotion = emotion,
            forceSpeakEvenIfMuted = true
        )
    }

    fun startVoiceListening(triggeredByWakePhrase: String? = null) {
        _wasCancelled.value = false
        if (triggeredByWakePhrase != null) {
            _lastActivatedWakePhrase.value = triggeredByWakePhrase
        }
        refreshPermissionsAndApps()
        if (!_permissionSnapshot.value.microphoneGranted) {
            promptPermissionRationaleForKey(Manifest.permission.RECORD_AUDIO)
            return
        }
        // Pause background wake recognizer before starting foreground command recognizer to prevent ERROR_RECOGNIZER_BUSY
        val wasWakeActive = MaxVoiceWakeService.isWakeServiceActive.value && !MaxVoiceWakeService.isWakeLoopPaused.value
        MaxVoiceWakeService.pauseWakeLoopIfActive(appContext)
        val locale = userPreferences.value.speechRecognitionLocale
        val handoffDelayMs = if (triggeredByWakePhrase != null || wasWakeActive) 220L else 0L
        voiceEngine.startListening(
            localeTag = locale,
            startDelayMs = handoffDelayMs
        ) { finalTranscript ->
            // Strip leading wake phrase if the user spoke "Hey MAX <command>" into the active listener
            val matchedWake = MaxVoiceWakeService.matchWakePhrase(
                finalTranscript,
                MaxVoiceWakeService.parseConfiguredWakePhrases(userPreferences.value.wakePhrasesCsv)
            )
            if (matchedWake != null && matchedWake.trailingCommand.isNotBlank()) {
                _lastActivatedWakePhrase.value = matchedWake.wakePhrase.uppercase()
                submitUserInput(matchedWake.trailingCommand)
            } else {
                submitUserInput(finalTranscript)
            }
        }
    }

    fun finishVoiceListening() {
        voiceEngine.finishListeningAndProcess()
    }

    fun handleActivitySpeechResult(matches: List<String>, scores: FloatArray? = null) {
        voiceEngine.onExternalSpeechResult(matches, scores) { finalTranscript ->
            submitUserInput(finalTranscript)
        }
    }

    fun switchSpeechRecognitionLocale(localeTag: String) {
        viewModelScope.launch {
            preferencesRepo.updateSpeechLocale(localeTag)
            if (voiceEngine.isListening.value) {
                voiceEngine.startListening(localeTag = localeTag) { finalTranscript ->
                    submitUserInput(finalTranscript)
                }
            } else if (MaxVoiceWakeService.isWakeServiceActive.value) {
                toggleWakeWordService(true)
            }
        }
    }

    fun stopOrCancelEverything(triggeredByCommand: String? = null) {
        activeCommandJob?.cancel()
        _isProcessing.value = false
        _lastActivatedWakePhrase.value = null
        voiceEngine.cancelAll()
        _pendingConfirmation.value = null
        _disambiguationPrompt.value = null
        _wasCancelled.value = true
        if (userPreferences.value.wakeWordEnabled || MaxVoiceWakeService.isWakeServiceActive.value) {
            MaxVoiceWakeService.resumeWakeLoopIfActive(appContext)
        }

        if (triggeredByCommand != null) {
            viewModelScope.launch {
                val currentSessionId = _activeSessionId.value
                val style = commandRouter.detectLanguageStyle(
                    triggeredByCommand,
                    userPreferences.value.preferredLanguageStyle
                )
                dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = currentSessionId,
                        isUser = true,
                        text = triggeredByCommand,
                        detectedLanguage = style,
                        intentCategory = BrainIntentType.VOICE_COMMAND.name
                    )
                )
                val ack = when (style) {
                    "Hindi" -> "जी बॉस, मैंने बोलना और वर्तमान कार्य रोक दिया है।"
                    "English" -> "Stopped speaking and cancelled the active operation."
                    else -> "Ji boss, maine bolna aur active operation stop kar diya hai."
                }
                dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = currentSessionId,
                        isUser = false,
                        text = ack,
                        detectedLanguage = style,
                        intentCategory = BrainIntentType.VOICE_COMMAND.name,
                        toolInvoked = "stop_speaking",
                        status = InteractionStatus.CANCELLED.name,
                        verificationEvidence = "TextToSpeech.stop() and SpeechRecognizer.cancel() verified"
                    )
                )
            }
        }
    }

    fun confirmPendingAction(saveTrustedRule: Boolean = false) {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            _isProcessing.value = true
            val result = commandRouter.confirmAndExecutePending(pending, saveTrustedRule)
            _isProcessing.value = false
            handleToolExecutionResult(result, pending.languageStyle)
        }
    }

    fun cancelPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val cancelMsg = when (pending.languageStyle) {
                "Hindi" -> "जी बॉस, '${pending.title}' रद्द कर दिया गया है।"
                "English" -> "Cancelled '${pending.title}'."
                else -> "Ji boss, '${pending.title}' cancel kar diya gaya hai."
            }
            dao.insertMessage(
                ChatMessageEntity(
                    sessionId = _activeSessionId.value,
                    isUser = false,
                    text = cancelMsg,
                    detectedLanguage = pending.languageStyle,
                    intentCategory = BrainIntentType.APP_ACTION.name,
                    toolInvoked = pending.toolName,
                    status = InteractionStatus.CANCELLED.name,
                    verificationEvidence = "User declined confirmation dialog"
                )
            )
        }
    }

    fun selectDisambiguatedApp(candidate: AppCandidate) {
        val prompt = _disambiguationPrompt.value
        _disambiguationPrompt.value = null
        val style = prompt?.languageStyle ?: "Hinglish"
        viewModelScope.launch {
            val result = commandRouter.appLaunchManager.executeLaunchApp(
                spokenName = candidate.appName,
                packageName = candidate.packageName,
                disambiguationChoice = candidate.packageName,
                languageStyle = style
            )
            commandRouter.logAction(result)
            handleToolExecutionResult(result, style)
        }
    }

    fun dismissDisambiguation() {
        _disambiguationPrompt.value = null
    }

    fun dismissCallingHud() {
        commandRouter.callingExecutor.dismissCallingHud()
    }

    fun selectCallingContactCandidate(candidate: com.example.data.ContactMatch, speaker: Boolean) {
        viewModelScope.launch {
            val res = commandRouter.callingExecutor.executeCallContact(
                contactName = candidate.name,
                phoneNumber = candidate.phoneNumber,
                speaker = speaker,
                languageStyle = "Hindi"
            )
            commandRouter.logAction(res)
            handleToolExecutionResult(res, "Hindi")
        }
    }

    fun toggleSpeakerFromHud(enabled: Boolean) {
        viewModelScope.launch {
            val res = commandRouter.callingExecutor.executeSetSpeaker(
                enabled = enabled,
                languageStyle = "Hindi"
            )
            commandRouter.logAction(res)
            handleToolExecutionResult(res, "Hindi")
        }
    }

    fun promptPermissionRationaleForKey(permissionKey: String) {
        val request = when (permissionKey) {
            Manifest.permission.RECORD_AUDIO -> PermissionRationaleRequest(
                permissionKey = Manifest.permission.RECORD_AUDIO,
                title = "माइक्रोफ़ोन अनुमति (Microphone Access)",
                explanation = "MAX को आपकी हिंदी, English और Hinglish वॉइस कमांड्स ('शिवम को कॉल करो', 'स्पीकर ऑन करो') सुनने के लिए माइक्रोफ़ोन की अनुमति चाहिए।",
                isSystemSettingsIntent = false
            )
            Manifest.permission.READ_CONTACTS -> PermissionRationaleRequest(
                permissionKey = Manifest.permission.READ_CONTACTS,
                title = "Contacts (संपर्क) अनुमति आवश्यक है",
                explanation = "जब आप 'शिवम को कॉल करो' या 'मम्मी को फोन लगाओ' कहते हैं, तो सही फ़ोन नंबर खोजने के लिए Contacts अनुमति आवश्यक है।",
                isSystemSettingsIntent = false
            )
            Manifest.permission.CALL_PHONE -> PermissionRationaleRequest(
                permissionKey = Manifest.permission.CALL_PHONE,
                title = "फ़ोन कॉल (Phone Call) अनुमति",
                explanation = "सीधे वॉइस कमांड से कॉल लगाने के लिए CALL_PHONE अनुमति आवश्यक है।",
                isSystemSettingsIntent = false
            )
            Manifest.permission.POST_NOTIFICATIONS -> PermissionRationaleRequest(
                permissionKey = Manifest.permission.POST_NOTIFICATIONS,
                title = "Notification Permission Required",
                explanation = "MAX AI needs Notification permission to display scheduled alarm alerts and the visible foreground microphone indicator when wake-word monitoring is enabled.",
                isSystemSettingsIntent = false
            )
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "LOCATION" -> PermissionRationaleRequest(
                permissionKey = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "Location (GPS) अनुमति आवश्यक है",
                explanation = "जब आप 'मेरी लोकेशन राहुल को भेजो' या 'मैं कहाँ हूँ' कहते हैं, तो गूगल मैप्स लोकेशन पिन प्राप्त करने के लिए लोकेशन की अनुमति आवश्यक है।",
                isSystemSettingsIntent = false
            )
            "NOTIFICATION_LISTENER" -> PermissionRationaleRequest(
                permissionKey = "NOTIFICATION_LISTENER",
                title = "Notification Access Authorization",
                explanation = "To read incoming notifications aloud on your command, Android requires you to enable MAX AI Notification Reader in System Notification Access settings. Sensitive OTPs are automatically redacted.",
                isSystemSettingsIntent = true
            )
            "ACCESSIBILITY_SERVICE" -> PermissionRationaleRequest(
                permissionKey = "ACCESSIBILITY_SERVICE",
                title = "AccessibilityService Authorization",
                explanation = "To perform UI actions like opening the notification shade, returning home, tapping buttons, scrolling, or entering text on your explicit command, enable 'MAX AI Automation' in Android Accessibility Settings.",
                isSystemSettingsIntent = true
            )
            else -> null
        }
        _permissionRationaleDialog.value = request
    }

    fun dismissPermissionRationale() {
        _permissionRationaleDialog.value = null
    }

    fun openSystemPermissionPage(permissionKey: String) {
        _permissionRationaleDialog.value = null
        val action = when (permissionKey) {
            "NOTIFICATION_LISTENER" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "ACCESSIBILITY_SERVICE" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        try {
            val intent = Intent(action).apply {
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    data = android.net.Uri.fromParts("package", appContext.packageName, null)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun onMicrophonePermissionResult(isGranted: Boolean, isConsoleTab: Boolean) {
        refreshPermissionsAndApps()
        if (isGranted) {
            if (pendingWakeWordEnableAfterPermission) {
                pendingWakeWordEnableAfterPermission = false
                toggleWakeWordService(true)
            } else if (isConsoleTab) {
                startVoiceListening()
            }
        } else {
            pendingWakeWordEnableAfterPermission = false
        }
    }

    fun toggleWakeWordService(enable: Boolean) {
        viewModelScope.launch {
            refreshPermissionsAndApps()
            if (enable && !_permissionSnapshot.value.microphoneGranted) {
                pendingWakeWordEnableAfterPermission = true
                promptPermissionRationaleForKey(Manifest.permission.RECORD_AUDIO)
                preferencesRepo.updateWakeWordEnabled(false)
                return@launch
            }
            pendingWakeWordEnableAfterPermission = false
            preferencesRepo.updateWakeWordEnabled(enable)
            val phrases = userPreferences.value.wakePhrasesCsv
            val locale = userPreferences.value.speechRecognitionLocale
            val serviceIntent = Intent(appContext, MaxVoiceWakeService::class.java).apply {
                if (!enable) {
                    action = MaxVoiceWakeService.ACTION_STOP_WAKE_SERVICE
                } else {
                    putExtra(MaxVoiceWakeService.EXTRA_WAKE_PHRASES, phrases)
                    putExtra(MaxVoiceWakeService.EXTRA_SPEECH_LOCALE, locale)
                }
            }
            try {
                if (enable) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        appContext.startService(serviceIntent)
                    }
                } else {
                    appContext.startService(serviceIntent)
                }
            } catch (_: Exception) {
            }
            refreshPermissionsAndApps()
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val currentSessionId = _activeSessionId.value
            val currentMsgs = dao.getMessagesBySessionSnapshot(currentSessionId)
                .filter { it.toolInvoked != "system_init" }
            if (currentMsgs.isNotEmpty()) {
                // Start a fresh conversation session so previous sessions remain in history while new chat has a clean context
                val newSessionId = "session_${System.currentTimeMillis()}"
                _activeSessionId.value = newSessionId
                preferencesRepo.updateActiveSessionId(newSessionId)
                conversationService.ensureSessionExists(newSessionId, memoryService.currentAccountKey())
                seedWelcomeMessageIfNeeded()
            } else {
                dao.clearSessionMessages(currentSessionId)
                seedWelcomeMessageIfNeeded()
            }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            dao.clearActionLogs()
            try {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    cloudRepository?.clearUserActionLogs()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun deleteTrustedRule(id: Long) {
        viewModelScope.launch {
            dao.deleteTrustedRule(id)
        }
    }

    fun toggleTrustedRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            dao.setTrustedRuleEnabled(id, enabled)
        }
    }

    fun deleteScheduledItem(id: Long) {
        viewModelScope.launch {
            dao.deleteScheduledItem(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
