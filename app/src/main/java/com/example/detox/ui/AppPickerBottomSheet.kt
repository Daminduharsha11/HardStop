package com.example.detox.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val iconBitmap: ImageBitmap?
)

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
    var searchQuery by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val apps = pm.queryIntentActivities(mainIntent, 0)
                .map { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val packageName = appInfo.packageName
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val iconBitmap = try {
                        pm.getApplicationIcon(appInfo).toBitmap(48, 48).asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                    InstalledApp(
                        packageName = packageName,
                        label = label,
                        iconBitmap = iconBitmap
                    )
                }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }

            installedApps = apps
            isLoading = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        windowInsets = WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Apps to Detox",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(onClick = onDismissRequest) {
                    Text("Done")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onPackageToggled(app.packageName) },
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .padding(end = 12.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(52.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Switch(
                                    checked = isSelected,
                                    onCheckedChange = { onPackageToggled(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}