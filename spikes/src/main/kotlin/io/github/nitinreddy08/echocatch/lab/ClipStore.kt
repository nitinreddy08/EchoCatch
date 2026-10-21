// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The consented test clips of the Lab app (ADR-0012): WAV files plus a JSON-lines manifest, in the app's private
 * storage. Nothing here is uploaded; a clip leaves the phone only when its owner shares the zip.
 */
object ClipStore {
    private const val CLIPS_DIR = "clips"
    private const val SHARE_DIR = "share"
    private const val MANIFEST = "clips.jsonl"

    fun clipsDir(context: Context): File = File(context.filesDir, CLIPS_DIR).apply { mkdirs() }

    fun shareDir(context: Context): File = File(context.filesDir, SHARE_DIR).apply { mkdirs() }

    @Synchronized
    fun entries(context: Context): List<JSONObject> {
        val manifest = File(clipsDir(context), MANIFEST)
        if (!manifest.exists()) return emptyList()
        return manifest.readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
    }

    @Synchronized
    fun nextId(context: Context): Int = (entries(context).maxOfOrNull { it.optInt("id") } ?: 0) + 1

    @Synchronized
    fun append(context: Context, entry: JSONObject) {
        File(clipsDir(context), MANIFEST).appendText(entry.toString() + "\n")
    }

    @Synchronized
    fun remove(context: Context, id: Int) {
        val all = entries(context)
        for (entry in all.filter { it.optInt("id") == id }) File(clipsDir(context), entry.optString("file")).delete()
        File(clipsDir(context), MANIFEST).writeText(all.filter { it.optInt("id") != id }.joinToString("") { it.toString() + "\n" })
    }

    @Synchronized
    fun removeAll(context: Context) {
        File(context.filesDir, CLIPS_DIR).deleteRecursively()
        File(context.filesDir, SHARE_DIR).deleteRecursively()
    }

    fun countsByKind(context: Context): Map<String, Int> =
        entries(context).groupingBy { it.optString("kind") }.eachCount()

    fun totalBytes(context: Context): Long =
        entries(context).sumOf { File(clipsDir(context), it.optString("file")).length() }

    /** Packs the manifest, every clip and a short session description into one zip for the share sheet. */
    @Synchronized
    fun buildZip(context: Context, session: JSONObject): File {
        val share = shareDir(context)
        share.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zip = File(share, "echocatch-clips-$stamp.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            fun add(name: String, bytes: ByteArray) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
            add("session.json", session.toString(2).toByteArray())
            val manifest = File(clipsDir(context), MANIFEST)
            if (manifest.exists()) add(MANIFEST, manifest.readBytes())
            for (entry in entries(context)) {
                val wav = File(clipsDir(context), entry.optString("file"))
                if (wav.exists()) add("clips/${wav.name}", wav.readBytes())
            }
        }
        return zip
    }
}
