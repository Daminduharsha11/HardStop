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

@Composable
fun HourlyRuleScreen(preferences: DetoxPreferences) {
    var windowMins by remember { mutableIntStateOf(preferences.getUsageWindowMins()) }
    var allowanceMins by remember { mutableIntStateOf(preferences.getAllowanceMins()) }
    var activeDays by remember { mutableStateOf<Set<Int>>(preferences.getHourlyDays()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(preferences.getHourlyApps()) }

    var showCustomWindowDialog by remember { mutableStateOf(false) }
    var showCustomAllowanceDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        //UsagePermissionBanner()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "Usage Window & Limit",
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
                        text = "Reset Window Period",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(60 to "1 hr", 120 to "2 hrs").forEach { (mins, label) ->
                            FilterChip(
                                selected = windowMins == mins,
                                onClick = {
                                    windowMins = mins
                                    preferences.setUsageWindowMins(mins)
                                },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = windowMins != 60 && windowMins != 120,
                            onClick = { showCustomWindowDialog = true },
                            label = { Text(if (windowMins != 60 && windowMins != 120) "$windowMins mins" else "Custom...") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Allowed Usage Per Period",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(15 to "15 mins", 30 to "30 mins").forEach { (mins, label) ->
                            FilterChip(
                                selected = allowanceMins == mins,
                                onClick = {
                                    allowanceMins = mins
                                    preferences.setAllowanceMins(mins)
                                },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = allowanceMins != 15 && allowanceMins != 30,
                            onClick = { showCustomAllowanceDialog = true },
                            label = { Text(if (allowanceMins != 15 && allowanceMins != 30) "$allowanceMins mins" else "Custom...") }
                        )
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
                preferences.setHourlyDays(updated)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hourly Limited Apps (${selectedApps.size})",
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
                    Text("No apps assigned to hourly rule.", color = MaterialTheme.colorScheme.outline)
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
                                    preferences.setHourlyApps(updated)
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

    if (showCustomWindowDialog) {
        InputDialog(
            title = "Set Reset Window (Minutes)",
            initialValue = windowMins.toString(),
            onDismiss = { showCustomWindowDialog = false },
            onConfirm = { inputMins ->
                inputMins.toIntOrNull()?.let {
                    if (it > 0) {
                        windowMins = it
                        preferences.setUsageWindowMins(it)
                    }
                }
                showCustomWindowDialog = false
            }
        )
    }

    if (showCustomAllowanceDialog) {
        InputDialog(
            title = "Set Allowed Usage (Minutes)",
            initialValue = allowanceMins.toString(),
            onDismiss = { showCustomAllowanceDialog = false },
            onConfirm = { inputMins ->
                inputMins.toIntOrNull()?.let {
                    if (it > 0) {
                        allowanceMins = it
                        preferences.setAllowanceMins(it)
                    }
                }
                showCustomAllowanceDialog = false
            }
        )
    }

    if (showAppPicker) {
        AppPickerBottomSheet(
            selectedPackages = selectedApps,
            onPackageToggled = { pkg ->
                val updated = if (selectedApps.contains(pkg)) selectedApps - pkg else selectedApps + pkg
                selectedApps = updated
                preferences.setHourlyApps(updated)
            },
            onDismissRequest = { showAppPicker = false }
        )
    }
}