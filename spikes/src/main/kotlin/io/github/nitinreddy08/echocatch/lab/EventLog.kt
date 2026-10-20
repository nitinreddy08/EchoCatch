// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Append-only JSON-lines log that survives process death, so overnight runs can be read the next morning. */
object EventLog {
    private const val FILE_NAME = "lab-events.jsonl"
    private val lock = Any()

    fun append(context: Context, type: String, data: JSONObject = JSONObject()) {
        val now = System.currentTimeMillis()
        val event = JSONObject()
            .put("type", type)
            .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now)))
            .put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
            .put("data", data)
        synchronized(lock) {
            File(context.filesDir, FILE_NAME).appendText(event.toString() + "\n")
        }
    }

    fun lines(context: Context, maxLines: Int = 500): List<String> = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) emptyList() else file.readLines().takeLast(maxLines)
    }

    fun clear(context: Context) {
        synchronized(lock) { File(context.filesDir, FILE_NAME).delete() }
    }
}
