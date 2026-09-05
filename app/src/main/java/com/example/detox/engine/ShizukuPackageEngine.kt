package com.example.detox.engine

import rikka.shizuku.Shizuku
import java.lang.reflect.Method

object ShizukuPackageEngine {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun setPackageSuspended(packageName: String, suspend: Boolean): Boolean {
        if (!isShizukuAvailable()) return false

        return try {
            val action = if (suspend) "suspend" else "unsuspend"
            val command = arrayOf("pm", action, packageName)

            // Access Shizuku.newProcess via reflection to bypass visibility restrictions
            val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(null, command, null, null) as Process
            val exitCode = process.waitFor()

            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}