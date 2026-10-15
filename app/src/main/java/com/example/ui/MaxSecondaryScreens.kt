package com.example.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.BuildConfig
import com.example.service.AppUpdateInfo
import com.example.service.UpdateUiState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ActionLogEntity
import com.example.data.MaxUserPreferences
import com.example.data.MemoryItemEntity
import com.example.data.TrustedRuleEntity
import com.example.ui.theme.MaxAmberWarning
import com.example.ui.theme.MaxCoralError
import com.example.ui.theme.MaxElectricBlue
import com.example.ui.theme.MaxElectricCyan
import com.example.ui.theme.MaxEmeraldSuccess
import com.example.viewmodel.MaxViewModel
import com.example.viewmodel.PermissionStatusSnapshot
import com.example.voice.VoiceInfoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MaxPermissionsScreen(
    snapshot: PermissionStatusSnapshot,
    wakeServiceStatus: String,
    onRequestPermissionWithRationale: (String) -> Unit,
    onOpenSystemSettingsForPermission: (String) -> Unit,
    onRefreshStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("permissions_guide_list"),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permission Status & Setup Guide",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "MAX AI requests only minimum required permissions, explains why each is needed, and re-checks before every action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefreshStatus,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh permission status"
                        )
                    }
                }
            }

            item {
                PermissionItemCard(
                    title = "Microphone Access (RECORD_AUDIO)",
                    isGranted = snapshot.microphoneGranted,
                    icon = Icons.Default.Mic,
                    whyNeeded = "Required to capture spoken Hindi, English, and Hinglish commands via SpeechRecognizer when you tap the mic or enable wake-word monitoring.",
                    setupGuide = "Tap 'Authorize' to grant runtime Microphone permission, or revoke anytime in System App Info.",
                    onActionClick = {
                        if (snapshot.microphoneGranted) {
                            onOpenSystemSettingsForPermission(Manifest.permission.RECORD_AUDIO)
                        } else {
                            onRequestPermissionWithRationale(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    actionLabel = if (snapshot.microphoneGranted) "Manage / Revoke" else "Authorize Microphone",
                    testTag = "perm_card_microphone"
                )
            }

            item {
                PermissionItemCard(
                    title = "Contacts Access (READ_CONTACTS)",
                    isGranted = snapshot.contactsGranted,
                    icon = Icons.Default.Contacts,
                    whyNeeded = "Required to resolve recipient names to verified phone numbers for WhatsApp and WhatsApp Business workflows. MAX AI never sends to an uncertain recipient.",
                    setupGuide = "Tap 'Authorize' to allow reading contacts, or provide a phone number directly in your command.",
                    onActionClick = {
                        if (snapshot.contactsGranted) {
                            onOpenSystemSettingsForPermission(Manifest.permission.READ_CONTACTS)
                        } else {
                            onRequestPermissionWithRationale(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    actionLabel = if (snapshot.contactsGranted) "Manage / Revoke" else "Authorize Contacts",
                    testTag = "perm_card_contacts"
                )
            }

            item {
                PermissionItemCard(
                    title = "AccessibilityService (UI Automation)",
                    isGranted = snapshot.accessibilityServiceEnabled,
                    icon = Icons.Default.AccessibilityNew,
                    whyNeeded = "Required for opening the notification shade, performing UI taps, scrolling, and text entry on your explicit command.",
                    setupGuide = "1. Tap 'Open Accessibility Settings'\n2. Select 'Downloaded apps' or 'Installed services'\n3. Enable 'MAX AI Automation'",
                    onActionClick = {
                        onRequestPermissionWithRationale("ACCESSIBILITY_SERVICE")
                    },
                    actionLabel = if (snapshot.accessibilityServiceEnabled) "Configure in Settings" else "Enable AccessibilityService",
                    testTag = "perm_card_accessibility"
                )
            }

            item {
                PermissionItemCard(
                    title = "Notification Listener Access",
                    isGranted = snapshot.notificationListenerEnabled,
                    icon = Icons.Default.NotificationsActive,
                    whyNeeded = "Required to read incoming notifications when you ask 'Read my notifications'. Sensitive OTPs and passwords are automatically redacted.",
                    setupGuide = "1. Tap 'Open Notification Access'\n2. Select 'MAX AI'\n3. Toggle 'Allow notification access' ON",
                    onActionClick = {
                        onRequestPermissionWithRationale("NOTIFICATION_LISTENER")
                    },
                    actionLabel = if (snapshot.notificationListenerEnabled) "Configure in Settings" else "Enable Notification Access",
                    testTag = "perm_card_notification_listener"
                )
            }

            item {
                PermissionItemCard(
                    title = "Foreground Wake-Word Microphone Service",
                    isGranted = snapshot.wakeWordServiceOperational,
                    icon = Icons.Default.RecordVoiceOver,
                    whyNeeded = "Runs a visible foreground notification to listen for 'Hey MAX' or 'MAX' only when explicitly enabled in Settings and Microphone permission is granted.",
                    setupGuide = "Current status: $wakeServiceStatus",
                    onActionClick = {
                        onOpenSystemSettingsForPermission(Manifest.permission.RECORD_AUDIO)
                    },
                    actionLabel = "App Permission Settings",
                    testTag = "perm_card_wake_service"
                )
            }

            item {
                PermissionItemCard(
                    title = "Location Access (ACCESS_FINE_LOCATION)",
                    isGranted = snapshot.locationGranted,
                    icon = Icons.Default.LocationOn,
                    whyNeeded = "Required to capture your live Google Maps coordinates when you ask 'Meri location Rahul ko bhejo' or 'Where am I?'.",
                    setupGuide = "Tap 'Authorize Location' to grant GPS location access for real-time map links.",
                    onActionClick = {
                        if (snapshot.locationGranted) {
                            onOpenSystemSettingsForPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            onRequestPermissionWithRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    actionLabel = if (snapshot.locationGranted) "Manage / Revoke" else "Authorize Location",
                    testTag = "perm_card_location"
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    isGranted: Boolean,
    icon: ImageVector,
    whyNeeded: String,
    setupGuide: String,
    onActionClick: () -> Unit,
    actionLabel: String,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isGranted) MaxEmeraldSuccess else MaxAmberWarning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    color = (if (isGranted) MaxEmeraldSuccess else MaxAmberWarning).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (isGranted) MaxEmeraldSuccess else MaxAmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGranted) "AUTHORIZED" else "NOT GRANTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGranted) MaxEmeraldSuccess else MaxAmberWarning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Why needed: $whyNeeded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = setupGuide,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_button")
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleModal(
    request: MaxViewModel.PermissionRationaleRequest,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Permission Explanation",
                tint = MaxElectricCyan
            )
        },
        title = {
            Text(text = request.title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                text = request.explanation,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                modifier = Modifier.testTag("rationale_proceed_button")
            ) {
                Text(if (request.isSystemSettingsIntent) "Open System Settings" else "Continue")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("rationale_cancel_button")
            ) {
                Text("Not Now")
            }
        }
    )
}

@Composable
fun MaxAuditHistoryScreen(
    actionLogs: List<ActionLogEntity>,
    trustedRules: List<TrustedRuleEntity>,
    onClearLogs: () -> Unit,
    onToggleTrustedRule: (Long, Boolean) -> Unit,
    onDeleteTrustedRule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("audit_history_list"),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy-Respecting Action History",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Logs operational outcomes and verification evidence while automatically redacting private message bodies and phone digits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (actionLogs.isNotEmpty()) {
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_audit_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear action history"
                            )
                        }
                    }
                }
            }

            if (trustedRules.isNotEmpty()) {
                item {
                    Text(
                        text = "Narrowly Scoped Trusted Rules",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(trustedRules, key = { it.id }) { rule ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.description,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Tool: ${rule.toolName} • Scope: ${rule.scopeKey}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaxElectricCyan
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { onToggleTrustedRule(rule.id, it) }
                            )
                            IconButton(onClick = { onDeleteTrustedRule(rule.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete trusted rule"
                                )
                            }
                        }
                    }
                }
            }

            if (actionLogs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaxElectricBlue,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No device actions logged yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Execute any voice or text command (e.g., 'Instagram kholo' or 'Volume badhao') to view verified execution logs here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(actionLogs, key = { it.id }) { log ->
                    val timeStr = remember(log.timestamp) {
                        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${log.toolName} • ${log.status}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (log.status == "VERIFIED_SUCCESS") MaxEmeraldSuccess else MaxAmberWarning
                                )
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.sanitizedSummary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Evidence: ${log.verificationDetails}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaxSettingsScreen(
    prefs: MaxUserPreferences,
    availableVoices: List<VoiceInfoItem>,
    wakeServiceActive: Boolean,
    wakeServiceStatus: String,
    userEmail: String? = null,
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onUpdateLanguageStyle: (String) -> Unit,
    onUpdateSpeechLocale: (String) -> Unit,
    onUpdatePreferMaleVoice: (Boolean) -> Unit,
    onUpdateSelectedVoice: (String) -> Unit,
    onUpdateSpeechRate: (Float) -> Unit,
    onUpdateSpeechPitch: (Float) -> Unit,
    onUpdateAutoSpeak: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onUpdateWakePhrases: (String) -> Unit,
    onUpdateDarkMode: (Boolean) -> Unit,
    userMemories: List<MemoryItemEntity> = emptyList(),
    onUpdatePersonality: (String) -> Unit = {},
    onUpdateMemoryConsent: (Boolean) -> Unit = {},
    onUpdateWebSearchGrounding: (Boolean) -> Unit = {},
    onUpdateCodingAssistantMode: (Boolean) -> Unit = {},
    onUpdateVoiceStylePreset: (String) -> Unit = {},
    onUpdateUseGeminiNeuralTts: (Boolean) -> Unit = {},
    onUpdateGeminiVoiceName: (String) -> Unit = {},
    onUpdateExplanationLevel: (String) -> Unit = {},
    onUpdateAdaptiveLearning: (Boolean) -> Unit = {},
    onResetPersonalization: () -> Unit = {},
    onAddManualMemory: (String) -> Unit = {},
    onAddCategorizedMemory: (String, String) -> Unit = { fact, _ -> onAddManualMemory(fact) },
    onEditMemory: (Long, String, String) -> Unit = { _, _, _ -> },
    onDeleteMemory: (Long) -> Unit = {},
    onClearAllMemories: () -> Unit = {},
    onTestVoiceOutput: () -> Unit,
    onResetVoiceDefaults: () -> Unit = {},
    updateUiState: UpdateUiState = UpdateUiState.Idle,
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: (AppUpdateInfo) -> Unit = {},
    onInstallAgain: (java.io.File) -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
    onUpdateAutoCheckUpdates: (Boolean) -> Unit = {},
    onUpdateManifestUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var wakePhrasesInput by remember(prefs.wakePhrasesCsv) { mutableStateOf(prefs.wakePhrasesCsv) }
    var personalityInput by remember(prefs.assistantPersonality) { mutableStateOf(prefs.assistantPersonality) }
    var newMemoryInput by remember { mutableStateOf("") }
    var selectedMemoryCategory by remember { mutableStateOf("User Preference") }
    var editingMemoryItem by remember { mutableStateOf<MemoryItemEntity?>(null) }
    var editingMemoryText by remember { mutableStateOf("") }
    var editingMemoryCategory by remember { mutableStateOf("User Preference") }
    var manifestUrlInput by remember(prefs.updateManifestUrl) { mutableStateOf(prefs.updateManifestUrl) }
    var showManifestConfig by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "MAXI Core, Voice & System Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // App Version & Online Updates Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_version_update_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = "App Updates",
                                        tint = MaxElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "App Version & Online Updates",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Current: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = onCheckForUpdates,
                                enabled = updateUiState !is UpdateUiState.Checking,
                                colors = ButtonDefaults.buttonColors(containerColor = MaxElectricCyan),
                                modifier = Modifier.testTag("check_updates_button")
                            ) {
                                if (updateUiState is UpdateUiState.Checking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Checking...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check for Updates", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Status message / feedback chip
                        when (updateUiState) {
                            is UpdateUiState.UpToDate -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaxEmeraldSuccess.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaxEmeraldSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "MAX AI is up to date (v${BuildConfig.VERSION_NAME})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaxEmeraldSuccess
                                        )
                                    }
                                }
                            }
                            is UpdateUiState.UpdateAvailable -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaxElectricCyan.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCheckForUpdates() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = MaxElectricCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "New update available: v${updateUiState.info.versionName}!",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaxElectricCyan
                                            )
                                        }
                                        Text(
                                            text = "View",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaxElectricCyan
                                        )
                                    }
                                }
                            }
                            is UpdateUiState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaxCoralError.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaxCoralError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = updateUiState.message,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaxCoralError
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Automatic check on startup toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Check on App Launch",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Silently checks for updates when MAX AI starts up.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.autoCheckUpdatesOnStartup,
                                onCheckedChange = onUpdateAutoCheckUpdates,
                                modifier = Modifier.testTag("auto_check_updates_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Release Manifest Source",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { showManifestConfig = !showManifestConfig }) {
                                Text(
                                    text = if (showManifestConfig) "Hide URL" else "Configure URL",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (showManifestConfig) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = manifestUrlInput,
                                onValueChange = { manifestUrlInput = it },
                                label = { Text("version.json Endpoint URL") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manifest_url_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        val defaultUrl = "https://raw.githubusercontent.com/princekushwaha/max-ai/main/version.json"
                                        manifestUrlInput = defaultUrl
                                        onUpdateManifestUrl(defaultUrl)
                                    }
                                ) {
                                    Text("Reset to Default", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        onUpdateManifestUrl(manifestUrlInput)
                                        showManifestConfig = false
                                    },
                                    modifier = Modifier.testTag("save_manifest_url_button")
                                ) {
                                    Text("Save URL", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (!userEmail.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Firebase Cloud Sync Active",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaxEmeraldSuccess
                                )
                                Text(
                                    text = "Signed in as $userEmail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onSignOutClick,
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Text("Sign Out")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Local Mode (Cloud Sync Paused)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaxAmberWarning
                                )
                                Text(
                                    text = "Sign in with Google anytime to back up & sync chats across devices.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onSignInClick,
                                modifier = Modifier.testTag("settings_sign_in_button")
                            ) {
                                Text("Sign In")
                            }
                        }
                    }
                }
            }

            // MAXI Core Assistant Personality, Coding Mode, Web Search & Consented Persistent Memory Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "MAXI Core Intelligence & Custom Personality",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaxElectricCyan
                        )
                        Text(
                            text = "Customize MAXI's tone, coding assistance mode, Google Search grounding, and consented persistent memory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = personalityInput,
                            onValueChange = {
                                personalityInput = it
                                onUpdatePersonality(it)
                            },
                            label = { Text("Custom Assistant Personality (MAXI)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("maxi_personality_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "Concise & Practical" to "Natural, respectful, concise, confident, helpful, and practical",
                                "Senior Engineer" to "Expert Senior Software Engineer: rigorous, architecture-focused, clean code",
                                "Friendly Hinglish" to "Warm, friendly Indian Hinglish companion ('Ji Boss'), witty and helpful"
                            ).forEach { (label, preset) ->
                                FilterChip(
                                    selected = personalityInput == preset,
                                    onClick = {
                                        personalityInput = preset
                                        onUpdatePersonality(preset)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Coding Assistance Mode",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Generates production-ready code, debugging analysis, and architecture explanations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.codingAssistantMode,
                                onCheckedChange = onUpdateCodingAssistantMode,
                                modifier = Modifier.testTag("coding_mode_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Web Search Grounding (Google Search)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Grounds live news, weather, prices, and current events with verified web citations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.webSearchGroundingEnabled,
                                onCheckedChange = onUpdateWebSearchGrounding,
                                modifier = Modifier.testTag("web_search_grounding_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = "Adaptive Explanation Depth & Learning Engine",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaxElectricCyan
                        )
                        Text(
                            text = "Choose how MAX AI explains technical or complex topics by default, and whether it adapts from your corrections and feedback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "Adaptive",
                                "Beginner-Friendly",
                                "Concise",
                                "Detailed Step-by-Step"
                            ).forEach { level ->
                                FilterChip(
                                    selected = prefs.explanationLevel == level,
                                    onClick = { onUpdateExplanationLevel(level) },
                                    label = { Text(level, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("explanation_level_chip_${level.lowercase().replace(" ", "_")}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Adaptive Personalization & Feedback Learning",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Learns from your corrections ('Keep it shorter', 'Speak in Hindi') and thumbs-up/down feedback.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.adaptiveLearningEnabled,
                                onCheckedChange = onUpdateAdaptiveLearning,
                                modifier = Modifier.testTag("adaptive_learning_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onResetPersonalization,
                                modifier = Modifier.testTag("reset_personalization_button")
                            ) {
                                Text("Reset Personalization Profile", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Long-Term Memory System (Privacy-Safe)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Stores approved preferences, language/style choices, ongoing projects, and non-sensitive facts. Never saves passwords, OTPs, or secrets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.persistentMemoryConsent,
                                onCheckedChange = onUpdateMemoryConsent,
                                modifier = Modifier.testTag("memory_consent_switch")
                            )
                        }

                        if (prefs.persistentMemoryConsent) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Memory Category:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                listOf(
                                    "User Preference",
                                    "Language & Style",
                                    "Ongoing Project & Goal",
                                    "Important Fact"
                                ).forEach { cat ->
                                    FilterChip(
                                        selected = selectedMemoryCategory == cat,
                                        onClick = { selectedMemoryCategory = cat },
                                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newMemoryInput,
                                    onValueChange = { newMemoryInput = it },
                                    label = { Text("Add memory fact (e.g., I prefer Hindi & simple examples)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_memory_input")
                                )
                                Button(
                                    onClick = {
                                        if (newMemoryInput.isNotBlank()) {
                                            onAddCategorizedMemory(newMemoryInput, selectedMemoryCategory)
                                            newMemoryInput = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("add_memory_button")
                                ) {
                                    Text("Save")
                                }
                            }

                            if (userMemories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Saved Memories (${userMemories.size}):",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaxEmeraldSuccess
                                    )
                                    OutlinedButton(
                                        onClick = onClearAllMemories,
                                        modifier = Modifier.testTag("clear_memories_button")
                                    ) {
                                        Text("Clear All Memories")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                userMemories.take(12).forEach { mem ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .testTag("memory_item_${mem.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "[${mem.category}]",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaxElectricCyan
                                                )
                                                Text(
                                                    text = mem.memoryFact,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        editingMemoryItem = mem
                                                        editingMemoryText = mem.memoryFact
                                                        editingMemoryCategory = mem.category
                                                    },
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .testTag("edit_memory_button_${mem.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit memory",
                                                        tint = MaxElectricCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteMemory(mem.id) },
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .testTag("delete_memory_button_${mem.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete memory",
                                                        tint = MaxCoralError,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No memories stored yet. Say \"Remember that I prefer Hindi\" in chat or add one above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Language & Style Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Language & Response Style",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "MAX AI automatically matches Hindi, English, or Hinglish unless overridden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Auto (Match User)", "Hinglish", "Hindi", "English").forEach { style ->
                                FilterChip(
                                    selected = prefs.preferredLanguageStyle == style,
                                    onClick = { onUpdateLanguageStyle(style) },
                                    label = { Text(style, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("lang_chip_$style")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Speech Recognition Input Locale",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            listOf(
                                "en-IN" to "Indian English / Hinglish",
                                "hi-IN" to "Hindi (हिंदी)",
                                "en-US" to "English (US)"
                            ).forEach { (tag, label) ->
                                FilterChip(
                                    selected = prefs.speechRecognitionLocale == tag,
                                    onClick = { onUpdateSpeechLocale(tag) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("locale_chip_$tag")
                                )
                            }
                        }
                    }
                }
            }

            // Text-to-Speech & Expressive Neural Voice Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Expressive Neural Voice & Speech Output",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaxElectricCyan
                                )
                                Text(
                                    text = "Human-like conversational speech with contextual emotion, natural rhythm, and smooth pauses. Say 'Stop' or 'Chup ho jao' anytime to interrupt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.autoSpeakResponses,
                                onCheckedChange = onUpdateAutoSpeak,
                                modifier = Modifier.testTag("auto_speak_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = "Voice Style & Emotional Delivery Preset",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Adapts pitch, speaking speed, warmth, and sentence pauses to conversation context (Happy, Empathetic, Calm, Storytelling, Romantic, Humorous, Urgent).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.example.voice.VoiceStylePreset.entries.forEach { preset ->
                                val isSelected = prefs.voiceStylePreset.equals(preset.id, ignoreCase = true) ||
                                    prefs.voiceStylePreset.equals(preset.displayTitle, ignoreCase = true)
                                Surface(
                                    color = if (isSelected) {
                                        MaxElectricCyan.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUpdateVoiceStylePreset(preset.id) }
                                        .testTag("voice_style_preset_${preset.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.displayTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isSelected) MaxElectricCyan else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaxEmeraldSuccess
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gemini 2.5 Flash Neural Audio TTS",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Uses gemini-2.5-flash-preview-tts neural audio when GEMINI_API_KEY is active, with automatic zero-drop fallback to Expressive Android Neural TTS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.useGeminiNeuralTts,
                                onCheckedChange = onUpdateUseGeminiNeuralTts,
                                modifier = Modifier.testTag("gemini_neural_tts_switch")
                            )
                        }

                        if (prefs.useGeminiNeuralTts) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gemini Neural Voice Character:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                listOf(
                                    "Puck" to "Puck (Warm)",
                                    "Aoede" to "Aoede (Soft)",
                                    "Charon" to "Charon (Calm)",
                                    "Fenrir" to "Fenrir (Lively)",
                                    "Kore" to "Kore (Clear)"
                                ).forEach { (voiceId, label) ->
                                    FilterChip(
                                        selected = prefs.geminiVoiceName.equals(voiceId, ignoreCase = true),
                                        onClick = { onUpdateGeminiVoiceName(voiceId) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("gemini_voice_chip_$voiceId")
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prefer Male-Sounding Device Voice",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Selects the highest-quality male-sounding Indian English / Hindi device neural TTS voice.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.preferMaleVoice,
                                onCheckedChange = onUpdatePreferMaleVoice,
                                modifier = Modifier.testTag("prefer_male_voice_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Speech Rate: ${String.format(Locale.US, "%.2fx", prefs.speechRate)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(
                                onClick = onResetVoiceDefaults,
                                modifier = Modifier.testTag("reset_voice_defaults_button")
                            ) {
                                Text("Reset to Natural Defaults (0.98x / 1.00)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Slider(
                            value = prefs.speechRate,
                            onValueChange = onUpdateSpeechRate,
                            valueRange = 0.6f..1.6f
                        )

                        Text(
                            text = "Voice Pitch: ${String.format(Locale.US, "%.2f", prefs.speechPitch)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = prefs.speechPitch,
                            onValueChange = onUpdateSpeechPitch,
                            valueRange = 0.6f..1.4f
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaxElectricCyan.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("✨", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "Ultra-Natural Engine: Indian Hinglish phonetics, clause breathing pauses, and Google / Gemini neural voice enabled.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (availableVoices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Installed Device TTS Voices (${availableVoices.size} detected):",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 6.dp)
                            ) {
                                FilterChip(
                                    selected = prefs.selectedVoiceName.isBlank(),
                                    onClick = { onUpdateSelectedVoice("") },
                                    label = {
                                        Text("Auto (Best Neural)", style = MaterialTheme.typography.labelSmall)
                                    },
                                    modifier = Modifier.testTag("voice_chip_auto")
                                )
                                availableVoices.forEach { v ->
                                    val shortName = v.name.takeLast(12)
                                    val neuralBadge = if (v.isNeural) " ★HD" else ""
                                    FilterChip(
                                        selected = prefs.selectedVoiceName == v.name,
                                        onClick = { onUpdateSelectedVoice(v.name) },
                                        label = {
                                            Text(
                                                text = "$shortName$neuralBadge (${if (v.isMaleLikely) "M" else "F"})",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.testTag("voice_chip_${v.name.hashCode()}")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onTestVoiceOutput,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("test_tts_button")
                        ) {
                            Text("Test Ultra-Natural Voice (Hinglish / Hindi)")
                        }
                    }
                }
            }

            // Wake-Word Configuration Card (Section 3.1)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hands-Free Wake-Word Activation (Hey MAX / OK MAX / MAX)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Listens for 'Hey MAX', 'OK MAX', or 'MAX', automatically captures your command, speaks the AI response via TTS, and resumes wake-word detection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.wakeWordEnabled || wakeServiceActive,
                                onCheckedChange = onToggleWakeWord,
                                modifier = Modifier.testTag("wake_word_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Verified Status: $wakeServiceStatus",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (wakeServiceActive) MaxEmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = wakePhrasesInput,
                            onValueChange = {
                                wakePhrasesInput = it
                                onUpdateWakePhrases(it)
                            },
                            label = { Text("Configured Wake Phrases (comma-separated)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wake_phrases_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Android Background & Lock-Screen Wake Architecture:\n• Active Now: Native Kotlin Foreground Service (MaxVoiceWakeService with FOREGROUND_SERVICE_TYPE_MICROPHONE) using Android SpeechRecognizer with automatic pause during command/TTS and automatic resume.\n• True Lock-Screen / Ultra-Low-Power Always-On Spotting: Replace the SpeechRecognizer loop in MaxVoiceWakeService with an on-device AudioRecord DSP engine (e.g., Picovoice PorcupineManager for 'Hey MAX' / 'OK MAX' .ppn keyword models) + PARTIAL_WAKE_LOCK and setShowWhenLocked(true) in MainActivity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Appearance & Known Platform Limitations Disclosure (Section 16)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Obsidian Dark Theme",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = prefs.darkMode,
                                onCheckedChange = onUpdateDarkMode,
                                modifier = Modifier.testTag("dark_mode_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = "Platform Restrictions & Security Disclosures",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaxElectricCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Android 10+ restricts silent background toggling of Wi-Fi and Bluetooth; MAX AI opens the official System Connectivity Panel instead.\n• WhatsApp & WhatsApp Business deep links populate drafts for verified recipients; Android requires a final user tap on Send unless UI automation is explicitly enabled.\n• MAX AI never bypasses login screens, CAPTCHAs, or app security controls, and never reveals OTPs or passwords.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    val currentEditMem = editingMemoryItem
    if (currentEditMem != null) {
        AlertDialog(
            onDismissRequest = { editingMemoryItem = null },
            title = { Text("Edit Saved Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editingMemoryText,
                        onValueChange = { editingMemoryText = it },
                        label = { Text("Memory Fact") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_memory_dialog_input")
                    )
                    OutlinedTextField(
                        value = editingMemoryCategory,
                        onValueChange = { editingMemoryCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingMemoryText.isNotBlank()) {
                            onEditMemory(currentEditMem.id, editingMemoryText, editingMemoryCategory)
                        }
                        editingMemoryItem = null
                    },
                    modifier = Modifier.testTag("save_edited_memory_button")
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingMemoryItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    AppUpdateDialog(
        updateUiState = updateUiState,
        onDownloadAndInstall = onDownloadAndInstallUpdate,
        onInstallAgain = onInstallAgain,
        onDismiss = onDismissUpdateDialog
    )
}
