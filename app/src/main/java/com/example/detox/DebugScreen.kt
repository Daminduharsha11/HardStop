package com.example.detox

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.detox.engine.ShizukuPackageEngine
import com.example.detox.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun DebugScreen(shizukuEngine: ShizukuPackageEngine) {
    val context = LocalContext.current
    var youtubeUsageTime by remember { mutableStateOf("Loading...") }
    var isTestRunning by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("Idle") }
    var secondsLeft by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    
    val targetPackage = "com.google.android.youtube"

    fun fetchUsageTime() {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            
            val ytStats = stats?.find { it.packageName == targetPackage }
            val totalTimeMillis = ytStats?.totalTimeInForeground ?: 0L
            val minutes = (totalTimeMillis / 1000) / 60
            val seconds = (totalTimeMillis / 1000) % 60
            youtubeUsageTime = "${minutes}m ${seconds}s today"
        } catch (e: Exception) {
            youtubeUsageTime = "Permission required or unavailable"
        }
    }

    // Auto-update usage time and countdown ticker every second
    LaunchedEffect(Unit) {
        while (true) {
            fetchUsageTime()
            if (isTestRunning && secondsLeft > 0) {
                secondsLeft--
            }
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Debug Dashboard", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("YouTube App Usage (Auto-updating)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = youtubeUsageTime)
                Button(
                    onClick = { fetchUsageTime() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Refresh Usage Now")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Shizuku Controls & Test", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Status: $testStatus" + if (isTestRunning) " (${secondsLeft}s left)" else "")
                Spacer(modifier = Modifier.height(8.dp))
                
                // Manual Suspend / Unsuspend buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val success = shizukuEngine.setPackageSuspended(targetPackage, true)
                                withContext(Dispatchers.Main) {
                                    testStatus = if (success) "Manually Suspended" else "Suspend Failed"
                                }
                            }
                        }
                    ) {
                        Text("Suspend Now")
                    }

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val success = shizukuEngine.setPackageSuspended(targetPackage, false)
                                withContext(Dispatchers.Main) {
                                    testStatus = if (success) "Manually Unsuspended" else "Unsuspend Failed"
                                }
                            }
                        }
                    ) {
                        Text("Unsuspend Now")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Test Warning Notification Button
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        NotificationHelper.showWarningNotification(
                            context = context,
                            title = "⚠️ App Suspending Soon!",
                            message = "YouTube will lock in 5 seconds. Wrap up your session."
                        )
                        testStatus = "Warning Notification Fired!"
                    }
                ) {
                    Text("Test Warning Notification Card")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 30s Countdown Test Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        enabled = !isTestRunning,
                        onClick = {
                            isTestRunning = true
                            secondsLeft = 30
                            testStatus = "Waiting..."
                            coroutineScope.launch {
                                while (secondsLeft > 0) {
                                    if (secondsLeft == 5) {
                                        NotificationHelper.showWarningNotification(
                                            context = context,
                                            title = "⚠️ Locking Soon!",
                                            message = "YouTube will suspend in 5 seconds."
                                        )
                                    }
                                    delay(1000L)
                                }
                                testStatus = "Suspending YouTube..."
                                withContext(Dispatchers.IO) {
                                    shizukuEngine.setPackageSuspended(targetPackage, true)
                                }
                                testStatus = "Suspended via 30s Timer!"
                                isTestRunning = false
                            }
                        }
                    ) {
                        Text(if (isTestRunning) "Running (${secondsLeft}s)" else "Test 30s Timer")
                    }

                    Button(
                        onClick = {
                            isTestRunning = false
                            secondsLeft = 0
                            coroutineScope.launch(Dispatchers.IO) {
                                shizukuEngine.setPackageSuspended(targetPackage, false)
                            }
                            testStatus = "Reset & Unsuspended"
                        }
                    ) {
                        Text("Cancel / Reset")
                    }
                }
            }
        }
    }
}