package com.aegis.hardstop.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ShizukuPackageEngine
 * Handles privileged package suspension and binder lifecycle management.
 * 
 * CRASH & RESTART RESILIENCE FIXES:
 * 1. Tracks Binder connection state with listener callbacks.
 * 2. Queues pending suspensions when Shizuku crashes or binder is dead.
 * 3. Automatically flushes and re-enforces queued suspensions when Shizuku restarts.
 * 4. Safe reflection wrapper that catches DeadObjectException / RemoteException without leaking threads.
 * 5. Returns real execution status so caller knows whether suspension actually succeeded.
 */
class ShizukuPackageEngine(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuEngine"
        private const val MAX_RETRIES = 2
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    // Thread-safe map of packages pending suspension state enforcement
    // Key: Package name, Value: Boolean (true = suspend, false = unsuspend)
    private val pendingOperations = ConcurrentHashMap<String, Boolean>()
    private val isBinderAliveState = AtomicBoolean(false)

    // Optional listener to notify services (like DetoxTimerService) of Shizuku state changes
    var onBinderStateChangeListener: ((Boolean) -> Unit)? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku Binder connected/restarted. Verifying permissions...")
        isBinderAliveState.set(true)
        onBinderStateChangeListener?.invoke(true)
        retryPendingOperations()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku Binder died or service crashed.")
        isBinderAliveState.set(false)
        onBinderStateChangeListener?.invoke(false)
    }

    init {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            isBinderAliveState.set(isShizukuAvailable())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku binder listeners", e)
        }
    }

    fun release() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove Shizuku binder listeners", e)
        }
    }

    fun getDiagnostics(): String {
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

        val hasPermission = if (binderAlive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        } else false

        val uid = if (hasPermission) {
            try {
                Shizuku.getUid()
            } catch (e: Exception) {
                -1
            }
        } else -1

        val pendingCount = pendingOperations.size
        return "Binder Alive: $binderAlive | Perm Granted: $hasPermission | UID: $uid | Pending Queue: $pendingCount"
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            } else if (!Shizuku.pingBinder()) {
                showToast("Shizuku Binder not active.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    /**
     * Checks if a package is currently suspended by Android OS without requiring Shizuku IPC.
     */
    fun isPackageSuspended(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use reflection to call hidden PackageManager.isPackageSuspended(String)
                val method = context.packageManager.javaClass.getMethod("isPackageSuspended", String::class.java)
                method.invoke(context.packageManager, packageName) as? Boolean ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes the pm suspend/unsuspend command via Shizuku.
     * Returns true if and only if the command succeeded (exit code 0).
     */
    fun setPackageSuspended(packageName: String, suspend: Boolean): Boolean {
        if (!isShizukuAvailable()) {
            val diag = getDiagnostics()
            Log.w(TAG, "Shizuku not ready when suspending $packageName: $diag. Adding to retry queue.")
            pendingOperations[packageName] = suspend
            return false
        }

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                val action = if (suspend) "suspend" else "unsuspend"
                val shellCommand = arrayOf("pm", action, "--user", "0", packageName)

                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, shellCommand, null, null) as Process

                val stdOutput = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
                val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
                val exitCode = process.waitFor()

                val success = exitCode == 0
                if (success) {
                    Log.d(TAG, "Success ($action): $packageName -> $stdOutput")
                    pendingOperations.remove(packageName)
                    return true
                } else {
                    Log.e(TAG, "Failed ($exitCode): $action $packageName -> $errorOutput")
                    if (attempt >= MAX_RETRIES) {
                        pendingOperations[packageName] = suspend
                        showToast("Failed to $action $packageName ($exitCode)")
                        return false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during $packageName execution (attempt $attempt): ${e.message}", e)
                if (attempt >= MAX_RETRIES) {
                    pendingOperations[packageName] = suspend
                    return false
                }
                // Brief pause before retry in case binder was reconnecting
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {}
            }
        }

        return false
    }

    /**
     * Suspend a set of packages asynchronously on the managed single-thread executor.
     */
    fun suspendPackages(packages: Set<String>, onComplete: ((successful: Set<String>, failed: Set<String>) -> Unit)? = null) {
        if (packages.isEmpty()) {
            onComplete?.invoke(emptySet(), emptySet())
            return
        }

        executor.execute {
            val successful = mutableSetOf<String>()
            val failed = mutableSetOf<String>()

            packages.forEach { pkg ->
                val ok = setPackageSuspended(pkg, true)
                if (ok) successful.add(pkg) else failed.add(pkg)
            }

            if (failed.isNotEmpty()) {
                Log.w(TAG, "${failed.size} packages failed suspension, queued for auto-retry upon Shizuku reconnect.")
            }

            onComplete?.let { cb ->
                mainHandler.post { cb(successful, failed) }
            }
        }
    }

    /**
     * Unsuspend a set of packages asynchronously.
     */
    fun unsuspendPackages(packages: Set<String>, onComplete: ((successful: Set<String>, failed: Set<String>) -> Unit)? = null) {
        if (packages.isEmpty()) {
            onComplete?.invoke(emptySet(), emptySet())
            return
        }

        executor.execute {
            val successful = mutableSetOf<String>()
            val failed = mutableSetOf<String>()

            packages.forEach { pkg ->
                val ok = setPackageSuspended(pkg, false)
                if (ok) successful.add(pkg) else failed.add(pkg)
            }

            onComplete?.let { cb ->
                mainHandler.post { cb(successful, failed) }
            }
        }
    }

    /**
     * Retries any operations that failed previously due to Shizuku crash / unavailability.
     */
    fun retryPendingOperations() {
        if (pendingOperations.isEmpty() || !isShizukuAvailable()) return

        Log.i(TAG, "Retrying ${pendingOperations.size} pending operations after Shizuku binder recovery...")
        executor.execute {
            val entries = ArrayList(pendingOperations.entries)
            entries.forEach { (pkg, shouldSuspend) ->
                val ok = setPackageSuspended(pkg, shouldSuspend)
                if (ok) {
                    pendingOperations.remove(pkg)
                    Log.i(TAG, "Pending operation resolved for $pkg (suspend=$shouldSuspend)")
                }
            }
        }
    }

    fun getPendingOperationsCount(): Int = pendingOperations.size

    /**
     * Executes arbitrary shell commands via Shizuku binder.
     */
    fun executeShellCommand(shellCommand: Array<String>): Pair<Boolean, String> {
        if (!isShizukuAvailable()) return Pair(false, "Shizuku service unreachable")
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, shellCommand, null, null) as Process
            val stdOutput = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Pair(true, stdOutput.ifEmpty { "Success" })
            } else {
                Pair(false, errorOutput.ifEmpty { "Command failed ($exitCode)" })
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Execution error")
        }
    }

    /**
     * Experimental: Edit hosts file to block specified domains.
     */
    fun applyHostsDomainBlock(blockedDomains: Set<String>, enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        executor.execute {
            if (!enable || blockedDomains.isEmpty()) {
                // Reset / restore standard hosts file
                val restoreCmd = arrayOf("sh", "-c", "printf '127.0.0.1 localhost\\n::1 localhost\\n' > /etc/hosts 2>/dev/null || true")
                val res = executeShellCommand(restoreCmd)
                mainHandler.post { onResult?.invoke(res.first, if (res.first) "Hosts domain block disabled." else res.second) }
                return@execute
            }

            val sb = StringBuilder("127.0.0.1 localhost\\n::1 localhost\\n# HardStop Aegis Domain Block\\n")
            blockedDomains.forEach { domain ->
                val clean = domain.trim().lowercase()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .removePrefix("www.")
                    .split("/")[0]
                if (clean.isNotEmpty()) {
                    sb.append("127.0.0.1 ").append(clean).append("\\n")
                    sb.append("127.0.0.1 www.").append(clean).append("\\n")
                    sb.append("0.0.0.0 ").append(clean).append("\\n")
                    sb.append("0.0.0.0 www.").append(clean).append("\\n")
                }
            }

            val content = sb.toString()
            val command = arrayOf("sh", "-c", "printf \"$content\" > /etc/hosts || printf \"$content\" > /system/etc/hosts")
            val result = executeShellCommand(command)

            mainHandler.post {
                val msg = if (result.first) {
                    "Domain block rules applied to hosts file (${blockedDomains.size} domains)."
                } else {
                    "Hosts edit notice: ${result.second} (Root / System RW permission required for hosts file)"
                }
                onResult?.invoke(result.first, msg)
            }
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
