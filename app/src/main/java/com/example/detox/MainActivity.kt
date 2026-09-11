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
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.detox.data.DetoxPreferences
import com.example.detox.engine.ShizukuPackageEngine
import com.example.detox.ui.screens.HourlyRuleScreen
import com.example.detox.ui.screens.NightRuleScreen
import com.example.detox.ui.screens.SettingsScreen
import rikka.shizuku.Shizuku

data class InstalledApp(
    val packageName: String,
    val label: String,
    val iconBitmap: ImageBitmap?
)

class MainActivity : ComponentActivity() {
    private lateinit var preferences: DetoxPreferences
    lateinit var shizukuEngine: ShizukuPackageEngine
        private set

    private var isShizukuGrantedState = mutableStateOf(false)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d("Shizuku", "Binder received")
        checkShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("Shizuku", "Binder dead")
        checkShizukuState()
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            val isGranted = grantResult == PackageManager.PERMISSION_GRANTED
            isShizukuGrantedState.value = isGranted
            if (isGranted) {
                showToast(this, "Shizuku permission granted")
            } else {
                showToast(this, "Shizuku permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = DetoxPreferences(this)
        shizukuEngine = ShizukuPackageEngine(applicationContext)

        // Register Shizuku event listeners
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        checkShizukuState()

        setContent {
            val context = LocalContext.current
            var selectedTab by remember { mutableIntStateOf(0) }
            val themeMode by remember { mutableIntStateOf(preferences.getThemeMode()) }

            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                1 -> false
                2, 3 -> true
                else -> isSystemDark
            }

            val colorScheme = when {
    isDark && themeMode == 3 -> {
        // Pure AMOLED Dark Scheme without M3 purple tinting
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF212121),
            onPrimaryContainer = Color(0xFFEADDFF),
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF121212), // Subtle dark contrast for cards
            onSurfaceVariant = Color(0xFFC4C4C4),
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color(0xFF121212),
            surfaceTint = Color.Transparent, // Disables elevation tint
            outline = Color(0xFF2C2C2C),
            outlineVariant = Color(0xFF1F1F1F)
        )
    }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark -> darkColorScheme()
    else -> lightColorScheme()
}

            MaterialTheme(colorScheme = colorScheme) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.DateRange, contentDescription = "Hourly Limit") },
                                label = { Text("Usage Limit") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Lock, contentDescription = "Night Block") },
                                label = { Text("Scheduled Block") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            PermissionBanner()

                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "TabTransition"
                            ) { tabIndex ->
                                when (tabIndex) {
                                    0 -> HourlyRuleScreen(preferences = preferences)
                                    1 -> NightRuleScreen(preferences = preferences)
                                    2 -> SettingsScreen(
                                        preferences = preferences,
                                        shizukuEngine = shizukuEngine,
                                        onNavigateBack = { selectedTab = 0 }
                                    )
                                    
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkShizukuState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun checkShizukuState() {
        val available = shizukuEngine.isShizukuAvailable()
        isShizukuGrantedState.value = available
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
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
fun PermissionBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }

            val apps = packages.filter { appInfo ->
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }.map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                val bitmap = try {
                    pm.getApplicationIcon(appInfo).toBitmap(48, 48).asImageBitmap()
                } catch (e: Exception) {
                    null
                }

                InstalledApp(
                    packageName = appInfo.packageName,
                    label = label,
                    iconBitmap = bitmap
                )
            }.sortedBy { it.label.lowercase() }

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
                            app.iconBitmap?.let { bitmap ->
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

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}