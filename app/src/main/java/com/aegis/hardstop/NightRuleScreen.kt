package com.aegis.hardstop.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aegis.hardstop.data.DetoxPreferences
import com.aegis.hardstop.service.DetoxTimerService
import com.aegis.hardstop.ui.AppPickerBottomSheet
import com.aegis.hardstop.ui.DayOfWeekSelector
import com.aegis.hardstop.ui.RuleAppListSection
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightRuleScreen(preferences: DetoxPreferences) {
    val context = LocalContext.current

    var activeDays by remember { mutableStateOf<Set<Int>>(preferences.getNightDays()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(preferences.getNightApps()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val initialStart = remember { preferences.getNightStart() }
    var startHour by remember { mutableIntStateOf(initialStart.first) }
    var startMin by remember { mutableIntStateOf(initialStart.second) }

    val initialEnd = remember { preferences.getNightEnd() }
    var endHour by remember { mutableIntStateOf(initialEnd.first) }
    var endMin by remember { mutableIntStateOf(initialEnd.second) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showPendingStopConfirmDialog by remember { mutableStateOf(false) }

    var isActive by remember { mutableStateOf<Boolean>(preferences.isNightMonitoringActive()) }
    var isLocked by remember { mutableStateOf<Boolean>(preferences.isNightLockedState()) }
    var isPendingStop by remember { mutableStateOf<Boolean>(preferences.isNightPendingStopNextCycle()) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 96.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scheduled Block",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Pick a time range and apps stay locked until it ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Locked State Banner if active
            AnimatedVisibility(
                visible = isLocked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPendingStop)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPendingStop) Icons.Default.HourglassTop else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isPendingStop)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPendingStop)
                                    "Pending Stop (At Window End)"
                                else
                                    "Scheduled Locked State Active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isPendingStop)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isPendingStop)
                                    "Stopping queued. Apps will remain blocked until current scheduled window ends."
                                else
                                    "Locked mode active: Stopping running blocks immediately is prohibited.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPendingStop)
                                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Locked Mode Control Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Locked State",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLocked)
                                "Immediate stop disabled; will stop after current block window finishes"
                            else
                                "Prevents stopping running blocks during active schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isLocked,
                        onCheckedChange = { enable ->
                            if (enable) {
                                preferences.setNightLockedState(true)
                                isLocked = true
                                isPendingStop = false
                                Toast.makeText(context, "Scheduled locked state enabled", Toast.LENGTH_SHORT).show()
                            } else {
                                if (isActive && preferences.isNightBlockActiveNow()) {
                                    showPendingStopConfirmDialog = true
                                } else {
                                    preferences.setNightLockedState(false)
                                    isLocked = false
                                    isPendingStop = false
                                    Toast.makeText(context, "Scheduled locked state cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Block Window",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Nightlight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Block Times",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start Time Block
                        Surface(
                            onClick = { showStartPicker = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Start Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMin),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )

                        // End Time Block
                        Surface(
                            onClick = { showEndPicker = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "End Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMin),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Active Days",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            DayOfWeekSelector(selectedDays = activeDays) { day ->
                val updated = if (activeDays.contains(day)) activeDays - day else activeDays + day
                activeDays = updated
                preferences.setNightDays(updated)
            }

            Spacer(modifier = Modifier.height(24.dp))

            RuleAppListSection(
                title = "Blocked Apps",
                emptyText = "No apps assigned to this block",
                selectedApps = selectedApps,
                onManageClick = { showAppPicker = true },
                onRemoveApp = { pkg ->
                    val updated = selectedApps - pkg
                    selectedApps = updated
                    preferences.setNightApps(updated)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Extra generous bottom spacer so content scrolls completely clear of the FAB
            Spacer(modifier = Modifier.height(140.dp))
        }

        // Active FAB with Locked State protection
        ExtendedFloatingActionButton(
            onClick = {
                if (isActive) {
                    if (isLocked && preferences.isNightBlockActiveNow()) {
                        // Running and locked: prompt to queue stop at window end
                        showPendingStopConfirmDialog = true
                    } else {
                        DetoxTimerService.stopNightMonitoring(context)
                        preferences.setNightMonitoringActive(false)
                        isActive = false
                        Toast.makeText(context, "Scheduled block stopped", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    DetoxTimerService.startNightMonitoring(context)
                    preferences.setNightMonitoringActive(true)
                    isActive = true
                    Toast.makeText(context, "Scheduled block started", Toast.LENGTH_SHORT).show()
                }
            },
            icon = {
                Icon(
                    when {
                        isActive && isPendingStop -> Icons.Default.HourglassTop
                        isActive && isLocked -> Icons.Default.Lock
                        isActive -> Icons.Default.Bolt
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null
                )
            },
            text = {
                Text(
                    when {
                        isActive && isPendingStop -> "Stopping Next Cycle"
                        isActive && isLocked -> "Locked Active"
                        isActive -> "Active"
                        else -> "Start"
                    }
                )
            },
            containerColor = when {
                isActive && isPendingStop -> MaterialTheme.colorScheme.tertiary
                isActive && isLocked -> MaterialTheme.colorScheme.error
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                isActive && isPendingStop -> MaterialTheme.colorScheme.onTertiary
                isActive && isLocked -> MaterialTheme.colorScheme.onError
                isActive -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showPendingStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPendingStopConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Locked Scheduled Block") },
            text = {
                Text(
                    text = "Stopping is prevented during an active locked scheduled window.\n\nWould you like to schedule stopping when the current block window finishes? Once it ends, the block will not resume."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        preferences.setNightPendingStopNextCycle(true)
                        isPendingStop = true
                        showPendingStopConfirmDialog = false
                        Toast.makeText(
                            context,
                            "Scheduled block will terminate after current window finishes.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                ) {
                    Text("Stop Next Cycle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPendingStopConfirmDialog = false }) {
                    Text("Keep Active")
                }
            }
        )
    }

    if (showStartPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMin,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startHour = timePickerState.hour
                    startMin = timePickerState.minute
                    preferences.setNightStart(timePickerState.hour, timePickerState.minute)
                    Toast.makeText(
                        context,
                        String.format(Locale.getDefault(), "Start time set to %02d:%02d", timePickerState.hour, timePickerState.minute),
                        Toast.LENGTH_SHORT
                    ).show()
                    showStartPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    if (showEndPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endHour,
            initialMinute = endMin,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endHour = timePickerState.hour
                    endMin = timePickerState.minute
                    preferences.setNightEnd(timePickerState.hour, timePickerState.minute)
                    Toast.makeText(
                        context,
                        String.format(Locale.getDefault(), "End time set to %02d:%02d", timePickerState.hour, timePickerState.minute),
                        Toast.LENGTH_SHORT
                    ).show()
                    showEndPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    if (showAppPicker) {
        AppPickerBottomSheet(
            selectedPackages = selectedApps,
            onPackageToggled = { pkg ->
                val updated = if (selectedApps.contains(pkg)) selectedApps - pkg else selectedApps + pkg
                selectedApps = updated
                preferences.setNightApps(updated)
            },
            onDismissRequest = { showAppPicker = false }
        )
    }
}