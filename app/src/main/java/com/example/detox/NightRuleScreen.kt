package com.example.detox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.detox.data.DetoxPreferences
import com.example.detox.ui.AppPickerBottomSheet
import com.example.detox.ui.DayOfWeekSelector
import com.example.detox.ui.RuleAppListSection
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightRuleScreen(preferences: DetoxPreferences) {
    var activeDays by remember { mutableStateOf<Set<Int>>(preferences.getNightDays()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(preferences.getNightApps()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val initialStart = preferences.getNightStart()
    var startHour by remember { mutableIntStateOf(initialStart.first) }
    var startMin by remember { mutableIntStateOf(initialStart.second) }

    val initialEnd = preferences.getNightEnd()
    var endHour by remember { mutableIntStateOf(initialEnd.first) }
    var endMin by remember { mutableIntStateOf(initialEnd.second) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Section
        Text(
            text = "Scheduled Night Lock",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Schedule Configuration Card
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
                        text = "Lock Window",
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

        // Days Selector Section
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

        // Shared RuleAppListSection component for consistent spacing & styling
        RuleAppListSection(
            title = "Night Blocked Apps",
            emptyText = "No apps assigned to night block",
            selectedApps = selectedApps,
            onManageClick = { showAppPicker = true },
            onRemoveApp = { pkg ->
                val updated = selectedApps - pkg
                selectedApps = updated
                preferences.setNightApps(updated)
            },
            modifier = Modifier.weight(1f)
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