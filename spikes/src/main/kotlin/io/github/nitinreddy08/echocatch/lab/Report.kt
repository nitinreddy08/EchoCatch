// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Accumulates experiment results as JSON. The report never contains audio, Bluetooth addresses or personal data. */
class Report {
    private val root = JSONObject().put("schema", 1).put("app", "EchoCatchLab")

    @Synchronized
    fun put(key: String, value: Any) {
        root.put(key, value)
    }

    @Synchronized
    fun add(section: String, item: JSONObject) {
        val array = root.optJSONArray(section) ?: JSONArray().also { root.put(section, it) }
        array.put(item)
    }

    @Synchronized
    fun lastIn(section: String): JSONObject? {
        val array = root.optJSONArray(section) ?: return null
        return if (array.length() > 0) array.optJSONObject(array.length() - 1) else null
    }

    /** Compact JSON, so a whole report fits in one chat message. */
    @Synchronized
    fun json(): String = root.toString()

    fun save(context: Context) {
        File(context.filesDir, "lab-report.json").writeText(json())
    }
}
