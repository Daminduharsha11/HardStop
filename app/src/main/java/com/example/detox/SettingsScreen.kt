package com.example.detox.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.detox.MainActivity
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine
import com.example.detox.showToast
import rikka.shizuku.Shizuku

enum class ShizukuConnectionStatus {
    RUNNING_AUTHORIZED,
    RUNNING_NOT_AUTHORIZED,
    NOT_RUNNING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: DetoxPreferences,
    shizukuEngine: ShizukuPackageEngine,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var themeMode by remember { mutableIntStateOf(preferences.getThemeMode()) }
    var startOnBoot by remember { mutableStateOf(preferences.getStartOnBoot()) }
    var expandedThemeDropdown by remember { mutableStateOf(false) }

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
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // System Control Engine
        Text(
            text = "System Control Engine",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ShizukuStateCard(shizukuEngine = shizukuEngine)

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance
        Text(
            text = "Appearance",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Theme",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedThemeDropdown,
                    onExpandedChange = { expandedThemeDropdown = !expandedThemeDropdown }
                ) {
                    OutlinedTextField(
                        value = themeOptions.getOrElse(themeMode) { "System Default" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedThemeDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedThemeDropdown,
                        onDismissRequest = { expandedThemeDropdown = false }
                    ) {
                        themeOptions.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    themeMode = index
                                    preferences.setThemeMode(index)
                                    expandedThemeDropdown = false
                                    showToast(context, "Applying theme...")

                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Behavior
        Text(
            text = "Behavior",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Start on Boot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Automatically start background enforcement service when device boots",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = { enabled ->
                        startOnBoot = enabled
                        preferences.setStartOnBoot(enabled)
                        showToast(
                            context,
                            if (enabled) "Start on boot enabled" else "Start on boot disabled"
                        )
                    }
                )
            }
        }

        // Developer Information
        Text(
            text = "About",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        DeveloperCard()
    }
}

@Composable
fun ShizukuStateCard(shizukuEngine: ShizukuPackageEngine) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var connectionStatus by remember { mutableStateOf(ShizukuConnectionStatus.NOT_RUNNING) }
    var runningUid by remember { mutableIntStateOf(-1) }
    var detectedManager by remember { mutableStateOf("Not Installed") }

    fun refreshState() {
        val pm = context.packageManager

        val isBinderAlive = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

        val hasStandard = try {
            pm.resolveContentProvider("moe.shizuku.privileged.api", 0) != null
        } catch (e: Exception) { false }

        val hasPlus = try {
            pm.resolveContentProvider("af.shizuku.plus.api", 0) != null
        } catch (e: Exception) { false }

        detectedManager = when {
            hasStandard || hasPlus || isBinderAlive -> "Shizuku"
            else -> "Not Installed"
        }

        if (!isBinderAlive) {
            connectionStatus = ShizukuConnectionStatus.NOT_RUNNING
            runningUid = -1
            return
        }

        val hasPermission = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        if (hasPermission) {
            connectionStatus = ShizukuConnectionStatus.RUNNING_AUTHORIZED
            runningUid = try { Shizuku.getUid() } catch (e: Exception) { -1 }
        } else {
            connectionStatus = ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED
            runningUid = -1
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shizuku Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                Text(
                    text = when (connectionStatus) {
                        ShizukuConnectionStatus.RUNNING_AUTHORIZED -> "Connected"
                        ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED -> "Unauthorized"
                        ShizukuConnectionStatus.NOT_RUNNING -> "Disconnected"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Detected Engine: $detectedManager",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val descriptionText = when (connectionStatus) {
                ShizukuConnectionStatus.RUNNING_AUTHORIZED -> {
                    val uidType = if (runningUid == 0) "Root (UID 0)" else "ADB (UID $runningUid)"
                    "Service active and authorized. Executing via $uidType."
                }
                ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED -> {
                    "Service is running, but permission has not been granted to Detox."
                }
                ShizukuConnectionStatus.NOT_RUNNING -> {
                    "Service is not running or binder is unreachable. Start Shizuku+ via ADB or Wireless Debugging."
                }
            }

            Text(
                text = descriptionText,
                fontSize = 13.sp,
                color = contentColor.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connectionStatus == ShizukuConnectionStatus.RUNNING_NOT_AUTHORIZED) {
                    Button(
                        onClick = {
                            shizukuEngine.requestShizukuPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = cardContainerColor
                        )
                    ) {
                        Text(text = "Authorize Now", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { refreshState() }
                ) {
                    Text(text = "Refresh", color = contentColor)
                }
            }
        }
    }
}

@Composable
fun DeveloperCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 12.dp)
            )
            Column {
                Text(
                    text = "Damindu Harsha",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Made with ❤️ by Damindu Harsha",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}