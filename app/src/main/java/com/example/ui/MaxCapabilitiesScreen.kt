package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.AppCandidate
import com.example.data.ScheduledItemEntity
import com.example.ui.theme.MaxElectricBlue
import com.example.ui.theme.MaxElectricCyan
import com.example.ui.theme.MaxEmeraldSuccess

data class CapabilityToolSpec(
    val toolName: String,
    val title: String,
    val parametersSpec: String,
    val description: String,
    val sampleCommand: String,
    val icon: ImageVector
)

@Composable
fun MaxCapabilitiesScreen(
    installedApps: List<AppCandidate>,
    scheduledItems: List<ScheduledItemEntity>,
    onExecuteCommand: (String) -> Unit,
    onLaunchInstalledApp: (AppCandidate) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var appSearchQuery by remember { mutableStateOf("") }

    val capabilitySpecs = remember {
        listOf(
            CapabilityToolSpec(
                toolName = "launch_app",
                title = "Application Launcher",
                parametersSpec = "spoken_name, package_name?, disambiguation_choice?",
                description = "Detects installed applications via PackageManager, resolves canonical names (WhatsApp, Instagram, YouTube, Free Fire MAX, Gemini, Maps, Gmail, Chrome, Settings), disambiguates multiple matches, and verifies launch.",
                sampleCommand = "Instagram kholo",
                icon = Icons.Default.Apps
            ),
            CapabilityToolSpec(
                toolName = "open_settings",
                title = "System Settings Router",
                parametersSpec = "settings_target (wifi, bluetooth, display, battery, sound, applications)",
                description = "Opens authorized Android Settings pages and validates target resolution.",
                sampleCommand = "Wi-Fi settings kholo",
                icon = Icons.Default.Settings
            ),
            CapabilityToolSpec(
                toolName = "adjust_volume",
                title = "Audio Stream Volume Control",
                parametersSpec = "stream (media, ring, alarm), operation (set, increase, decrease, mute, unmute), value?",
                description = "Adjusts real Android AudioManager stream levels and verifies before/after index.",
                sampleCommand = "Media volume 70 karo",
                icon = Icons.Default.VolumeUp
            ),
            CapabilityToolSpec(
                toolName = "control_media",
                title = "Media Playback Controller",
                parametersSpec = "operation (play, pause, stop, next, previous), application?",
                description = "Dispatches hardware/system media key events via AudioManager to active media sessions.",
                sampleCommand = "Next song",
                icon = Icons.Default.PlayArrow
            ),
            CapabilityToolSpec(
                toolName = "control_device_setting",
                title = "Hardware & Connectivity Toggle",
                parametersSpec = "setting (wifi, bluetooth, flashlight), desired_state (on, off)",
                description = "Toggles flashlight via CameraManager or opens the Android 10+ Settings Connectivity Panel with honest platform restriction disclosure.",
                sampleCommand = "Turn on Wi-Fi",
                icon = Icons.Default.Settings
            ),
            CapabilityToolSpec(
                toolName = "prepare_message",
                title = "WhatsApp & WhatsApp Business Workflow",
                parametersSpec = "application, recipient_name?, phone_number?, message, mode (draft|send)",
                description = "Resolves contacts (never sends to an uncertain recipient), requires confirmation before launching WhatsApp draft, and never reports a draft as message delivery.",
                sampleCommand = "WhatsApp message to +919876543210: Meeting at 6 PM",
                icon = Icons.Default.Chat
            ),
            CapabilityToolSpec(
                toolName = "schedule_alarm_or_reminder",
                title = "Alarm & Reminder Scheduler",
                parametersSpec = "time, date?, label, recurrence?",
                description = "Validates time, asks for clarification if AM/PM is ambiguous (e.g., 'Set alarm for 7'), and schedules via AlarmManager + Clock intent.",
                sampleCommand = "7:30 AM ka alarm lagao",
                icon = Icons.Default.AccessAlarm
            ),
            CapabilityToolSpec(
                toolName = "accessibility_action",
                title = "Authorized UI Automation",
                parametersSpec = "operation (tap, scroll, text_entry, home, notifications), target, text?",
                description = "Executes UI taps, scrolling, text entry, and notification shade expansion strictly when MaxAccessibilityService is enabled.",
                sampleCommand = "Scroll down",
                icon = Icons.Default.TouchApp
            )
        )
    }

    val filteredApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(appSearchQuery, ignoreCase = true) ||
                it.packageName.contains(appSearchQuery, ignoreCase = true)
        }
    }

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
                .testTag("capabilities_list"),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Validated Device-Control Tools",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Every capability below executes real Android SDK calls and reports verified outcomes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(capabilitySpecs, key = { it.toolName }) { spec ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = spec.title,
                                    tint = MaxElectricCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = spec.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Tool: \"${spec.toolName}\" (${spec.parametersSpec})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaxElectricBlue
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = spec.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Try: “${spec.sampleCommand}”",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaxEmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onExecuteCommand(spec.sampleCommand) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("run_sample_${spec.toolName}")
                            ) {
                                Text("Run Tool", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Scheduled Alarms & Reminders section
            if (scheduledItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verified Scheduled Alarms (${scheduledItems.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(scheduledItems, key = { it.id }) { alarm ->
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
                            Column {
                                Text(
                                    text = "${alarm.timeText} — ${alarm.label}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Verified via AlarmManager",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaxEmeraldSuccess
                                )
                            }
                            IconButton(
                                onClick = { onDeleteAlarm(alarm.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete alarm"
                                )
                            }
                        }
                    }
                }
            }

            // Installed Applications Inspector (PackageManager Live Detection)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detected Installed Apps (${installedApps.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Live PackageManager scan. Tap any installed app to launch and verify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search apps")
                    },
                    placeholder = { Text("Filter installed apps by name or package...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installed_apps_search_input")
                )
            }

            items(filteredApps.take(40), key = { it.packageName }) { app ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchInstalledApp(app) }
                        .testTag("installed_app_row_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Launch ${app.appName}",
                            tint = MaxElectricCyan
                        )
                    }
                }
            }
        }
    }
}
