package com.example.detox.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detox.data.DetoxPreferences
import com.example.detox.service.DetoxTimerService
import com.example.detox.ui.AppPickerBottomSheet
import com.example.detox.ui.DayOfWeekSelector
import com.example.detox.ui.InputDialog
import com.example.detox.ui.RuleAppListSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var showCycleDialog by remember { mutableStateOf(false) }
    var showPendingStopConfirmDialog by remember { mutableStateOf(false) }

    var isActive by remember { mutableStateOf<Boolean>(preferences.isHourlyMonitoringActive()) }
    var isLocked by remember { mutableStateOf<Boolean>(preferences.isHourlyLockedState()) }
    var isPendingStop by remember { mutableStateOf<Boolean>(preferences.isHourlyPendingStopNextCycle()) }
    var lockedEndTime by remember { mutableLongStateOf(preferences.getHourlyLockedUntilTime()) }
    var lockedCycles by remember { mutableIntStateOf(preferences.getHourlyLockedCycles()) }

    val scrollState = rememberScrollState()

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

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
                                    "Pending Stop (At Next Cycle)"
                                else
                                    "Locked State Active ($lockedCycles Cycles)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isPendingStop)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isPendingStop)
                                    "Stopping queued. Apps will remain limited until cycle finishes, then unlock."
                                else
                                    "Locked until ${formatTimestamp(lockedEndTime)}. Stopping active blocks is restricted.",
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
                                "Ends at ${formatTimestamp(lockedEndTime)} ($lockedCycles cycles)"
                            else
                                "Locks active rules until set cycles end",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isLocked,
                        onCheckedChange = { enable ->
                            if (enable) {
                                showCycleDialog = true
                            } else {
                                if (isActive) {
                                    // When locked is active, clicking toggle prompts to disable at next cycle
                                    showPendingStopConfirmDialog = true
                                } else {
                                    preferences.clearHourlyLockedState()
                                    isLocked = false
                                    isPendingStop = false
                                    Toast.makeText(context, "Locked state cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

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
                    // Resets Every Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Current window indicator
                        Text(
                            text = if (windowMins >= 60 && windowMins % 60 == 0) {
                                "${windowMins / 60} hr${if (windowMins / 60 > 1) "s" else ""}"
                            } else if (windowMins >= 60) {
                                "${windowMins / 60}h ${windowMins % 60}m"
                            } else {
                                "$windowMins mins"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                            FilterChip(
                                selected = windowMins == mins,
                                onClick = {
                                    windowMins = mins
                                    preferences.setUsageWindowMins(mins)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        FilterChip(
                            selected = windowMins != 30 && windowMins != 60 && windowMins != 120,
                            onClick = { showCustomWindowDialog = true },
                            label = { Text("Custom", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Time Allowed Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Current allowance indicator
                        Text(
                            text = "$allowanceMins mins",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(5 to "5m", 10 to "10m", 15 to "15m", 30 to "30m").forEach { (mins, label) ->
                            FilterChip(
                                selected = allowanceMins == mins,
                                onClick = {
                                    allowanceMins = mins
                                    preferences.setAllowanceMins(mins)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        FilterChip(
                            selected = allowanceMins != 5 && allowanceMins != 10 && allowanceMins != 15 && allowanceMins != 30,
                            onClick = { showCustomAllowanceDialog = true },
                            label = { Text("Custom", style = MaterialTheme.typography.labelSmall) }
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
                modifier = Modifier.fillMaxWidth()
            )

            // Generous bottom spacer so content scrolls completely above the FAB
            Spacer(modifier = Modifier.height(140.dp))
        }

        // Active / Stop FAB with Locked-State Enforcement
        ExtendedFloatingActionButton(
            onClick = {
                if (isActive) {
                    if (isLocked) {
                        // Locked state active: stopping directly is prohibited; offer next-cycle stop
                        showPendingStopConfirmDialog = true
                    } else {
                        DetoxTimerService.stopMonitoring(context)
                        preferences.setHourlyMonitoringActive(false)
                        isActive = false
                        Toast.makeText(context, "Hourly limit stopped", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    DetoxTimerService.startHourlyMonitoring(context)
                    preferences.setHourlyMonitoringActive(true)
                    isActive = true
                    Toast.makeText(context, "Hourly limit started", Toast.LENGTH_SHORT).show()
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

    // Dialog asking how many cycles locked state will stay & displaying calculated end time
    if (showCycleDialog) {
        var selectedCyclesCount by remember { mutableIntStateOf(1) }
        val now = System.currentTimeMillis()
        val cycleDurationMs = windowMins * 60_000L
        val calculatedEnd = now + (selectedCyclesCount * cycleDurationMs)
        val endFormatted = remember(selectedCyclesCount, windowMins) {
            SimpleDateFormat("hh:mm a (EEEE)", Locale.getDefault()).format(Date(calculatedEnd))
        }

        AlertDialog(
            onDismissRequest = { showCycleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Locked State")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Locks active rules until selected cycles finish. Stopped blocks safely end at cycle reset.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Select Lock Duration",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2).forEach { count ->
                                FilterChip(
                                    selected = selectedCyclesCount == count,
                                    onClick = { selectedCyclesCount = count },
                                    label = { Text("$count ${if (count == 1) "Cycle" else "Cycles"}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 4).forEach { count ->
                                FilterChip(
                                    selected = selectedCyclesCount == count,
                                    onClick = { selectedCyclesCount = count },
                                    label = { Text("$count ${if (count == 1) "Cycle" else "Cycles"}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Calculated Lock End Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = endFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Duration: ${selectedCyclesCount * windowMins} minutes ($selectedCyclesCount × $windowMins min cycles)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val computedEnd = System.currentTimeMillis() + (selectedCyclesCount * windowMins * 60_000L)
                        preferences.enableHourlyLockedState(selectedCyclesCount, computedEnd)
                        isLocked = true
                        lockedEndTime = computedEnd
                        lockedCycles = selectedCyclesCount
                        isPendingStop = false
                        showCycleDialog = false
                        Toast.makeText(
                            context,
                            "Locked state enabled for $selectedCyclesCount cycles (until ${formatTimestamp(computedEnd)})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("Enable Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCycleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog confirming next-cycle stop when user tries to stop while locked
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
            title = { Text("Locked State Active") },
            text = {
                Text(
                    text = "Running blocks cannot be stopped immediately while Locked State is active.\n\nWould you like to schedule stopping for the NEXT cycle reset? Once the current cycle ends, the block will safely turn off."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        preferences.setHourlyPendingStopNextCycle(true)
                        isPendingStop = true
                        showPendingStopConfirmDialog = false
                        Toast.makeText(
                            context,
                            "Block will be stopped at the next cycle reset.",
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