package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import com.example.service.AppUpdateInfo
import com.example.ui.AppUpdateDialog
import java.io.File
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MaxAuditHistoryScreen
import com.example.ui.MaxAuthScreen
import com.example.ui.MaxCapabilitiesScreen
import com.example.ui.MaxConsoleScreen
import com.example.ui.MaxPermissionsScreen
import com.example.ui.MaxSettingsScreen
import com.example.ui.PermissionRationaleModal
import com.example.ui.authStateFlow
import com.example.ui.signOutFromMaxAi
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MaxViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

enum class MaxTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    CONSOLE("console", R.string.tab_assistant, Icons.Default.Mic),
    CAPABILITIES("capabilities", R.string.tab_capabilities, Icons.Default.Apps),
    PERMISSIONS("permissions", R.string.tab_permissions, Icons.Default.Security),
    HISTORY("history", R.string.tab_history, Icons.Default.History),
    SETTINGS("settings", R.string.tab_settings, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val firebaseReady = try {
                FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
            } catch (_: Exception) {
                false
            }

            if (firebaseReady) {
                val auth = FirebaseAuth.getInstance()
                val currentUser: FirebaseUser? by auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = auth.currentUser)
                var skippedSignIn by rememberSaveable { mutableStateOf(false) }

                MyApplicationTheme(darkTheme = true) {
                    if (currentUser == null && !skippedSignIn) {
                        MaxAuthScreen(
                            onAuthSuccess = { skippedSignIn = false },
                            onSkipSignIn = { skippedSignIn = true }
                        )
                    } else {
                        val maxViewModel: MaxViewModel = viewModel(key = currentUser?.uid ?: "guest_local")
                        val prefs by maxViewModel.userPreferences.collectAsStateWithLifecycle()
                        MyApplicationTheme(darkTheme = prefs.darkMode) {
                            MaxAiRootApp(
                                viewModel = maxViewModel,
                                userEmail = currentUser?.email,
                                onRequestSignIn = { skippedSignIn = false }
                            )
                        }
                    }
                }
            } else {
                val maxViewModel: MaxViewModel = viewModel()
                val prefs by maxViewModel.userPreferences.collectAsStateWithLifecycle()
                MyApplicationTheme(darkTheme = prefs.darkMode) {
                    MaxAiRootApp(viewModel = maxViewModel)
                }
            }
        }
    }
}

@Composable
fun MaxAiRootApp(
    viewModel: MaxViewModel = viewModel(),
    userEmail: String? = null,
    onRequestSignIn: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversationSessions by viewModel.conversationSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val recognitionStage by viewModel.recognitionStage.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val rmsDbLevel by viewModel.rmsDbLevel.collectAsStateWithLifecycle()
    val waveformHistory by viewModel.waveformHistory.collectAsStateWithLifecycle()
    val recognizedCandidates by viewModel.recognizedCandidates.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val activeVoiceEmotion by viewModel.activeVoiceEmotion.collectAsStateWithLifecycle()
    val activeVoiceEngineLabel by viewModel.activeVoiceEngineLabel.collectAsStateWithLifecycle()
    val wakeServiceActive by viewModel.wakeServiceActive.collectAsStateWithLifecycle()
    val wakeServiceStatus by viewModel.wakeServiceStatusMessage.collectAsStateWithLifecycle()
    val wakePartialTranscript by viewModel.wakePartialTranscript.collectAsStateWithLifecycle()
    val lastActivatedWakePhrase by viewModel.lastActivatedWakePhrase.collectAsStateWithLifecycle()
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val disambiguationPrompt by viewModel.disambiguationPrompt.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val scheduledItems by viewModel.scheduledItems.collectAsStateWithLifecycle()
    val permissionSnapshot by viewModel.permissionSnapshot.collectAsStateWithLifecycle()
    val rationaleRequest by viewModel.permissionRationaleDialog.collectAsStateWithLifecycle()
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
    val trustedRules by viewModel.trustedRules.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val userMemories by viewModel.userMemories.collectAsStateWithLifecycle()
    val callingHudState by viewModel.callingHudState.collectAsStateWithLifecycle()
    val contactSearchQuery by viewModel.contactSearchQuery.collectAsStateWithLifecycle()
    val filteredContacts by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(MaxTab.CONSOLE) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Zero-permission Android Photo Picker for image understanding
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUri(uri)
        }
    }

    // Document picker for PDF, code, JSON, CSV, and text files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUri(uri)
        }
    }

    // BackHandler for drawer or secondary tabs to return to Console
    if (drawerState.isOpen) {
        BackHandler {
            coroutineScope.launch { drawerState.close() }
        }
    } else if (currentTab != MaxTab.CONSOLE) {
        BackHandler {
            currentTab = MaxTab.CONSOLE
        }
    }

    var activePermissionRequestedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionPendingDeletion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showClearAllSessionsDialog by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refreshPermissionsAndApps()
        if (activePermissionRequestedKey == Manifest.permission.RECORD_AUDIO) {
            viewModel.onMicrophonePermissionResult(
                isGranted = isGranted,
                isConsoleTab = currentTab == MaxTab.CONSOLE
            )
        }
        activePermissionRequestedKey = null
    }

    // Request CALL_PHONE and READ_CONTACTS once at startup so voice calling commands execute direct calls immediately
    val startupCallPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndApps()
    }
    val localContext = LocalContext.current
    LaunchedEffect(Unit) {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(localContext, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CALL_PHONE)
            }
            if (ContextCompat.checkSelfPermission(localContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_CONTACTS)
            }
        }
        if (missing.isNotEmpty()) {
            try {
                startupCallPermissionsLauncher.launch(missing.toTypedArray())
            } catch (_: Exception) {
            }
        }
    }

    // Fallback system RecognizerIntent activity launcher when direct SpeechRecognizer is unavailable
    val systemSpeechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val data = activityResult.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            val scores = data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
            viewModel.handleActivitySpeechResult(matches, scores)
        }
    }

    // Re-check permissions automatically whenever app resumes from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsAndApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pendingSession = sessionPendingDeletion
    if (pendingSession != null) {
        val (targetSessionId, targetTitle) = pendingSession
        AlertDialog(
            onDismissRequest = { sessionPendingDeletion = null },
            title = {
                Text(
                    text = "Delete Conversation?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"$targetTitle\"? All messages and action logs in this session will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversationSession(targetSessionId)
                        sessionPendingDeletion = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_session_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionPendingDeletion = null },
                    modifier = Modifier.testTag("cancel_delete_session_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAllSessionsDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllSessionsDialog = false },
            title = {
                Text(
                    text = "Delete All Conversations?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all conversation sessions? All history will be deleted and a fresh session will be started.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllConversationSessions()
                        showClearAllSessionsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_all_sessions_button")
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllSessionsDialog = false },
                    modifier = Modifier.testTag("cancel_delete_all_sessions_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .width(300.dp)
                    .testTag("conversation_navigation_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    // Drawer Brand Header + New Chat Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_max_ai_logo),
                                contentDescription = "MAX AI Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MAX AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateDarkMode(!prefs.darkMode)
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("drawer_theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (prefs.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark or Light Mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ChatGPT-style "+ New Chat" button in sidebar
                    OutlinedButton(
                        onClick = {
                            viewModel.startNewConversationSession()
                            currentTab = MaxTab.CONSOLE
                            coroutineScope.launch { drawerState.close() }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "New Chat",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New chat",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "WORKSPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    MaxTab.entries.forEach { tab ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (currentTab == tab) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.labelRes),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            selected = currentTab == tab,
                            onClick = {
                                currentTab = tab
                                coroutineScope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .testTag("nav_tab_${tab.route}")
                        )
                    }

                    if (conversationSessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CONVERSATIONS (${conversationSessions.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { showClearAllSessionsDialog = true },
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("clear_all_sessions_drawer_btn")
                            ) {
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            conversationSessions.forEach { session ->
                                val isActiveSession = session.sessionId == activeSessionId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActiveSession) MaterialTheme.colorScheme.surfaceVariant
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.switchConversationSession(session.sessionId)
                                            currentTab = MaxTab.CONSOLE
                                            coroutineScope.launch { drawerState.close() }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("drawer_session_${session.sessionId}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = null,
                                            tint = if (isActiveSession) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title.ifBlank { "Conversation" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isActiveSession) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isActiveSession) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (session.lastActiveTopic.isNotBlank()) {
                                                Text(
                                                    text = session.lastActiveTopic,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            sessionPendingDeletion = Pair(
                                                session.sessionId,
                                                session.title.ifBlank { "Conversation" }
                                            )
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_session_btn_${session.sessionId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete conversation session",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userEmail ?: "MAX AI Local Session",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (wakeServiceActive) "Hands-Free Wake: Active" else "HI / EN / Hinglish",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("side_navigation_rail")
                        ) {
                            MaxTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("rail_tab_${tab.route}")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        // Compact header bar when viewing secondary screens so the user can always open the menu or return to chat
                        if (currentTab != MaxTab.CONSOLE) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { coroutineScope.launch { drawerState.open() } },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("secondary_open_drawer_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Open navigation menu"
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(currentTab.labelRes),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { currentTab = MaxTab.CONSOLE },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.testTag("return_to_chat_button")
                                    ) {
                                        Text("Back to Chat", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                MaxTab.CONSOLE -> MaxConsoleScreen(
                                    messages = messages,
                                    assistantState = assistantState,
                                    recognitionStage = recognitionStage,
                                    partialTranscript = partialTranscript,
                                    rmsDbLevel = rmsDbLevel,
                                    waveformHistory = waveformHistory,
                                    recognizedCandidates = recognizedCandidates,
                                    speechLocaleTag = prefs.speechRecognitionLocale,
                                    voiceError = voiceError,
                                    activeVoiceEmotion = activeVoiceEmotion,
                                    activeVoiceEngineLabel = activeVoiceEngineLabel,
                                    autoSpeakEnabled = prefs.autoSpeakResponses,
                                    voiceStylePreset = prefs.voiceStylePreset,
                                    onToggleAutoSpeak = { enabled ->
                                        coroutineScope.launch {
                                            viewModel.preferencesRepo.updateAutoSpeak(enabled)
                                            if (!enabled) viewModel.voiceEngine.stopSpeaking()
                                        }
                                    },
                                    onUpdateVoiceStylePreset = { preset ->
                                        coroutineScope.launch {
                                            viewModel.preferencesRepo.updateVoiceStylePreset(preset)
                                        }
                                    },
                                    onSpeakMessageAloud = { msg ->
                                        viewModel.speakMessageOnDemand(msg)
                                    },
                                    onFeedbackMessage = { msg, feedback ->
                                        viewModel.submitMessageFeedback(msg, feedback)
                                    },
                                    wakeServiceActive = wakeServiceActive,
                                    wakeWordEnabled = prefs.wakeWordEnabled,
                                    wakeServiceStatus = wakeServiceStatus,
                                    wakePartialTranscript = wakePartialTranscript,
                                    lastActivatedWakePhrase = lastActivatedWakePhrase,
                                    onToggleWakeWord = { enabled ->
                                        viewModel.toggleWakeWordService(enabled)
                                    },
                                    darkMode = prefs.darkMode,
                                    onToggleDarkMode = {
                                        coroutineScope.launch {
                                            viewModel.preferencesRepo.updateDarkMode(!prefs.darkMode)
                                        }
                                    },
                                    onOpenNavigationDrawer = {
                                        coroutineScope.launch { drawerState.open() }
                                    },
                                    hideSecurityBanner = prefs.hideSecurityBanner,
                                    pendingConfirmation = pendingConfirmation,
                                    disambiguationPrompt = disambiguationPrompt,
                                    pendingAttachment = pendingAttachment,
                                    callingHudState = callingHudState,
                                    contactSearchQuery = contactSearchQuery,
                                    filteredContacts = filteredContacts,
                                    onContactSearchQueryChange = { query ->
                                        viewModel.updateContactSearchQuery(query)
                                    },
                                    onDismissCallingHud = { viewModel.dismissCallingHud() },
                                    onSelectCallingCandidate = { match, speaker ->
                                        viewModel.selectCallingContactCandidate(match, speaker)
                                    },
                                    onToggleSpeakerHud = { enabled ->
                                        viewModel.toggleSpeakerFromHud(enabled)
                                    },
                                    onPickImageClick = {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onPickFileClick = {
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "application/pdf",
                                                "text/*",
                                                "application/json",
                                                "image/*"
                                            )
                                        )
                                    },
                                    onClearAttachment = { viewModel.clearPendingAttachment() },
                                    onMicClick = { viewModel.startVoiceListening() },
                                    onFinishListeningClick = { viewModel.finishVoiceListening() },
                                    onStopCancelClick = { viewModel.stopOrCancelEverything() },
                                    onLaunchSystemSpeechFallback = {
                                        try {
                                            val intent = viewModel.voiceEngine.buildRecognizerIntent(
                                                prefs.speechRecognitionLocale
                                            )
                                            systemSpeechIntentLauncher.launch(intent)
                                        } catch (_: Exception) {
                                        }
                                    },
                                    onSelectSpeechLocale = { localeTag ->
                                        viewModel.switchSpeechRecognitionLocale(localeTag)
                                    },
                                    onSendCommand = { cmd -> viewModel.submitUserInput(cmd) },
                                    onDismissVoiceError = { viewModel.voiceEngine.clearError() },
                                    onDismissSecurityBanner = {
                                        coroutineScope.launch {
                                            viewModel.preferencesRepo.dismissSecurityBanner()
                                        }
                                    },
                                    onConfirmPending = { saveRule -> viewModel.confirmPendingAction(saveRule) },
                                    onCancelPending = { viewModel.cancelPendingAction() },
                                    onSelectDisambiguation = { candidate ->
                                        viewModel.selectDisambiguatedApp(candidate)
                                    },
                                    onDismissDisambiguation = { viewModel.dismissDisambiguation() },
                                    onClearChat = { viewModel.clearChatHistory() },
                                    onDeleteCurrentSession = { viewModel.deleteConversationSession(activeSessionId) }
                                )

                        MaxTab.CAPABILITIES -> MaxCapabilitiesScreen(
                            installedApps = installedApps,
                            scheduledItems = scheduledItems,
                            onExecuteCommand = { sampleCmd ->
                                currentTab = MaxTab.CONSOLE
                                viewModel.submitUserInput(sampleCmd)
                            },
                            onLaunchInstalledApp = { app ->
                                currentTab = MaxTab.CONSOLE
                                viewModel.selectDisambiguatedApp(app)
                            },
                            onDeleteAlarm = { id -> viewModel.deleteScheduledItem(id) }
                        )

                        MaxTab.PERMISSIONS -> MaxPermissionsScreen(
                            snapshot = permissionSnapshot,
                            wakeServiceStatus = wakeServiceStatus,
                            onRequestPermissionWithRationale = { key ->
                                viewModel.promptPermissionRationaleForKey(key)
                            },
                            onOpenSystemSettingsForPermission = { key ->
                                viewModel.openSystemPermissionPage(key)
                            },
                            onRefreshStatus = { viewModel.refreshPermissionsAndApps() }
                        )

                        MaxTab.HISTORY -> MaxAuditHistoryScreen(
                            actionLogs = actionLogs,
                            trustedRules = trustedRules,
                            onClearLogs = { viewModel.clearAuditLogs() },
                            onToggleTrustedRule = { id, enabled ->
                                viewModel.toggleTrustedRule(id, enabled)
                            },
                            onDeleteTrustedRule = { id -> viewModel.deleteTrustedRule(id) }
                        )

                        MaxTab.SETTINGS -> {
                            val context = LocalContext.current
                            MaxSettingsScreen(
                                prefs = prefs,
                                availableVoices = availableVoices,
                                wakeServiceActive = wakeServiceActive,
                                wakeServiceStatus = wakeServiceStatus,
                                userEmail = userEmail,
                                onSignInClick = onRequestSignIn,
                                onSignOutClick = {
                                    signOutFromMaxAi(
                                        context = context,
                                        credentialManager = CredentialManager.create(context),
                                        onSignOutComplete = {},
                                        scope = coroutineScope
                                    )
                                },
                                onUpdateLanguageStyle = { style ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updatePreferredLanguage(style)
                                }
                            },
                            onUpdateSpeechLocale = { locale ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateSpeechLocale(locale)
                                }
                            },
                            onUpdatePreferMaleVoice = { prefer ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updatePreferMaleVoice(prefer)
                                }
                            },
                            onUpdateSelectedVoice = { voiceName ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateSelectedVoiceName(voiceName)
                                }
                            },
                            onUpdateSpeechRate = { rate ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateSpeechRate(rate)
                                }
                            },
                            onUpdateSpeechPitch = { pitch ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateSpeechPitch(pitch)
                                }
                            },
                            onUpdateAutoSpeak = { enabled ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateAutoSpeak(enabled)
                                }
                            },
                            onToggleWakeWord = { enabled ->
                                viewModel.toggleWakeWordService(enabled)
                            },
                            onUpdateWakePhrases = { phrases ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateWakePhrases(phrases)
                                }
                            },
                            onUpdateDarkMode = { dark ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateDarkMode(dark)
                                }
                            },
                            userMemories = userMemories,
                            onUpdatePersonality = { personality ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateAssistantPersonality(personality)
                                }
                            },
                            onUpdateMemoryConsent = { consented ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updatePersistentMemoryConsent(consented)
                                }
                            },
                            onUpdateWebSearchGrounding = { enabled ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateWebSearchGrounding(enabled)
                                }
                            },
                            onUpdateCodingAssistantMode = { enabled ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateCodingAssistantMode(enabled)
                                }
                            },
                            onUpdateVoiceStylePreset = { preset ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateVoiceStylePreset(preset)
                                }
                            },
                            onUpdateUseGeminiNeuralTts = { enabled ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateUseGeminiNeuralTts(enabled)
                                }
                            },
                            onUpdateGeminiVoiceName = { voiceName ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateGeminiVoiceName(voiceName)
                                }
                            },
                            onUpdateExplanationLevel = { level ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateExplanationLevel(level)
                                }
                            },
                            onUpdateAdaptiveLearning = { enabled ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateAdaptiveLearningEnabled(enabled)
                                }
                            },
                            onResetPersonalization = {
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.resetPersonalizationProfile()
                                }
                            },
                            onAddManualMemory = { fact ->
                                viewModel.addManualMemoryFact(fact)
                            },
                            onAddCategorizedMemory = { fact, category ->
                                viewModel.addManualMemoryFact(fact, category)
                            },
                            onEditMemory = { id, updatedFact, category ->
                                viewModel.editMemoryFact(id, updatedFact, category)
                            },
                            onDeleteMemory = { id ->
                                viewModel.deleteMemoryFact(id)
                            },
                            onClearAllMemories = {
                                viewModel.clearAllMemoryFacts()
                            },
                            onTestVoiceOutput = {
                                val stylePreset = com.example.voice.VoiceStylePreset.fromId(prefs.voiceStylePreset)
                                viewModel.voiceEngine.speak(
                                    text = "Namaste! Main MAX AI hoon. Suniye, ab meri aawaz bilkul natural, warm aur lifelike hai. Chalo, bataiye aaj main aapki kya madad kar sakta hoon?",
                                    prefs = prefs.copy(autoSpeakResponses = true),
                                    detectedLanguage = "Hinglish",
                                    emotion = stylePreset.defaultEmotionOverride ?: com.example.voice.VoiceEmotion.HAPPY,
                                    forceSpeakEvenIfMuted = true
                                )
                            },
                            onResetVoiceDefaults = {
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.resetVoiceToNaturalDefaults()
                                }
                            },
                            updateUiState = updateUiState,
                            onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) },
                            onDownloadAndInstallUpdate = { info -> viewModel.downloadAndInstallUpdate(info) },
                            onInstallAgain = { apkFile -> viewModel.triggerInstallAgain(apkFile) },
                            onDismissUpdateDialog = { viewModel.dismissUpdateDialog() },
                            onUpdateAutoCheckUpdates = { enabled ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateAutoCheckUpdates(enabled)
                                }
                            },
                            onUpdateManifestUrl = { url ->
                                coroutineScope.launch {
                                    viewModel.preferencesRepo.updateManifestUrl(url)
                                }
                            }
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }

    // Global update dialog if update is detected outside of the Settings screen (e.g. on startup in Chat tab)
    if (currentTab != MaxTab.SETTINGS) {
        AppUpdateDialog(
            updateUiState = updateUiState,
            onDownloadAndInstall = { info -> viewModel.downloadAndInstallUpdate(info) },
            onInstallAgain = { apkFile -> viewModel.triggerInstallAgain(apkFile) },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    // Permission Rationale Modal before launching system runtime dialog or settings intent (Section 9)
    val currentRationale = rationaleRequest
    if (currentRationale != null) {
        PermissionRationaleModal(
            request = currentRationale,
            onProceed = {
                val key = currentRationale.permissionKey
                val isSettings = currentRationale.isSystemSettingsIntent
                viewModel.dismissPermissionRationale()
                if (isSettings) {
                    viewModel.openSystemPermissionPage(key)
                } else {
                    activePermissionRequestedKey = key
                    permissionLauncher.launch(key)
                }
            },
            onDismiss = { viewModel.dismissPermissionRationale() }
        )
    }
}
