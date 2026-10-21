// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Consented test clips (ADR-0012): the owner records short clips of a name being said, other names, ordinary talking
 * and background sound, at known places and distances, so the name detectors can be tested on real voices instead of
 * synthetic ones. The clips stay in this app's private storage until the owner taps Share; "Delete all" removes them.
 * This screen exists only in the throwaway Lab app. The EchoCatch app never stores audio.
 */
class ClipsActivity : Activity() {
    private enum class Kind(val id: String, val title: String, val hasCue: Boolean) {
        NAME("name", "Name clip", true),
        OTHER_NAME("other_name", "Other-name clip", true),
        TALKING("talking", "Talking clip", false),
        BACKGROUND("background", "Background clip", false),
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val recorder by lazy { ClipRecorder(this) }
    private lateinit var prefs: SharedPreferences
    private lateinit var nameField: EditText
    private lateinit var otherField: EditText
    private lateinit var speakerField: EditText
    private lateinit var placeSpinner: Spinner
    private lateinit var distanceSpinner: Spinner
    private lateinit var permissionButton: Button
    private lateinit var statusText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var countsText: TextView
    private lateinit var deleteLastButton: Button
    private val actionButtons = mutableListOf<Button>()
    private var busy = false
    private var pendingStep: Runnable? = null
    private var lastSavedId: Int? = null

    private val places = listOf("Quiet room", "TV or music on", "Street or outdoors", "Crowd or cafe", "Car", "Other")
    private val distances = listOf("0.5 m", "1 m", "2 m", "4 m", "Other room")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())
        refreshPermissionButton()
        updateCounts()
    }

    override fun onPause() {
        pendingStep?.let { main.removeCallbacks(it) }
        pendingStep = null
        if (busy) recorder.cancel()
        savePrefs()
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Screen
    // ---------------------------------------------------------------------------------------------------------------

    private fun buildUi(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD, PAD, PAD, PAD)
        }
        column.addView(heading("Test clips"))
        column.addView(
            note(
                "Record short clips so the name detector can be tested on real voices. Only record people who agree to it. " +
                    "Clips stay on this phone, in this app, until you tap Share. Delete all removes them. " +
                    "Each clip is ${CLIP_SECONDS} seconds from the phone microphone.",
            ),
        )
        permissionButton = button("Allow the microphone") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC) }
        column.addView(permissionButton)

        column.addView(heading("Who and where"))
        nameField = textField("The name to detect (for example Maya)", prefs.getString(KEY_NAME, ""))
        speakerField = textField("Who is speaking (for example me, friend1)", prefs.getString(KEY_SPEAKER, "me"))
        otherField = textField("Another name for the other-name clips (ideally one that sounds a bit like it)", prefs.getString(KEY_OTHER, ""))
        placeSpinner = spinner(places, prefs.getInt(KEY_PLACE, 0))
        distanceSpinner = spinner(distances, prefs.getInt(KEY_DISTANCE, 1))
        column.addView(nameField)
        column.addView(speakerField)
        column.addView(otherField)
        column.addView(labelled("Place", placeSpinner))
        column.addView(labelled("Distance", distanceSpinner))

        column.addView(heading("Record"))
        column.addView(
            note(
                "A good set is about 30 clips: name clips (at least 6 in a quiet room at different distances, 6 with TV or music on, " +
                    "and a few outdoors), 6 other-name clips, 6 talking clips and 4 background clips. " +
                    "A second speaker is very helpful: change \"Who is speaking\" and repeat some name clips.",
            ),
        )
        for (kind in Kind.entries) {
            val recordButton = button("Record: ${kind.title}") { begin(kind) }
            actionButtons += recordButton
            column.addView(recordButton)
        }
        statusText = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, PAD, 0, PAD / 2)
            minLines = 4
        }
        column.addView(statusText)
        levelBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 60 }
        column.addView(levelBar)
        resultText = TextView(this).apply { setPadding(0, PAD / 2, 0, PAD / 2) }
        column.addView(resultText)
        deleteLastButton = button("Delete this clip and record it again") { deleteLast() }.apply { visibility = View.GONE }
        column.addView(deleteLastButton)

        column.addView(heading("Your clips"))
        countsText = TextView(this)
        column.addView(countsText)
        column.addView(button("Share clips (zip)") { share() })
        column.addView(button("Delete all clips") { confirmDeleteAll() })
        return ScrollView(this).apply { addView(column) }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------------------------------

    private fun begin(kind: Kind) {
        if (busy) return
        val name = nameField.text.toString().trim()
        val other = otherField.text.toString().trim()
        when {
            name.isEmpty() -> statusText.text = "Type the name to detect first."
            kind == Kind.OTHER_NAME && other.isEmpty() -> statusText.text = "Type the other name to say first."
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            else -> {
                savePrefs()
                busy = true
                setButtonsEnabled(false)
                resultText.text = ""
                deleteLastButton.visibility = View.GONE
                countdown(kind, name, other, COUNTDOWN_SECONDS)
            }
        }
    }

    private fun countdown(kind: Kind, name: String, other: String, remaining: Int) {
        if (remaining == 0) {
            startRecording(kind, name, other)
            return
        }
        statusText.text = "Get ready... $remaining\n${instruction(kind, name, other)}"
        val step = Runnable { countdown(kind, name, other, remaining - 1) }
        pendingStep = step
        main.postDelayed(step, 1000)
    }

    private fun startRecording(kind: Kind, name: String, other: String) {
        pendingStep = null
        recorder.start(
            CLIP_SECONDS,
            object : ClipRecorder.Listener {
                override fun onProgress(elapsedMs: Long, peakDbfs: Double) {
                    levelBar.progress = (peakDbfs + 60.0).coerceIn(0.0, 60.0).toInt()
                    statusText.text = cue(kind, name, other, elapsedMs)
                }

                override fun onFinished(result: ClipRecorder.Result) {
                    levelBar.progress = 0
                    when (result) {
                        is ClipRecorder.Result.Failed -> finishIdle(result.message)
                        ClipRecorder.Result.Cancelled -> finishIdle("Cancelled. Nothing was saved.")
                        is ClipRecorder.Result.Recorded -> save(kind, name, other, result)
                    }
                }
            },
        )
    }

    private fun save(kind: Kind, name: String, other: String, recorded: ClipRecorder.Result.Recorded) {
        statusText.text = "Saving..."
        val speaker = speakerField.text.toString().trim().ifEmpty { "unknown" }
        val place = placeSpinner.selectedItem.toString()
        val distance = distanceSpinner.selectedItem.toString()
        io.execute {
            val id = ClipStore.nextId(this)
            val fileName = String.format(Locale.US, "c%04d_%s.wav", id, kind.id)
            WavFile.write(File(ClipStore.clipsDir(this), fileName), recorded.samples, recorded.count, ClipRecorder.SAMPLE_RATE_HZ)
            val entry = JSONObject()
                .put("id", id)
                .put("file", fileName)
                .put("kind", kind.id)
                .put("target", name)
                .put("spoken", when (kind) {
                    Kind.NAME -> name
                    Kind.OTHER_NAME -> other
                    else -> ""
                })
                .put("speaker", speaker)
                .put("place", place)
                .put("distance", distance)
                .put("source", "MIC")
                .put("sampleRateHz", ClipRecorder.SAMPLE_RATE_HZ)
                .put("durationS", CLIP_SECONDS)
                .put("recordedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                .put("stats", recorded.stats)
            if (kind.hasCue) {
                entry.put("cueS", CUE_START_S).put("windowS", JSONArray(listOf(CUE_START_S, CUE_END_S)))
            }
            ClipStore.append(this, entry)
            main.post {
                lastSavedId = id
                busy = false
                setButtonsEnabled(true)
                statusText.text = "Saved clip #$id."
                resultText.text = "${kind.title}: ${verdict(kind, recorded.stats)}"
                deleteLastButton.visibility = View.VISIBLE
                updateCounts()
            }
        }
    }

    private fun finishIdle(message: String) {
        busy = false
        setButtonsEnabled(true)
        statusText.text = message
    }

    private fun deleteLast() {
        val id = lastSavedId ?: return
        io.execute {
            ClipStore.remove(this, id)
            main.post {
                lastSavedId = null
                deleteLastButton.visibility = View.GONE
                resultText.text = "Deleted clip #$id."
                updateCounts()
            }
        }
    }

    private fun instruction(kind: Kind, name: String, other: String): String = when (kind) {
        Kind.NAME -> "When the screen says SAY IT NOW, say \"$name\" once, the way a friend would call you. You can add a word like \"hey\" first. Then stay quiet."
        Kind.OTHER_NAME -> "When the screen says SAY IT NOW, say \"$other\" once, like calling a different person."
        Kind.TALKING -> "Talk normally for the whole clip (read something aloud or chat) without ever saying \"$name\"."
        Kind.BACKGROUND -> "Do not speak. Let the normal sound of the place (TV, street, room) be recorded."
    }

    private fun cue(kind: Kind, name: String, other: String, elapsedMs: Long): String {
        val left = (CLIP_SECONDS - elapsedMs / 1000).coerceAtLeast(0)
        val text = when (kind) {
            Kind.NAME, Kind.OTHER_NAME -> {
                val said = if (kind == Kind.NAME) name else other
                when {
                    elapsedMs < CUE_START_S * 1000 -> "Wait..."
                    elapsedMs < CUE_END_S * 1000 -> "SAY IT NOW: \"$said\""
                    else -> "Done. Stay quiet."
                }
            }
            Kind.TALKING -> "Keep talking normally."
            Kind.BACKGROUND -> "Stay quiet."
        }
        return "$text\n(${left}s left)"
    }

    private fun verdict(kind: Kind, stats: JSONObject): String {
        val peak = stats.optDouble("peakDbfs", -120.0)
        val zeros = stats.optDouble("zeroSampleFraction", 0.0)
        val active = stats.optDouble("fractionOfWindowsAboveMinus45Dbfs", 0.0)
        return when {
            zeros > 0.95 -> "Only silence was recorded. Another app may be using the microphone. Delete it and try again."
            peak > -1.0 -> "Too loud, the sound was cut off. Move back a little, delete it and record again."
            kind != Kind.BACKGROUND && active < 0.08 -> "Hardly any sound was picked up. Speak up or move closer, delete it and record again."
            else -> "Looks good (peak ${"%.0f".format(Locale.US, peak)} dBFS)."
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Clips: counts, sharing, deleting
    // ---------------------------------------------------------------------------------------------------------------

    private fun updateCounts() {
        io.execute {
            val counts = ClipStore.countsByKind(this)
            val total = counts.values.sum()
            val megabytes = ClipStore.totalBytes(this) / 1_000_000.0
            val text = "Name ${counts["name"] ?: 0}, other-name ${counts["other_name"] ?: 0}, " +
                "talking ${counts["talking"] ?: 0}, background ${counts["background"] ?: 0}.\n" +
                "$total clips, ${"%.1f".format(Locale.US, megabytes)} MB on this phone."
            main.post { countsText.text = text }
        }
    }

    private fun share() {
        io.execute {
            if (ClipStore.entries(this).isEmpty()) {
                main.post { resultText.text = "There are no clips to share yet." }
                return@execute
            }
            val session = JSONObject()
                .put("schema", 1)
                .put("app", "EchoCatchLab")
                .put("kind", "test-clips")
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("sampleRateHz", ClipRecorder.SAMPLE_RATE_HZ)
                .put("clipSeconds", CLIP_SECONDS)
                .put("cueWindowS", JSONArray(listOf(CUE_START_S, CUE_END_S)))
                .put("createdAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            val zip = ClipStore.buildZip(this, session)
            main.post { sendZip(zip) }
        }
    }

    private fun sendZip(zip: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.clips", zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "EchoCatch test clips")
            clipData = ClipData.newRawUri("EchoCatch test clips", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share test clips"))
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("Delete all clips?")
            .setMessage("Every clip recorded in this app is removed from this phone. This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ ->
                io.execute {
                    ClipStore.removeAll(this)
                    main.post {
                        lastSavedId = null
                        deleteLastButton.visibility = View.GONE
                        resultText.text = "All clips deleted."
                        updateCounts()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Permission, preferences and small view helpers
    // ---------------------------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissionButton()
    }

    private fun refreshPermissionButton() {
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionButton.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun savePrefs() {
        prefs.edit()
            .putString(KEY_NAME, nameField.text.toString())
            .putString(KEY_SPEAKER, speakerField.text.toString())
            .putString(KEY_OTHER, otherField.text.toString())
            .putInt(KEY_PLACE, placeSpinner.selectedItemPosition)
            .putInt(KEY_DISTANCE, distanceSpinner.selectedItemPosition)
            .apply()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        for (button in actionButtons) button.isEnabled = enabled
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, PAD, 0, PAD / 2)
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, 0, 0, PAD / 2)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun textField(hint: String, value: String?) = EditText(this).apply {
        this.hint = hint
        setText(value ?: "")
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun spinner(items: List<String>, selected: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(this@ClipsActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected.coerceIn(0, items.size - 1))
    }

    private fun labelled(label: String, view: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(TextView(this@ClipsActivity).apply { text = label }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private companion object {
        const val PAD = 32
        const val REQUEST_MIC = 2
        const val CLIP_SECONDS = 8
        const val COUNTDOWN_SECONDS = 3
        const val CUE_START_S = 2.0
        const val CUE_END_S = 5.5
        const val PREFS = "clips"
        const val KEY_NAME = "name"
        const val KEY_SPEAKER = "speaker"
        const val KEY_OTHER = "other"
        const val KEY_PLACE = "place"
        const val KEY_DISTANCE = "distance"
    }
}
