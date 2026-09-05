package com.example.detox

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detox.data.DetoxPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String
)

class MainActivity : ComponentActivity() {
    private lateinit var preferences: DetoxPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = DetoxPreferences(this)

        setContent {
            MaterialTheme {
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    val tabs = listOf("Hourly Limit", "Night Block")

    // Check permission state when user resumes/interacts
    LaunchedEffect(Unit) {
        hasPermission = hasUsageStatsPermission(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Usage Access Permission Warning Banner ---
        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
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
                        color = Color(0xFF856404),
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    ) {
                        Text("Grant", fontWeight = FontWeight.Bold, color = Color(0xFF856404))
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

        when (selectedTab) {
            0 -> HourlyRuleScreen(preferences)
            1 -> NightRuleScreen(preferences)
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
                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
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
    var showAppPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Usage Window & Limit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reset Window Period", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60 to "Every 1 hr", 120 to "Every 2 hrs").forEach { (mins, label) ->
                        FilterChip(
                            selected = windowMins == mins,
                            onClick = {
                                windowMins = mins
                                preferences.setUsageWindowMins(mins)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Allowed Usage Per Window", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "10 mins", 15 to "15 mins", 30 to "30 mins").forEach { (mins, label) ->
                        FilterChip(
                            selected = allowanceMins == mins,
                            onClick = {
                                allowanceMins = mins
                                preferences.setAllowanceMins(mins)
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Active Days", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))
        DayOfWeekSelector(selectedDays = activeDays) { day ->
            activeDays = if (activeDays.contains(day)) activeDays - day else activeDays + day
            preferences.setHourlyDays(activeDays)
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
                Text("No apps assigned to hourly rule.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selectedApps.toList()) { pkg ->
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
                                selectedApps = selectedApps - pkg
                                preferences.setHourlyApps(selectedApps)
                            }) {
                                Text("✕", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerBottomSheet(
            selectedPackages = selectedApps,
            onPackageToggled = { pkg ->
                selectedApps = if (selectedApps.contains(pkg)) selectedApps - pkg else selectedApps + pkg
                preferences.setHourlyApps(selectedApps)
            },
            onDismissRequest = { showAppPicker = false }
        )
    }
}

@Composable
fun NightRuleScreen(preferences: DetoxPreferences) {
    var activeDays by remember { mutableStateOf(preferences.getNightDays()) }
    var selectedApps by remember { mutableStateOf(preferences.getNightApps()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val (startHour, startMin) = preferences.getNightStart()
    val (endHour, endMin) = preferences.getNightEnd()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Scheduled Night Lock", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Lock Active Window (Combines All Apps)", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = String.format("%02d:%02d  ➜  %02d:%02d", startHour, startMin, endHour, endMin),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD32F2F)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Active Days", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))
        DayOfWeekSelector(selectedDays = activeDays) { day ->
            activeDays = if (activeDays.contains(day)) activeDays - day else activeDays + day
            preferences.setNightDays(activeDays)
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
                Text("No apps assigned to night block.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selectedApps.toList()) { pkg ->
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
                                selectedApps = selectedApps - pkg
                                preferences.setNightApps(selectedApps)
                            }) {
                                Text("✕", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerBottomSheet(
            selectedPackages = selectedApps,
            onPackageToggled = { pkg ->
                selectedApps = if (selectedApps.contains(pkg)) selectedApps - pkg else selectedApps + pkg
                preferences.setNightApps(selectedApps)
            },
            onDismissRequest = { showAppPicker = false }
        )
    }
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
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                .map { appInfo ->
                    InstalledApp(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString()
                    )
                }
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
                text = "Select Applications",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.6f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(installedApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPackageToggled(app.packageName) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}