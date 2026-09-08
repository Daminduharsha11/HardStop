package com.example.detox.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuPackageEngine(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuEngine"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

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

        return "Binder Alive: $binderAlive | Perm Granted: $hasPermission | UID: $uid"
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
     * Executes a command via Shizuku Process using reflection on the static Shizuku class method.
     */
    private fun execShizukuCommand(cmd: Array<String>): Process {
        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        newProcessMethod.isAccessible = true
        return newProcessMethod.invoke(null, cmd, null, null) as Process
    }

    /**
     * Suspends or unsuspends a package using Shizuku.
     * Must be executed off the main thread.
     */
    fun setPackageSuspended(packageName: String, suspend: Boolean): Boolean {
        if (!isShizukuAvailable()) {
            val diag = getDiagnostics()
            Log.e(TAG, "Shizuku not ready: $diag")
            showToast("Shizuku Not Ready:\n$diag")
            return false
        }

        return try {
            val action = if (suspend) "suspend" else "unsuspend"
            val command = arrayOf("pm", action, "--user", "0", packageName)

            // Safely execute using accessible reflection call
            val process = execShizukuCommand(command)
            val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val stdOutput = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val exitCode = process.waitFor()

            val success = exitCode == 0
            if (success) {
                Log.d(TAG, "Success: $action $packageName -> $stdOutput")
            } else {
                Log.e(TAG, "Failed ($exitCode): $action $packageName -> $errorOutput")
                showToast("FAILED ($exitCode): $packageName\nErr: $errorOutput")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Execution exception for $packageName", e)
            showToast("Error: ${e.localizedMessage}")
            false
        }
    }

    fun suspendPackages(packages: Set<String>) {
        if (packages.isEmpty()) return
        Thread {
            packages.forEach { pkg ->
                setPackageSuspended(pkg, true)
            }
        }.start()
    }

    fun unsuspendPackages(packages: Set<String>) {
        if (packages.isEmpty()) return
        Thread {
            packages.forEach { pkg ->
                setPackageSuspended(pkg, false)
            }
        }.start()
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}