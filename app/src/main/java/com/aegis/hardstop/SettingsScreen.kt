package com.aegis.hardstop

import com.example.R
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aegis.hardstop.data.DetoxPreferences
import com.aegis.hardstop.engine.ShizukuPackageEngine
import com.aegis.hardstop.service.DetoxTimerService
import rikka.shizuku.Shizuku

enum class ShizukuConnectionStatus {
    RUNNING_AUTHORIZED,
    RUNNING_NOT_AUTHORIZED,
    NOT_RUNNING
}

@Composable
fun SettingsScreen(
    preferences: DetoxPreferences,
    shizukuEngine: ShizukuPackageEngine,
    onThemeChanged: (Int) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var themeMode by remember { mutableIntStateOf(preferences.getThemeMode()) }
    var startOnBoot by remember { mutableStateOf(preferences.getStartOnBoot()) }
    var headlessService by remember { mutableStateOf(preferences.isHeadlessServiceEnabled()) }
    var showThemeMenu by remember { mutableStateOf(false) }

    // Experimental state
    var hostsBlockingEnabled by remember { mutableStateOf(preferences.isHostsBlockingEnabled()) }
    var blockedDomains by remember { mutableStateOf(preferences.getBlockedDomains()) }
    var hmacSpoofingEnabled by remember { mutableStateOf(preferences.isHmacSpoofingEnabled()) }

    // Dialog states
    var showDomainsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var newDomainInput by remember { mutableStateOf("") }

    val themeOptions = listOf("System Default", "Light", "Dark", "AMOLED Dark")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. System Control Engine
        SectionHeader("Engine & System Status")
        ShizukuStateCard(shizukuEngine = shizukuEngine)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Preferences & Behavior
        SectionHeader("General Settings")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Theme selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "App Theme", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = themeOptions.getOrElse(themeMode) { "System Default" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        OutlinedCard(
                            onClick = { showThemeMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = themeOptions.getOrElse(themeMode) { "System Default" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Theme",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            themeOptions.forEachIndexed { index, title ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = title,
                                            fontSize = 13.sp,
                                            fontWeight = if (index == themeMode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        themeMode = index
                                        preferences.setThemeMode(index)
                                        showThemeMenu = false
                                        onThemeChanged(index)
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Start on Boot
                CompactSettingToggle(
                    title = "Start on Boot",
                    description = "Launch background service on device startup",
                    checked = startOnBoot,
                    onCheckedChange = { enabled ->
                        startOnBoot = enabled
                        preferences.setStartOnBoot(enabled)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Headless background execution
                CompactSettingToggle(
                    title = "Headless Service",
                    description = "Keep rules active in background with zero UI overhead",
                    checked = headlessService,
                    onCheckedChange = { enabled ->
                        headlessService = enabled
                        preferences.setHeadlessServiceEnabled(enabled)
                        if (enabled) {
                            try {
                                DetoxTimerService.evaluateRules(context)
                                showToast(context, "Headless background monitoring enabled")
                            } catch (_: Exception) {}
                        } else {
                            try {
                                DetoxTimerService.stopAllBackgroundWork(context)
                                context.stopService(Intent(context, DetoxTimerService::class.java))
                                showToast(context, "Headless service stopped - background memory freed")
                            } catch (_: Exception) {}
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Battery Optimization
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isBatteryOptimizedIgnored = try {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                } catch (_: Exception) { false }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Battery Optimization", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isBatteryOptimizedIgnored) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (isBatteryOptimizedIgnored) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Text(
                                    text = if (isBatteryOptimizedIgnored) "UNRESTRICTED" else "OPTIMIZED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isBatteryOptimizedIgnored) "Background execution unrestricted" else " (may delay background rules)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    showToast(context, "Open system settings to manage battery usage")
                                }
                            }
                        }
                    ) {
                        Text(if (isBatteryOptimizedIgnored) "Manage" else "Grant", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. Experimental Section
        SectionHeader("Experimental Features")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Domain Block Host Editing
                CompactSettingToggle(
                    title = "Host File Domain Block",
                    description = "Redirect domain DNS requests to 127.0.0.1 via system hosts file",
                    checked = hostsBlockingEnabled,
                    trailingBadge = {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(
                                text = "ROOT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    },
                    onCheckedChange = { enabled ->
                        hostsBlockingEnabled = enabled
                        preferences.setHostsBlockingEnabled(enabled)
                        try {
                            shizukuEngine.applyHostsDomainBlock(blockedDomains, enabled) { _, msg ->
                                showToast(context, msg)
                            }
                        } catch (e: Exception) {
                            showToast(context, "Hosts edit failed: ${e.message}")
                        }
                    }
                )

                if (hostsBlockingEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${blockedDomains.size} domain(s) blocked",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Row {
                            OutlinedButton(
                                onClick = { showDomainsDialog = true },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Manage Domains", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        shizukuEngine.applyHostsDomainBlock(blockedDomains, true) { _, msg ->
                                            showToast(context, msg)
                                        }
                                    } catch (e: Exception) {
                                        showToast(context, "Hosts sync failed: ${e.message}")
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Sync Hosts", fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Data Export
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Export Configuration Data", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "Export app rules & settings as JSON payload",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                exportedJsonText = preferences.exportDataPayload()
                                showExportDialog = true
                            } catch (e: Exception) {
                                showToast(context, "Export error: ${e.message}")
                            }
                        },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // HMAC Spoofing Option
                CompactSettingToggle(
                    title = "HMAC Signature Spoofing",
                    description = "Attach spoofed HMAC-SHA256 signature token on exported data payload",
                    checked = hmacSpoofingEnabled,
                    onCheckedChange = { enabled ->
                        hmacSpoofingEnabled = enabled
                        preferences.setHmacSpoofingEnabled(enabled)
                        showToast(context, if (enabled) "HMAC Spoofing enabled" else "HMAC Spoofing disabled")
                    }
                )
            }
        }

        // 4. Creator Card
        SectionHeader("About & Creator")
        CreatorCard()
    }

    // --- Domain Management Dialog ---
    if (showDomainsDialog) {
        AlertDialog(
            onDismissRequest = { showDomainsDialog = false },
            title = { Text("Manage Blocked Domains", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDomainInput,
                            onValueChange = { newDomainInput = it },
                            placeholder = { Text("e.g. reddit.com", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val clean = newDomainInput.trim().lowercase().removePrefix("http://").removePrefix("https://").removePrefix("www.")
                                if (clean.isNotEmpty()) {
                                    val updated = blockedDomains + clean
                                    blockedDomains = updated
                                    preferences.setBlockedDomains(updated)
                                    newDomainInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Domain")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (blockedDomains.isEmpty()) {
                            Text("No domains blocked.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        } else {
                            blockedDomains.forEach { domain ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(domain, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    IconButton(
                                        onClick = {
                                            val updated = blockedDomains - domain
                                            blockedDomains = updated
                                            preferences.setBlockedDomains(updated)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDomainsDialog = false
                        if (hostsBlockingEnabled) {
                            try {
                                shizukuEngine.applyHostsDomainBlock(blockedDomains, true) { _, msg -> showToast(context, msg) }
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }

    // --- Export Data Payload Dialog ---
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Payload Data", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (hmacSpoofingEnabled) "⚠️ HMAC Spoofing ACTIVE: Payload signature is spoofed" else "Standard Payload (HMAC Authentic)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hmacSpoofingEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, exportedJsonText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Configuration Export"))
                        } catch (e: Exception) {
                            showToast(context, "Could not share: ${e.message}")
                        }
                        showExportDialog = false
                    }
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun CompactSettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trailingBadge: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (trailingBadge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    trailingBadge()
                }
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun ShizukuStateCard(shizukuEngine: ShizukuPackageEngine) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var connectionStatus by remember { mutableStateOf(ShizukuConnectionStatus.NOT_RUNNING) }
    var runningUid by remember { mutableIntStateOf(-1) }
    var pendingOperationsCount by remember { mutableIntStateOf(0) }

    fun refreshState() {
        try {
            val isBinderAlive = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
            pendingOperationsCount = try { shizukuEngine.getPendingOperationsCount() } catch (_: Throwable) { 0 }

            if (!isBinderAlive) {
                connectionStatus = ShizukuConnectionStatus.NOT_RUNNING
                runningUid = -1
                return
            }

            val hasPermission = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) { false }

            if (hasPermission) {
                connectionStatus = ShizukuConnectionStatus.RUNNING_AUTHORIZED
                runningUid = try { Shizuku.getUid() } catch (_: Throwable) { -1 }
            } else {
                connectionStatus = ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED
                runningUid = -1
            }
        } catch (_: Throwable) {
            connectionStatus = ShizukuConnectionStatus.NOT_RUNNING
            runningUid = -1
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cardContainerColor = when (connectionStatus) {
        ShizukuConnectionStatus.RUNNING_AUTHORIZED -> MaterialTheme.colorScheme.primaryContainer
        ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED -> MaterialTheme.colorScheme.tertiaryContainer
        ShizukuConnectionStatus.NOT_RUNNING -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when (connectionStatus) {
        ShizukuConnectionStatus.RUNNING_AUTHORIZED -> MaterialTheme.colorScheme.onPrimaryContainer
        ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED -> MaterialTheme.colorScheme.onTertiaryContainer
        ShizukuConnectionStatus.NOT_RUNNING -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shizuku Engine",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = when (connectionStatus) {
                            ShizukuConnectionStatus.RUNNING_AUTHORIZED -> if (runningUid == 0) "Connected (Root)" else "Connected (ADB)"
                            ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED -> "Unauthorized"
                            ShizukuConnectionStatus.NOT_RUNNING -> "Disconnected"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (connectionStatus) {
                    ShizukuConnectionStatus.RUNNING_AUTHORIZED -> "Privileged suspension engine active & crash resilient."
                    ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED -> "Shizuku running, but permission is required."
                    ShizukuConnectionStatus.NOT_RUNNING -> "Service offline. Start Shizuku via Wireless Debugging or ADB."
                },
                fontSize = 12.sp,
                color = contentColor.copy(alpha = 0.9f)
            )

            if (pendingOperationsCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ $pendingOperationsCount package operation(s) queued",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (connectionStatus == ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED) {
                    Button(
                        onClick = {
                            try {
                                shizukuEngine.requestShizukuPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                            } catch (e: Exception) {
                                showToast(context, "Permission request failed: ${e.message}")
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = cardContainerColor)
                    ) {
                        Text("Authorize", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { refreshState() },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (pendingOperationsCount > 0 && connectionStatus == ShizukuConnectionStatus.RUNNING_AUTHORIZED) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                shizukuEngine.retryPendingOperations()
                                refreshState()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("Retry ($pendingOperationsCount)", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Developer Card with Safe Vector Logo
 */
/**
 * Creator Card with Safe Vector Logo and Clean Multi-Row Layout
 */
@Composable
fun CreatorCard() {
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Avatar, Creator Name, Subtitle, and Single-Line MIT License Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logo_monochrome),
                            contentDescription = "Aegis Shield Logo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Damindu Harsha",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Creator of HardStop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "MIT License",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Version indicator on the left, Git & Contact buttons on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HardStop v2.9.3",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val gitIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Daminduharsha11/HardStop"))
                                context.startActivity(gitIntent)
                            } catch (_: Exception) {
                                try {
                                    val gitIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Daminduharsha11"))
                                    context.startActivity(gitIntent)
                                } catch (e: Exception) {
                                    showToast(context, "GitHub: https://github.com/Daminduharsha11")
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub Repository",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Git", style = MaterialTheme.typography.labelMedium, fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            try {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:daminduharsha11@gmail.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "Feedback on Aegis HardStop")
                                }
                                context.startActivity(emailIntent)
                            } catch (_: Exception) {
                                showToast(context, "Creator email: daminduharsha11@gmail.com")
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Contact", style = MaterialTheme.typography.labelMedium, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
