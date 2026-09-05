package com.example.detox

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.detox.data.DetoxPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

class MainActivity : ComponentActivity() {
    private lateinit var preferences: DetoxPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = DetoxPreferences(this)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DualRuleDashboardScreen(preferences = preferences)
                }
            }
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
fun DualRuleDashboardScreen(preferences: DetoxPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    val tabs = listOf("Hourly Limit", "Night Block")

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !hasPermission,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Usage Access required to track screen time",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    ) {
                        Text("Grant", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                )
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "TabTransition"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> HourlyRuleScreen(preferences)
                1 -> NightRuleScreen(preferences)
            }
        }
    }
}

@Composable
fun DayOfWeekSelector(
    selectedDays: Set<Int>,
    onDayToggled: (Int) -> Unit
) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dayLabels.forEachIndexed { index, label ->
            val dayNumber = index + 1
            val isSelected = selectedDays.contains(dayNumber)
            FilterChip(
                selected = isSelected,
                onClick = { onDayToggled(dayNumber) },
                label = { 
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun HourlyRuleScreen(preferences: DetoxPreferences) {
    var windowMins by remember { mutableIntStateOf(preferences.getUsageWindowMins()) }
    var allowanceMins by remember { mutableIntStateOf(preferences.getAllowanceMins()) }
    var activeDays by remember { mutableStateOf(preferences.getHourlyDays()) }
    var selectedApps by remember { mutableStateOf(preferences.getHourlyApps()) }
    
    var showCustomWindowDialog by remember { mutableStateOf(false) }
    var showCustomAllowanceDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Usage Window & Limit", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reset Window Period", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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

                Text("Allowed Usage Per Period", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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

        Text("Active Days", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
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
            Text("Hourly Limited Apps (${selectedApps.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { showAppPicker = true }) {
                Text("+ Manage Apps")
            }
        }

        if (selectedApps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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

    if (showCustomWindowDialog) {
        InputDialog(
            title = "Set Reset Window (Minutes)",
            initialValue = windowMins.toString(),
            onDismiss = { showCustomWindowDialog = false },
            onConfirm = { mins ->
                mins.toIntOrNull()?.let {
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
            onConfirm = { mins ->
                mins.toIntOrNull()?.let {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightRuleScreen(preferences: DetoxPreferences) {
    var activeDays by remember { mutableStateOf(preferences.getNightDays()) }
    var selectedApps by remember { mutableStateOf(preferences.getNightApps()) }
    var showAppPicker by remember { mutableStateOf(false) }

    var (startHour, startMin) = preferences.getNightStart()
    var (endHour, endMin) = preferences.getNightEnd()

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Scheduled Night Lock", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Lock Window", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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

        Text("Active Days", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
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
            Text("Night Blocked Apps (${selectedApps.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { showAppPicker = true }) {
                Text("+ Manage Apps")
            }
        }

        if (selectedApps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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

    if (showStartPicker) {
        val timePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMin, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
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

@Composable
fun InputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { char -> char.isDigit() } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
    selectedPackages: Set<String>,
    onPackageToggled: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            
            // Query all apps that can be launched from the homescreen
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            // Process list off the main thread to prevent UI freezing
            val apps = resolveInfos.mapNotNull { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                
                // Fetch icon smoothly
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                
                InstalledApp(
                    packageName = pkgName,
                    label = label,
                    icon = icon
                )
            }.distinctBy { it.packageName }
             .sortedBy { it.label.lowercase() }

            installedApps = apps
            isLoading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Applications (${installedApps.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.65f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = installedApps,
                        key = { it.packageName }
                    ) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPackageToggled(app.packageName) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Render icon directly from memory cache
                            app.icon?.let { iconDrawable ->
                                val bitmap = remember(app.packageName) {
                                    iconDrawable.toBitmap(48, 48).asImageBitmap()
                                }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 12.dp)
                                )
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onPackageToggled(app.packageName) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}