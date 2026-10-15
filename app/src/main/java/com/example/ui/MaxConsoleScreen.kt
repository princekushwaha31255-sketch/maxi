package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppCandidate
import com.example.data.AssistantState
import com.example.data.AttachmentPayload
import com.example.data.ChatMessageEntity
import com.example.data.ContactMatch
import com.example.data.DisambiguationPrompt
import com.example.data.InteractionStatus
import com.example.data.PendingConfirmation
import com.example.tools.CallingHudState
import com.example.ui.theme.MaxAmberWarning
import com.example.ui.theme.MaxCoralError
import com.example.ui.theme.MaxElectricBlue
import com.example.ui.theme.MaxElectricCyan
import com.example.ui.theme.MaxEmeraldSuccess
import com.example.voice.RecognizedCandidate
import com.example.voice.SpeechRecognitionStage
import com.example.voice.VoiceEmotion
import com.example.voice.VoiceStylePreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MaxConsoleScreen(
    messages: List<ChatMessageEntity>,
    assistantState: AssistantState,
    recognitionStage: SpeechRecognitionStage,
    partialTranscript: String,
    rmsDbLevel: Float,
    waveformHistory: List<Float>,
    recognizedCandidates: List<RecognizedCandidate>,
    speechLocaleTag: String,
    voiceError: String?,
    activeVoiceEmotion: VoiceEmotion = VoiceEmotion.EVERYDAY_CONVERSATION,
    activeVoiceEngineLabel: String = "Expressive Neural TTS",
    autoSpeakEnabled: Boolean = true,
    voiceStylePreset: String = "Auto (Context-Aware)",
    onToggleAutoSpeak: (Boolean) -> Unit = {},
    onUpdateVoiceStylePreset: (String) -> Unit = {},
    onSpeakMessageAloud: (ChatMessageEntity) -> Unit = {},
    onFeedbackMessage: (ChatMessageEntity, String) -> Unit = { _, _ -> },
    wakeServiceActive: Boolean,
    wakeWordEnabled: Boolean = false,
    wakeServiceStatus: String = "",
    wakePartialTranscript: String = "",
    lastActivatedWakePhrase: String? = null,
    onToggleWakeWord: (Boolean) -> Unit = {},
    darkMode: Boolean = true,
    onToggleDarkMode: () -> Unit = {},
    onOpenNavigationDrawer: () -> Unit = {},
    hideSecurityBanner: Boolean,
    pendingConfirmation: PendingConfirmation?,
    disambiguationPrompt: DisambiguationPrompt?,
    pendingAttachment: AttachmentPayload? = null,
    callingHudState: CallingHudState = CallingHudState(),
    contactSearchQuery: String = "",
    filteredContacts: List<ContactMatch> = emptyList(),
    onContactSearchQueryChange: (String) -> Unit = {},
    onDismissCallingHud: () -> Unit = {},
    onSelectCallingCandidate: (ContactMatch, Boolean) -> Unit = { _, _ -> },
    onToggleSpeakerHud: (Boolean) -> Unit = {},
    onPickImageClick: () -> Unit = {},
    onPickFileClick: () -> Unit = {},
    onClearAttachment: () -> Unit = {},
    onMicClick: () -> Unit,
    onFinishListeningClick: () -> Unit,
    onStopCancelClick: () -> Unit,
    onLaunchSystemSpeechFallback: () -> Unit,
    onSelectSpeechLocale: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onDismissVoiceError: () -> Unit,
    onDismissSecurityBanner: () -> Unit,
    onConfirmPending: (Boolean) -> Unit,
    onCancelPending: () -> Unit,
    onSelectDisambiguation: (AppCandidate) -> Unit,
    onDismissDisambiguation: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteCurrentSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showContactSearchExpanded by remember { mutableStateOf(false) }
    var showDeleteCurrentSessionDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isWelcomeConversation = remember(messages) {
        messages.isEmpty() || (messages.size == 1 && !messages.first().isUser && messages.first().toolInvoked == "system_init")
    }

    LaunchedEffect(messages.size, assistantState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val welcomeSuggestionChips = remember {
        listOf(
            "Explain something" to "Explain quantum computing in simple Hinglish",
            "Write a message" to "Write a professional WhatsApp message for a team meeting at 5 PM",
            "Generate ideas" to "Generate 5 creative startup ideas for Android mobile apps",
            "Call Shivam" to "शिवम को कॉल करो"
        )
    }

    val quickSampleCommands = remember {
        listOf(
            "शिवम को कॉल करो",
            "मम्मी को फोन लगाओ",
            "राहुल को कॉल करो और स्पीकर ऑन करना",
            "स्पीकर ऑन करो",
            "स्पीकर बंद करो",
            "सुनना बंद करो",
            "Instagram kholo",
            "Volume 70 karo"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Compact ChatGPT-Style Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Menu Drawer Button + MAX AI Logo & Model/Language Selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenNavigationDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_navigation_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open navigation menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showLanguageMenu = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("header_model_language_selector")
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_max_ai_logo),
                                contentDescription = "MAX AI Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MAX AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (speechLocaleTag) {
                                    "hi-IN" -> "• HI"
                                    "en-US" -> "• EN"
                                    else -> "• Hinglish"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select language or voice mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Hinglish / India (en-IN)") },
                                onClick = {
                                    onSelectSpeechLocale("en-IN")
                                    showLanguageMenu = false
                                },
                                trailingIcon = {
                                    if (speechLocaleTag == "en-IN") {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("हिंदी / Hindi (hi-IN)") },
                                onClick = {
                                    onSelectSpeechLocale("hi-IN")
                                    showLanguageMenu = false
                                },
                                trailingIcon = {
                                    if (speechLocaleTag == "hi-IN") {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("English (en-US)") },
                                onClick = {
                                    onSelectSpeechLocale("en-US")
                                    showLanguageMenu = false
                                },
                                trailingIcon = {
                                    if (speechLocaleTag == "en-US") {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Voice Style Preset",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaxElectricCyan
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            VoiceStylePreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.displayTitle) },
                                    onClick = {
                                        onUpdateVoiceStylePreset(preset.id)
                                        showLanguageMenu = false
                                    },
                                    trailingIcon = {
                                        if (voiceStylePreset.equals(preset.id, ignoreCase = true) ||
                                            voiceStylePreset.equals(preset.displayTitle, ignoreCase = true)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (autoSpeakEnabled) "Mute Voice Output (Auto-Speak ON)" else "Enable Voice Output (Muted)")
                                },
                                onClick = {
                                    onToggleAutoSpeak(!autoSpeakEnabled)
                                    showLanguageMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (autoSpeakEnabled) Icons.Default.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = null,
                                        tint = if (autoSpeakEnabled) MaxEmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (darkMode) "Switch to Light Mode" else "Switch to Dark Mode")
                                },
                                onClick = {
                                    onToggleDarkMode()
                                    showLanguageMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Subtle status dot for compatibility & live state
                    StatusPillIndicator(
                        assistantState = assistantState,
                        wakeServiceActive = wakeServiceActive,
                        activeVoiceEmotion = activeVoiceEmotion
                    )
                }

                // Right: Auto-Speak Mute Toggle, Wake-Word Quick Chip, Contact Search Toggle, Stop Button (when active), and New Chat Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleAutoSpeak(!autoSpeakEnabled) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("console_voice_mute_toggle")
                    ) {
                        Icon(
                            imageVector = if (autoSpeakEnabled) Icons.Default.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (autoSpeakEnabled) "Mute AI Voice" else "Unmute AI Voice",
                            tint = if (autoSpeakEnabled) MaxEmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    FilterChip(
                        selected = wakeServiceActive || wakeWordEnabled,
                        onClick = { onToggleWakeWord(!(wakeServiceActive || wakeWordEnabled)) },
                        label = {
                            Text(
                                text = if (wakeServiceActive) "Wake ON" else "Wake",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Toggle Hands-Free Wake Word",
                                tint = if (wakeServiceActive) MaxEmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaxEmeraldSuccess.copy(alpha = 0.16f),
                            selectedLabelColor = MaxEmeraldSuccess
                        ),
                        modifier = Modifier
                            .height(30.dp)
                            .padding(end = 2.dp)
                            .testTag("console_wake_word_toggle")
                    )

                    IconButton(
                        onClick = { showContactSearchExpanded = !showContactSearchExpanded },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("toggle_contact_search_bar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Quick Call Contacts",
                            tint = if (showContactSearchExpanded || contactSearchQuery.isNotBlank()) {
                                MaxElectricCyan
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (assistantState == AssistantState.LISTENING ||
                        assistantState == AssistantState.SPEAKING ||
                        assistantState == AssistantState.PROCESSING
                    ) {
                        IconButton(
                            onClick = onStopCancelClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("stop_cancel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.stop_speaking_desc),
                                tint = MaxCoralError
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "New chat",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(
                        onClick = { showDeleteCurrentSessionDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_current_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete current conversation session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showDeleteCurrentSessionDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteCurrentSessionDialog = false },
                title = {
                    Text(
                        text = "Delete Conversation?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete this active conversation session? All messages will be permanently deleted and a fresh session will start.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteCurrentSessionDialog = false
                            onDeleteCurrentSession()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.testTag("confirm_delete_current_session_btn")
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteCurrentSessionDialog = false },
                        modifier = Modifier.testTag("cancel_delete_current_session_btn")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 12.dp)
        ) {
            // Top Contact Search Bar for Quick Direct Calling without Voice Commands
            TopContactSearchBarCard(
                searchQuery = contactSearchQuery,
                filteredContacts = filteredContacts,
                isExpanded = showContactSearchExpanded,
                onExpandChange = { showContactSearchExpanded = it },
                onSearchQueryChange = onContactSearchQueryChange,
                onCallContact = { contact, withSpeaker ->
                    onSelectCallingCandidate(contact, withSpeaker)
                    onContactSearchQueryChange("")
                    showContactSearchExpanded = false
                }
            )

            // Hands-Free Wake-Word Active Status & Listening Indicator Banner
            AnimatedVisibility(visible = wakeServiceActive || lastActivatedWakePhrase != null) {
                Surface(
                    color = if (lastActivatedWakePhrase != null) {
                        MaxElectricCyan.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("hands_free_wake_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaxElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                lastActivatedWakePhrase != null ->
                                    "Wake-word \"$lastActivatedWakePhrase\" detected • Listening for your command..."
                                wakePartialTranscript.isNotBlank() ->
                                    "Wake monitor heard: \"$wakePartialTranscript\"..."
                                else ->
                                    "Hands-free wake active • Say \"Hey MAX\", \"OK MAX\", or \"MAX\""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Security Warning Notice (Dismissible, required by android-secret-management skill)
            AnimatedVisibility(visible = !hideSecurityBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("security_warning_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Security Notice",
                            tint = MaxAmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.security_warning_banner),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissSecurityBanner,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("dismiss_security_banner_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss warning",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Voice error toast banner with Retry & System Speech Prompt Fallback
            AnimatedVisibility(visible = !voiceError.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("voice_error_banner")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Voice Error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = voiceError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissVoiceError,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            TextButton(
                                onClick = onMicClick,
                                modifier = Modifier.testTag("retry_voice_button")
                            ) {
                                Text(
                                    text = "Retry Mic",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            TextButton(
                                onClick = onLaunchSystemSpeechFallback,
                                modifier = Modifier.testTag("system_speech_fallback_button")
                            ) {
                                Text(
                                    text = "System Voice Dialog",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main Conversational Chat Area (Occupies most of the screen)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("chat_messages_list"),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isWelcomeConversation) {
                    item(key = "welcome_hero_header") {
                        ChatGptWelcomeHero(
                            suggestionChips = welcomeSuggestionChips,
                            quickSampleCommands = quickSampleCommands,
                            onSendSuggestion = { prompt -> onSendCommand(prompt) }
                        )
                    }
                }

                items(
                    items = if (isWelcomeConversation) emptyList() else messages,
                    key = { it.id }
                ) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isCurrentlySpeaking = assistantState == AssistantState.SPEAKING,
                        onSpeakMessageAloud = { onSpeakMessageAloud(msg) },
                        onStopSpeaking = onStopCancelClick,
                        onFeedbackMessage = { feedback -> onFeedbackMessage(msg, feedback) }
                    )
                }

                if (assistantState == AssistantState.PROCESSING) {
                    item(key = "ai_typing_indicator") {
                        AiTypingIndicatorRow()
                    }
                }
            }

            // Calling & Speaker Structured Action Status Card
            AnimatedVisibility(visible = callingHudState.isActive) {
                CallingAndSpeakerStatusCard(
                    state = callingHudState,
                    onToggleSpeaker = onToggleSpeakerHud,
                    onSelectCandidate = { match ->
                        onSelectCallingCandidate(match, callingHudState.speakerEnabled)
                    },
                    onDismiss = onDismissCallingHud
                )
            }

            // Live SpeechRecognizer HUD Card (visible when actively listening or processing audio)
            AnimatedVisibility(
                visible = assistantState == AssistantState.LISTENING ||
                    recognitionStage == SpeechRecognitionStage.PROCESSING_AUDIO
            ) {
                SpeechRecognizerLiveHudCard(
                    recognitionStage = recognitionStage,
                    partialTranscript = partialTranscript,
                    waveformHistory = waveformHistory,
                    speechLocaleTag = speechLocaleTag,
                    onSelectSpeechLocale = onSelectSpeechLocale,
                    onFinishListeningClick = onFinishListeningClick,
                    onCancelListeningClick = onStopCancelClick
                )
            }

            // Alternative N-Best SpeechRecognizer Candidates Bar
            AnimatedVisibility(
                visible = recognizedCandidates.size > 1 && assistantState == AssistantState.IDLE
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp)
                        .testTag("speech_candidates_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Did you say:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    recognizedCandidates.drop(1).take(3).forEach { candidate ->
                        AssistChip(
                            onClick = { onSendCommand(candidate.transcript) },
                            label = {
                                Text(
                                    text = candidate.transcript,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // 3. Fixed Rounded ChatGPT-Style Message Composer at Bottom
            VoiceAndInputDock(
                assistantState = assistantState,
                rmsDbLevel = rmsDbLevel,
                speechLocaleTag = speechLocaleTag,
                inputText = inputText,
                pendingAttachment = pendingAttachment,
                onInputTextChange = { inputText = it },
                onPickImageClick = onPickImageClick,
                onPickFileClick = onPickFileClick,
                onClearAttachment = onClearAttachment,
                onMicClick = onMicClick,
                onStopCancelClick = onStopCancelClick,
                onSelectSpeechLocale = onSelectSpeechLocale,
                onSendClick = {
                    if (inputText.isNotBlank() || pendingAttachment != null) {
                        onSendCommand(inputText)
                        inputText = ""
                    }
                }
            )
        }
    }

    // Confirmation Modal (Section 7, 8, 10)
    if (pendingConfirmation != null) {
        PendingConfirmationDialog(
            pending = pendingConfirmation,
            onConfirm = onConfirmPending,
            onCancel = onCancelPending
        )
    }

    // Disambiguation Modal (Section 5.4)
    if (disambiguationPrompt != null) {
        AppDisambiguationDialog(
            prompt = disambiguationPrompt,
            onSelect = onSelectDisambiguation,
            onDismiss = onDismissDisambiguation
        )
    }
}

@Composable
private fun SpeechRecognizerLiveHudCard(
    recognitionStage: SpeechRecognitionStage,
    partialTranscript: String,
    waveformHistory: List<Float>,
    speechLocaleTag: String,
    onSelectSpeechLocale: (String) -> Unit,
    onFinishListeningClick: () -> Unit,
    onCancelListeningClick: () -> Unit
) {
    val stageTitle = when (recognitionStage) {
        SpeechRecognitionStage.INITIALIZING -> "Starting Android SpeechRecognizer..."
        SpeechRecognitionStage.READY_FOR_SPEECH -> "Listening • Speak now (Hindi / English / Hinglish)"
        SpeechRecognitionStage.SPEECH_DETECTED -> "Capturing voice stream..."
        SpeechRecognitionStage.PROCESSING_AUDIO -> "Processing spoken audio..."
        SpeechRecognitionStage.ERROR -> "SpeechRecognizer encountered an error"
        SpeechRecognitionStage.IDLE -> "Listening..."
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                width = 1.dp,
                color = MaxElectricCyan.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("speech_recognizer_live_hud")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardVoice,
                        contentDescription = null,
                        tint = MaxElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stageTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaxElectricCyan
                    )
                }

                // Quick Language Switcher while listening
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("en-IN" to "HI/EN", "hi-IN" to "हिंदी", "en-US" to "EN").forEach { (tag, label) ->
                        val selected = speechLocaleTag == tag
                        Surface(
                            color = if (selected) MaxElectricCyan else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { onSelectSpeechLocale(tag) }
                                .testTag("hud_locale_$tag")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 20-bar Real-Time Audio Waveform Equalizer driven by SpeechRecognizer onRmsChanged()
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("speech_waveform_canvas")
            ) {
                val barCount = waveformHistory.size.coerceAtLeast(1)
                val spacing = 6.dp.toPx()
                val totalSpacing = spacing * (barCount - 1)
                val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(4f)
                val centerY = size.height / 2f

                waveformHistory.forEachIndexed { index, amplitude ->
                    val barHeight = (size.height * amplitude.coerceIn(0.12f, 1f)).coerceAtLeast(6f)
                    val left = index * (barWidth + spacing)
                    val top = centerY - (barHeight / 2f)
                    drawRoundRect(
                        color = if (index >= barCount - 4) MaxElectricCyan else MaxElectricBlue,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Partial Transcript Display
            Text(
                text = if (partialTranscript.isNotBlank()) {
                    "“$partialTranscript”"
                } else {
                    "Try: “Instagram kholo”, “Volume 70 karo”, or “7:00 AM alarm lagao”"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (partialTranscript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                color = if (partialTranscript.isNotBlank()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("live_partial_transcript_text")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Controls: Done Speaking vs Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancelListeningClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("hud_cancel_listening_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop listening",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("सुनना बंद करो (Stop)")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onFinishListeningClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxEmeraldSuccess,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("hud_done_speaking_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Finish speaking",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Done Speaking", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallingAndSpeakerStatusCard(
    state: CallingHudState,
    onToggleSpeaker: (Boolean) -> Unit,
    onSelectCandidate: (ContactMatch) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (state.errorTextHi != null) MaxCoralError.copy(alpha = 0.7f) else MaxEmeraldSuccess.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("calling_speaker_hud_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = "Calling & Speaker Bridge",
                        tint = if (state.errorTextHi != null) MaxCoralError else MaxEmeraldSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.statusTextHi.ifBlank { "कॉल और स्पीकर नियंत्रण" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.speakerEnabled,
                        onClick = { onToggleSpeaker(!state.speakerEnabled) },
                        label = {
                            Text(
                                text = if (state.speakerEnabled) "स्पीकर: ऑन" else "स्पीकर: बंद",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker Toggle",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaxElectricCyan.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("speaker_toggle_chip")
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss calling card",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (!state.errorTextHi.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.errorTextHi,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxCoralError
                )
            }

            if (state.candidateMatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "कई संपर्क मिले — कॉल करने के लिए चुनें:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.candidateMatches.forEach { candidate ->
                        AssistChip(
                            onClick = { onSelectCandidate(candidate) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${candidate.name} (${candidate.phoneNumber})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopContactSearchBarCard(
    searchQuery: String,
    filteredContacts: List<ContactMatch>,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onCallContact: (ContactMatch, Boolean) -> Unit
) {
    var showQuickContactsDrawer by remember { mutableStateOf(false) }
    val showBar = isExpanded || searchQuery.isNotBlank() || showQuickContactsDrawer
    val isSearching = searchQuery.isNotBlank() || showQuickContactsDrawer
    val rawDialDigits = remember(searchQuery) {
        com.example.tools.MessagingAndSocialExecutor.normalizePhoneNumber(searchQuery)
    }

    AnimatedVisibility(visible = showBar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp)
                .testTag("top_contact_search_container")
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    onSearchQueryChange(it)
                    if (it.isNotBlank()) showQuickContactsDrawer = true
                },
                placeholder = {
                    Text(
                        text = "Search contacts to call (शिवम, मम्मी, राहुल or number)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search contacts",
                        tint = MaxElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    showQuickContactsDrawer = false
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("clear_contact_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear contact search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            TextButton(
                                onClick = { showQuickContactsDrawer = !showQuickContactsDrawer },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("toggle_contacts_dropdown_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Quick Call Contacts",
                                    tint = MaxElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showQuickContactsDrawer) "Hide" else "Contacts (${filteredContacts.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaxElectricCyan
                                )
                            }
                            IconButton(
                                onClick = {
                                    showQuickContactsDrawer = false
                                    onExpandChange(false)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close contact search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaxElectricCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val topMatch = filteredContacts.firstOrNull()
                        if (topMatch != null) {
                            onCallContact(topMatch, false)
                        } else if (rawDialDigits.isNotEmpty()) {
                            onCallContact(ContactMatch(name = searchQuery.trim(), phoneNumber = rawDialDigits), false)
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top_contact_search_bar")
            )

            AnimatedVisibility(visible = isSearching) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("contact_search_results_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "QUICK CALL CONTACTS (${filteredContacts.size})"
                                } else {
                                    "MATCHING CONTACTS (${filteredContacts.size})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaxElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap Call or Speaker",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (filteredContacts.isEmpty()) {
                            if (rawDialDigits.isNotEmpty()) {
                                ContactSearchResultRow(
                                    contact = ContactMatch(
                                        name = "Dial Number: ${searchQuery.trim()}",
                                        phoneNumber = rawDialDigits
                                    ),
                                    onDirectCall = {
                                        onCallContact(
                                            ContactMatch(name = searchQuery.trim(), phoneNumber = rawDialDigits),
                                            false
                                        )
                                    },
                                    onSpeakerCall = {
                                        onCallContact(
                                            ContactMatch(name = searchQuery.trim(), phoneNumber = rawDialDigits),
                                            true
                                        )
                                    }
                                )
                            } else {
                                Text(
                                    text = "कोई संपर्क नहीं मिला (No contact matching \"$searchQuery\"). नाम या 10-digit नंबर टाइप करें।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        } else {
                            filteredContacts.take(5).forEach { contact ->
                                ContactSearchResultRow(
                                    contact = contact,
                                    onDirectCall = { onCallContact(contact, false) },
                                    onSpeakerCall = { onCallContact(contact, true) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSearchResultRow(
    contact: ContactMatch,
    onDirectCall: () -> Unit,
    onSpeakerCall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDirectCall() }
            .testTag("contact_result_${contact.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaxElectricBlue.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaxElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledIconButton(
                    onClick = onDirectCall,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaxEmeraldSuccess,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("direct_call_button_${contact.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Direct Call ${contact.name}",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledIconButton(
                    onClick = onSpeakerCall,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaxElectricCyan.copy(alpha = 0.2f),
                        contentColor = MaxElectricCyan
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("speaker_call_button_${contact.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Call ${contact.name} on Speaker",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPillIndicator(
    assistantState: AssistantState,
    wakeServiceActive: Boolean,
    activeVoiceEmotion: VoiceEmotion = VoiceEmotion.EVERYDAY_CONVERSATION
) {
    // Minimal indicator dot and compact label only when active (no oversized status banner)
    val (label, dotColor) = when (assistantState) {
        AssistantState.LISTENING -> "Listening" to MaxElectricCyan
        AssistantState.PROCESSING -> "Thinking" to MaxAmberWarning
        AssistantState.SPEAKING -> "${activeVoiceEmotion.badgeEmoji} ${activeVoiceEmotion.displayLabel}" to MaxEmeraldSuccess
        AssistantState.CANCELLED -> "Stopped" to MaxCoralError
        AssistantState.IDLE -> if (wakeServiceActive) {
            "" to MaxEmeraldSuccess
        } else {
            "" to MaxElectricCyan
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("assistant_state_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatGptWelcomeHero(
    suggestionChips: List<Pair<String, String>>,
    quickSampleCommands: List<String>,
    onSendSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp, start = 8.dp, end = 8.dp)
            .testTag("chatgpt_welcome_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_max_ai_logo),
            contentDescription = "MAX AI Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "What can I help you with?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Ask MAX AI anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 2x2 Grid of ChatGPT-style Suggestion Cards ("Explain something", "Write a message", "Generate ideas", "Call Shivam")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestionChips.chunked(2).forEach { rowPairs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPairs.forEach { (title, actualCommand) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSendSuggestion(actualCommand) }
                                .testTag("suggestion_chip_${title.lowercase().replace(" ", "_")}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = actualCommand,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Subtle scrollable Hindi/Hinglish voice & calling quick actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickSampleCommands.forEach { cmd ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(50)
                        )
                        .clip(RoundedCornerShape(50))
                        .clickable { onSendSuggestion(cmd) }
                        .testTag("quick_chip_${cmd.take(10).lowercase().replace(" ", "_")}")
                ) {
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTypingIndicatorRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("ai_typing_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_max_ai_logo),
            contentDescription = "MAX AI Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "MAX AI is thinking...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    isCurrentlySpeaking: Boolean = false,
    onSpeakMessageAloud: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onFeedbackMessage: (String) -> Unit = {}
) {
    val isUser = message.isUser
    val clipboardManager = LocalClipboardManager.current
    var copiedMessage by remember { mutableStateOf(false) }

    val statusEnum = remember(message.status) {
        try {
            InteractionStatus.valueOf(message.status)
        } catch (_: Exception) {
            InteractionStatus.INFO_RESPONSE
        }
    }

    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    if (isUser) {
        // User Message: Right-aligned rounded pill bubble (ChatGPT mobile style)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 4.dp
                ),
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                    )
                }
            }
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 3.dp, end = 4.dp)
            )
        }
    } else {
        // AI Response: Clean full-width conversational layout with avatar, Markdown & code blocks, and copy action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_max_ai_logo),
                contentDescription = "MAX AI Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MAX AI",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                SelectionContainer {
                    MarkdownAndCodeBlockRenderer(
                        rawText = message.text,
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                        }
                    )
                }

                if (message.toolInvoked in listOf("get_location", "share_location") || message.text.contains("maps.google.com")) {
                    val mapMatch = Regex("""(https?://maps\.google\.com/\S+)""").find(message.text)
                    val mapUrl = mapMatch?.value ?: "https://maps.google.com"
                    val addrLine = message.text.lines().firstOrNull { it.contains("यहाँ हैं:") || it.contains("location:") || it.contains("Lat:") }
                        ?.substringAfter(":")?.trim().orEmpty()
                    Spacer(modifier = Modifier.height(8.dp))
                    MapLocationCard(
                        mapsUrl = mapUrl,
                        addressText = addrLine
                    )
                }

                // Subtle action row below AI message (Copy full response + Expressive Speak Aloud / Stop)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(message.text))
                                copiedMessage = true
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("copy_message_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (copiedMessage) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (copiedMessage) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (isCurrentlySpeaking) {
                                    onStopSpeaking()
                                } else {
                                    onSpeakMessageAloud()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("speak_message_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (isCurrentlySpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (isCurrentlySpeaking) "Stop speaking" else "Speak response aloud",
                            tint = if (isCurrentlySpeaking) MaxCoralError else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCurrentlySpeaking) "Stop" else "Read Aloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrentlySpeaking) MaxCoralError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Thumbs Up / Helpful Feedback (Personalization Engine learning signal)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onFeedbackMessage("helpful") }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("feedback_helpful_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Mark response helpful",
                            tint = if (message.userFeedback == "helpful") MaxEmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Thumbs Down / Not Helpful Feedback (Personalization Engine learning signal)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onFeedbackMessage("not_helpful") }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("feedback_not_helpful_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = "Mark response not helpful",
                            tint = if (message.userFeedback == "not_helpful") MaxCoralError else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed class MarkdownSegment {
    data class TextParagraph(val content: String) : MarkdownSegment()
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment()
}

private fun parseMarkdownSegments(rawText: String): List<MarkdownSegment> {
    if (!rawText.contains("```")) {
        return listOf(MarkdownSegment.TextParagraph(rawText))
    }
    val segments = mutableListOf<MarkdownSegment>()
    val regex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(rawText)) {
        if (match.range.first > lastIndex) {
            val preceding = rawText.substring(lastIndex, match.range.first).trim()
            if (preceding.isNotEmpty()) {
                segments.add(MarkdownSegment.TextParagraph(preceding))
            }
        }
        val lang = match.groupValues[1].trim().ifEmpty { "code" }
        val codeContent = match.groupValues[2].trimEnd()
        segments.add(MarkdownSegment.CodeBlock(language = lang, code = codeContent))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < rawText.length) {
        val remaining = rawText.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            segments.add(MarkdownSegment.TextParagraph(remaining))
        }
    }
    return if (segments.isEmpty()) listOf(MarkdownSegment.TextParagraph(rawText)) else segments
}

@Composable
private fun formatInlineMarkdown(text: String, codeBg: Color): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            if (text.startsWith("**", idx)) {
                val closeIdx = text.indexOf("**", idx + 2)
                if (closeIdx != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(idx + 2, closeIdx))
                    }
                    idx = closeIdx + 2
                    continue
                }
            }
            if (text[idx] == '`') {
                val closeIdx = text.indexOf('`', idx + 1)
                if (closeIdx != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg
                        )
                    ) {
                        append(" ${text.substring(idx + 1, closeIdx)} ")
                    }
                    idx = closeIdx + 1
                    continue
                }
            }
            append(text[idx])
            idx++
        }
    }
}

@Composable
private fun MarkdownAndCodeBlockRenderer(
    rawText: String,
    onCopyCode: (String) -> Unit
) {
    val segments = remember(rawText) { parseMarkdownSegments(rawText) }
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.TextParagraph -> {
                    Text(
                        text = formatInlineMarkdown(segment.content, inlineCodeBg),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                is MarkdownSegment.CodeBlock -> {
                    var codeCopied by remember { mutableStateOf(false) }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = segment.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onCopyCode(segment.code)
                                            codeCopied = true
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (codeCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy code block",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (codeCopied) "Copied!" else "Copy code",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = segment.code,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeBadge(status: InteractionStatus, toolName: String) {
    val color = statusColor(status)
    val icon = when (status) {
        InteractionStatus.VERIFIED_SUCCESS -> Icons.Default.CheckCircle
        InteractionStatus.PENDING_CONFIRMATION -> Icons.Default.WarningAmber
        InteractionStatus.CLARIFICATION_REQUIRED -> Icons.AutoMirrored.Filled.HelpOutline
        InteractionStatus.PERMISSION_REQUIRED -> Icons.Default.Lock
        InteractionStatus.EXECUTION_FAILED -> Icons.Default.ErrorOutline
        InteractionStatus.UNSUPPORTED_ACTION -> Icons.Default.Info
        InteractionStatus.INFO_RESPONSE -> Icons.Default.Info
        InteractionStatus.CANCELLED -> Icons.AutoMirrored.Filled.VolumeOff
    }

    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.name,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$toolName • ${status.name}",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

private fun statusColor(status: InteractionStatus): Color = when (status) {
    InteractionStatus.VERIFIED_SUCCESS -> MaxEmeraldSuccess
    InteractionStatus.PENDING_CONFIRMATION -> MaxAmberWarning
    InteractionStatus.CLARIFICATION_REQUIRED -> MaxElectricCyan
    InteractionStatus.PERMISSION_REQUIRED -> MaxAmberWarning
    InteractionStatus.EXECUTION_FAILED -> MaxCoralError
    InteractionStatus.UNSUPPORTED_ACTION -> MaxCoralError
    InteractionStatus.INFO_RESPONSE -> MaxElectricBlue
    InteractionStatus.CANCELLED -> MaxCoralError
}

@Composable
private fun VoiceAndInputDock(
    assistantState: AssistantState,
    rmsDbLevel: Float,
    speechLocaleTag: String,
    inputText: String,
    pendingAttachment: AttachmentPayload?,
    onInputTextChange: (String) -> Unit,
    onPickImageClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onStopCancelClick: () -> Unit,
    onSelectSpeechLocale: (String) -> Unit,
    onSendClick: () -> Unit
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pending Attachment Pill (when an image or PDF/code file is attached)
        if (pendingAttachment != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("pending_attachment_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (pendingAttachment.mimeType.startsWith("image/")) {
                                Icons.Default.Image
                            } else {
                                Icons.Default.PictureAsPdf
                            },
                            contentDescription = "Attached File",
                            tint = MaxElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${pendingAttachment.fileName} (${pendingAttachment.mimeType})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onClearAttachment,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ChatGPT-style Rounded Pill Message Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (assistantState == AssistantState.LISTENING) {
                        MaxElectricCyan
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Plus / Attachment Button with Dropdown + Direct Test Tags
                Box {
                    IconButton(
                        onClick = { showAttachmentMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("composer_plus_attachment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add attachment or switch voice language",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showAttachmentMenu,
                        onDismissRequest = { showAttachmentMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Attach Photo / Image") },
                            onClick = {
                                showAttachmentMenu = false
                                onPickImageClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaxElectricCyan
                                )
                            },
                            modifier = Modifier.testTag("attach_image_button")
                        )
                        DropdownMenuItem(
                            text = { Text("Attach PDF / Code / Document") },
                            onClick = {
                                showAttachmentMenu = false
                                onPickFileClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = MaxElectricCyan
                                )
                            },
                            modifier = Modifier.testTag("attach_file_button")
                        )
                        HorizontalDivider()
                        listOf(
                            "en-IN" to "Voice: Hinglish / India",
                            "hi-IN" to "Voice: हिंदी (Hindi)",
                            "en-US" to "Voice: English (US)"
                        ).forEach { (tag, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSelectSpeechLocale(tag)
                                    showAttachmentMenu = false
                                },
                                trailingIcon = {
                                    if (speechLocaleTag == tag) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                modifier = Modifier.testTag("dock_speech_locale_$tag")
                            )
                        }
                    }
                }

                // 2. Multiline Auto-Resizing Text Input ("Message MAX AI...")
                // Supports Enter to send & Shift+Enter for newline on desktop, and Send IME action on mobile
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.input_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    minLines = 1,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.Enter &&
                                !keyEvent.isShiftPressed
                            ) {
                                if (inputText.isNotBlank() || pendingAttachment != null) {
                                    onSendClick()
                                }
                                true
                            } else {
                                false
                            }
                        }
                        .testTag("command_text_input")
                )

                // 3. Microphone / Stop Voice Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp)
                ) {
                    if (assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING) {
                        val ringColor = if (assistantState == AssistantState.LISTENING) MaxElectricCyan else MaxEmeraldSuccess
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dynamicRadius = (size.minDimension / 2f) *
                                (pulseScale + (rmsDbLevel / 40f)).coerceAtMost(1.3f)
                            drawCircle(
                                color = ringColor.copy(alpha = 0.22f),
                                radius = dynamicRadius
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING) {
                                onStopCancelClick()
                            } else {
                                onMicClick()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("prominent_mic_button")
                    ) {
                        Icon(
                            imageVector = when (assistantState) {
                                AssistantState.LISTENING -> Icons.Default.Stop
                                AssistantState.SPEAKING -> Icons.Default.GraphicEq
                                else -> Icons.Default.Mic
                            },
                            contentDescription = stringResource(R.string.mic_button_desc),
                            tint = when (assistantState) {
                                AssistantState.LISTENING -> MaxCoralError
                                AssistantState.SPEAKING -> MaxEmeraldSuccess
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // 4. Send Button
                val canSend = inputText.isNotBlank() || pendingAttachment != null
                FilledIconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send_command_desc),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingConfirmationDialog(
    pending: PendingConfirmation,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var rememberTrustedRule by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Confirmation Required",
                tint = MaxAmberWarning
            )
        },
        title = {
            Text(
                text = pending.title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pending.riskReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pending.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                if (pending.canCreateTrustedRule) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberTrustedRule = !rememberTrustedRule }
                            .padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = rememberTrustedRule,
                            onCheckedChange = { rememberTrustedRule = it },
                            modifier = Modifier.testTag("trusted_rule_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save narrowly scoped trusted rule for this recipient",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rememberTrustedRule) },
                colors = ButtonDefaults.buttonColors(containerColor = MaxEmeraldSuccess),
                modifier = Modifier.testTag("confirm_action_button")
            ) {
                Text("Confirm & Proceed", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_confirmation_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AppDisambiguationDialog(
    prompt: DisambiguationPrompt,
    onSelect: (AppCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Application for “${prompt.originalSpokenName}”",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Multiple installed applications match your request. Select which one MAX AI should launch:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                prompt.candidates.forEach { candidate ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(candidate) }
                            .testTag("disambiguate_option_${candidate.packageName}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = candidate.appName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = candidate.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
