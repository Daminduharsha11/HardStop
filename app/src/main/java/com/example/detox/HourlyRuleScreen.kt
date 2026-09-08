package com.example.detox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.detox.data.DetoxPreferences
import com.example.detox.ui.AppPickerBottomSheet
import com.example.detox.ui.DayOfWeekSelector
import com.example.detox.ui.InputDialog
import com.example.detox.ui.RuleAppListSection

@Composable
fun HourlyRuleScreen(preferences: DetoxPreferences) {
    var windowMins by remember { mutableIntStateOf(preferences.getUsageWindowMins()) }
    var allowanceMins by remember { mutableIntStateOf(preferences.getAllowanceMins()) }
    var activeDays by remember { mutableStateOf<Set<Int>>(preferences.getHourlyDays()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(preferences.getHourlyApps()) }

    var showCustomWindowDialog by remember { mutableStateOf(false) }
    var showCustomAllowanceDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Usage Window & Limit",
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
                // Window Period Configuration
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Window Period",
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

                // Allowed Usage Configuration
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Allowed Usage Per Period",
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
            title = "Hourly Limited Apps",
            emptyText = "No apps assigned to hourly rule",
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