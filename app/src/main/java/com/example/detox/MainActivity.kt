package com.example.detox

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val SHIZUKU_CODE = 1001

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndRequestPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        // Handle binder disconnects
    }

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
                AppPickerScreen(
                    onToggleSuspend = { packageName: String, shouldSuspend: Boolean ->
                        val success = ShizukuPackageEngine.setPackageSuspended(packageName, shouldSuspend)
                        if (success) {
                            val status = if (shouldSuspend) "Suspended" else "Unsuspended"
                            Toast.makeText(this, "$status $packageName", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to execute Shizuku command", Toast.LENGTH_SHORT).show()
                        }
                        success
                    },
                    loadApps = { fetchInstalledApps() }
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
fun AppPickerScreen(
    onToggleSuspend: (String, Boolean) -> Boolean,
    loadApps: () -> List<AppInfo>
) {
    var appList by remember { mutableStateOf(loadApps()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aegis Detox") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(appList, key = { it.packageName }) { app ->
                AppRowItem(
                    app = app,
                    onToggle = { shouldSuspend: Boolean ->
                        val success = onToggleSuspend(app.packageName, shouldSuspend)
                        if (success) {
                            appList = appList.map { item ->
                                if (item.packageName == app.packageName) {
                                    item.copy(isSuspended = shouldSuspend)
                                } else item
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun AppRowItem(
    app: AppInfo,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon.toBitmap().asImageBitmap(),
            contentDescription = app.name,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.name, style = MaterialTheme.typography.titleMedium)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = app.isSuspended,
            onCheckedChange = { shouldSuspend: Boolean -> onToggle(shouldSuspend) }
        )
    }
}