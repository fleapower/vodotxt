package com.vodotxt.data

import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader

object DebugLogger {

    fun getAppLogs(): String {
        return try {
            val pid = Process.myPid().toString()
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.contains(pid) || l.contains("com.vodotxt")) {
                    log.append(l).append("\n")
                }
            }
            reader.close()
            process.destroy()
            if (log.isBlank()) "No logs found for process $pid" else log.toString()
        } catch (e: Exception) {
            "Failed to retrieve logcat: ${e.localizedMessage}"
        }
    }

    fun clear() {
        try {
            val process = Runtime.getRuntime().exec("logcat -c")
            process.waitFor()
            process.destroy()
        } catch (_: Exception) {
            // Defensive fallback
        }
    }
}
