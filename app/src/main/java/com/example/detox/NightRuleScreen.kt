package com.example.detox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.detox.data.DetoxPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightRuleScreen(preferences: DetoxPreferences) {
    var activeDays by remember { mutableStateOf<Set<Int>>(preferences.getNightDays()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(preferences.getNightApps()) }
    var showAppPicker by remember { mutableStateOf(false) }

    // Read initial times into state wrappers so updates cause a trigger re-render
    val initialStart = preferences.getNightStart()
    var startHour by remember { mutableIntStateOf(initialStart.first) }
    var startMin by remember { mutableIntStateOf(initialStart.second) }

    val initialEnd = preferences.getNightEnd()
    var endHour by remember { mutableIntStateOf(initialEnd.first) }
    var endMin by remember { mutableIntStateOf(initialEnd.second) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        //UsagePermissionBanner()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "Scheduled Night Lock",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Lock Window",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            onClick = { showStartPicker = true },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Start Time", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = String.format("%02d:%02d", startHour, startMin),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text("➜", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        Card(
                            onClick = { showEndPicker = true },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("End Time", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = String.format("%02d:%02d", endHour, endMin),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Active Days",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            DayOfWeekSelector(selectedDays = activeDays) { day ->
                val updated = if (activeDays.contains(day)) activeDays - day else activeDays + day
                activeDays = updated
                preferences.setNightDays(updated)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Night Blocked Apps (${selectedApps.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(onClick = { showAppPicker = true }) {
                    Text("+ Manage Apps")
                }
            }

            if (selectedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No apps assigned to night block.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(selectedApps.toList(), key = { it }) { pkg ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = pkg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = {
                                    val updated = selectedApps - pkg
                                    selectedApps = updated
                                    preferences.setNightApps(updated)
                                }) {
                                    Text("✕", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val timePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMin, is24Hour = true)
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
        val timePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMin, is24Hour = true)
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