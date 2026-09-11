package com.example.detox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.detox.data.DetoxPreferences
import com.example.detox.service.DetoxTimerService
import com.example.detox.ui.AppPickerBottomSheet
import com.example.detox.ui.DayOfWeekSelector
import com.example.detox.ui.InputDialog
import com.example.detox.ui.RuleAppListSection

@Composable
fun HourlyRuleScreen(preferences: DetoxPreferences) {
    val context = LocalContext.current

    var windowMins by remember { mutableIntStateOf(preferences.getUsageWindowMins()) }
    var allowanceMins by remember { mutableIntStateOf(preferences.getAllowanceMins()) }
    var activeDays by remember { mutableStateOf<Set<Int>>(preferences.getHourlyDays()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(preferences.getHourlyApps()) }

    var showCustomWindowDialog by remember { mutableStateOf(false) }
    var showCustomAllowanceDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    var isActive by remember { mutableStateOf(preferences.isHourlyMonitoringActive()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 80.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Usage Limit",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Apps get cut off after your allowed time, then wait for the next reset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Limit Settings",
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
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Resets Every",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            label = {
                                Text(
                                    if (windowMins != 60 && windowMins != 120) "$windowMins mins" else "Custom..."
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Time Allowed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            label = {
                                Text(
                                    if (allowanceMins != 15 && allowanceMins != 30) "$allowanceMins mins" else "Custom..."
                                )
                            }
                        )
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
                preferences.setHourlyDays(updated)
            }

            Spacer(modifier = Modifier.height(24.dp))

            RuleAppListSection(
                title = "Limited Apps",
                emptyText = "No apps assigned to this limit",
                selectedApps = selectedApps,
                onManageClick = { showAppPicker = true },
                onRemoveApp = { pkg ->
                    val updated = selectedApps - pkg
                    selectedApps = updated
                    preferences.setHourlyApps(updated)
                },
                modifier = Modifier.weight(1f)
            )
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (isActive) {
                    DetoxTimerService.stopMonitoring(context)
                    preferences.setHourlyMonitoringActive(false)
                    isActive = false
                    Toast.makeText(context, "Hourly limit stopped", Toast.LENGTH_SHORT).show()
                } else {
                    DetoxTimerService.startHourlyMonitoring(context)
                    preferences.setHourlyMonitoringActive(true)
                    isActive = true
                    Toast.makeText(context, "Hourly limit started", Toast.LENGTH_SHORT).show()
                }
            },
            icon = {
                Icon(
                    if (isActive) Icons.Default.Bolt else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            },
            text = { Text(if (isActive) "Active" else "Start") },
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isActive)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showCustomWindowDialog) {
        InputDialog(
            title = "Set Reset Interval (Minutes)",
            initialValue = windowMins.toString(),
            onDismiss = { showCustomWindowDialog = false },
            onConfirm = { inputMins ->
                inputMins.toIntOrNull()?.let {
                    if (it > 0) {
                        windowMins = it
                        preferences.setUsageWindowMins(it)
                        Toast.makeText(context, "Resets every $it min", Toast.LENGTH_SHORT).show()
                    }
                }
                showCustomWindowDialog = false
            }
        )
    }

    if (showCustomAllowanceDialog) {
        InputDialog(
            title = "Set Time Allowed (Minutes)",
            initialValue = allowanceMins.toString(),
            onDismiss = { showCustomAllowanceDialog = false },
            onConfirm = { inputMins ->
                inputMins.toIntOrNull()?.let {
                    if (it > 0) {
                        allowanceMins = it
                        preferences.setAllowanceMins(it)
                        Toast.makeText(context, "Time allowed: $it min", Toast.LENGTH_SHORT).show()
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