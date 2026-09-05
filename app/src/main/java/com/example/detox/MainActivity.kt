package com.example.detox

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.detox.engine.ShizukuPackageEngine
import com.example.detox.model.AppInfo
import com.example.detox.service.DetoxTimerService
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val SHIZUKU_CODE = 1001

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndRequestPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {}

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku Permission Granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        checkAndRequestPermission()

        setContent {
            MaterialTheme {
                MainDashboardScreen(
                    loadApps = { fetchInstalledApps() },
                    onStartLock = { packages, minutes ->
                        startLockSession(packages, minutes)
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermission() {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_CODE)
            }
        }
    }

    private fun startLockSession(packages: Set<String>, minutes: Int) {
        var successCount = 0
        packages.forEach { pkg ->
            val ok = ShizukuPackageEngine.setPackageSuspended(pkg, true)
            if (ok) successCount++
        }

        if (successCount > 0) {
            DetoxTimerService.startService(this, ArrayList(packages), minutes)
            Toast.makeText(this, "Locked $successCount apps for $minutes mins!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to lock apps. Check Shizuku status.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun fetchInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.filter { app ->
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && app.packageName != packageName
        }.map { app ->
            val isSuspended = (app.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
            AppInfo(
                name = pm.getApplicationLabel(app).toString(),
                packageName = app.packageName,
                icon = pm.getApplicationIcon(app),
                isSuspended = isSuspended
            )
        }.sortedBy { it.name }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    loadApps: () -> List<AppInfo>,
    onStartLock: (Set<String>, Int) -> Unit
) {
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    val selectedPackages = remember { mutableStateListOf<String>() }
    var selectedMinutes by remember { mutableIntStateOf(1) } // Default set to 1 min for quick testing
    val appList by remember { mutableStateOf(loadApps()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aegis Detox") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Focus Control Center",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showAppPickerSheet = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apps (${selectedPackages.size})")
                }

                OutlinedButton(
                    onClick = { showTimerDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Timer (${selectedMinutes}m)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onStartLock(selectedPackages.toSet(), selectedMinutes) },
                enabled = selectedPackages.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Start Focus Session")
            }
        }
    }

    if (showAppPickerSheet) {
        ModalBottomSheet(onDismissRequest = { showAppPickerSheet = false }) {
            AppPickerSheetContent(
                appList = appList,
                selectedPackages = selectedPackages,
                onDone = { showAppPickerSheet = false }
            )
        }
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Set Duration") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun AppPickerSheetContent(
    appList: List<AppInfo>,
    selectedPackages: MutableList<String>,
    onDone: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, appList) {
        if (searchQuery.isBlank()) appList
        else appList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(0.85f)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select Distractions", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onDone) {
                Text("Done")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search apps...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isSelected = selectedPackages.contains(app.packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) selectedPackages.remove(app.packageName)
                            else selectedPackages.add(app.packageName)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.icon.toBitmap().asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            if (isSelected) selectedPackages.remove(app.packageName)
                            else selectedPackages.add(app.packageName)
                        }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}