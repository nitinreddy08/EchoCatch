// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * EchoCatch Lab: the experiment app for milestone M0. Each section matches a spike in docs/plan/04-spikes.md.
 * Results accumulate in a JSON report that can be shared from the Share buttons. No audio is stored or shared.
 */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var report = Report()
    private lateinit var audioManager: AudioManager
    private lateinit var log: TextView
    private lateinit var deviceButtons: LinearLayout
    private lateinit var methodSpinner: Spinner
    private lateinit var presetSpinner: Spinner
    private lateinit var secondsSpinner: Spinner
    private lateinit var rateSpinner: Spinner
    private lateinit var playerField: EditText
    private lateinit var gainSpinner: Spinner
    private lateinit var usageSpinner: Spinner
    private lateinit var contentSpinner: Spinner
    private lateinit var serviceModeSpinner: Spinner
    private lateinit var hopSpinner: Spinner
    private lateinit var cycleSpinner: Spinner

    private val presets = listOf(
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
        "MIC" to MediaRecorder.AudioSource.MIC,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
    )
    private val gains = listOf(
        "GAIN_TRANSIENT_MAY_DUCK" to AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        "GAIN_TRANSIENT" to AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        "GAIN_TRANSIENT_EXCLUSIVE" to AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        "GAIN" to AudioManager.AUDIOFOCUS_GAIN,
    )
    private val usages = listOf(
        "ASSISTANCE_NAVIGATION_GUIDANCE" to AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        "ASSISTANCE_ACCESSIBILITY" to AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        "ASSISTANT" to AudioAttributes.USAGE_ASSISTANT,
        "NOTIFICATION" to AudioAttributes.USAGE_NOTIFICATION,
        "ALARM" to AudioAttributes.USAGE_ALARM,
        "MEDIA" to AudioAttributes.USAGE_MEDIA,
    )
    private val contentTypes = listOf(
        "SPEECH" to AudioAttributes.CONTENT_TYPE_SPEECH,
        "SONIFICATION" to AudioAttributes.CONTENT_TYPE_SONIFICATION,
        "MUSIC" to AudioAttributes.CONTENT_TYPE_MUSIC,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        setContentView(buildUi())
        appendLog("EchoCatch Lab ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLog("Start with step 1.")
    }

    override fun onDestroy() {
        worker.shutdown()
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
        column.addView(heading("EchoCatch Lab"))
        column.addView(note("Experiment app. It listens only while a test runs and keeps no audio. Nothing leaves your phone unless you tap Share."))

        column.addView(heading("Step 1: set up"))
        column.addView(button("Grant permissions") { requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS) })
        column.addView(button("Scan devices (connect your earbuds first)") { scanDevices() })

        column.addView(heading("Step 2: earbuds microphone test (S1)"))
        column.addView(note("Four short tests, 10 seconds each. Before you start: earbuds in your ears and connected, music playing from another app. During each test keep talking and listen to your music."))
        column.addView(button("Start the earbuds microphone test") { startGuidedHeadsetTest() })

        column.addView(heading("Step 3: alert and music test (S3)"))
        column.addView(note("Four alerts. Type the name of the app that is playing (for example Spotify or YouTube), keep it playing, then start."))
        playerField = EditText(this).apply { hint = "Name of the music or podcast APP" }
        column.addView(playerField)
        column.addView(button("Start the alert test") { startGuidedAlertTest() })

        column.addView(heading("Step 4: background service (S2, S5, S6)"))
        column.addView(note("Starts a microphone foreground service and logs every event. Modes: cycle = opens the mic briefly every N seconds; mic_open = mic open all the time, audio discarded; idle = service only."))
        serviceModeSpinner = spinner(listOf(LabService.MODE_CYCLE, LabService.MODE_MIC_OPEN, LabService.MODE_IDLE))
        hopSpinner = spinner(listOf("100", "20", "200"))
        cycleSpinner = spinner(listOf("60", "30", "300"))
        column.addView(labelled("Mode", serviceModeSpinner))
        column.addView(labelled("Read size, mic_open (ms)", hopSpinner))
        column.addView(labelled("Cycle every (s)", cycleSpinner))
        column.addView(button("Start service (from this screen)") { startLabService() })
        column.addView(button("Stop service") { stopLabService() })
        column.addView(button("Show last events") { showEvents() })
        column.addView(button("Clear events") {
            EventLog.clear(this)
            appendLog("Events cleared.")
        })
        column.addView(note("Other start paths to try: (a) stop the service, then tap Start in the notification that appears; (b) add the EchoCatch Lab tile to Quick Settings and tap it; (c) restart the phone and read the events (boot_start_failed is expected)."))

        column.addView(heading("Share your results"))
        column.addView(button("Share report (JSON, no audio)") { shareReport(eventsOnly = false) })
        column.addView(button("Share events only") { shareReport(eventsOnly = true) })
        column.addView(button("Clear report") {
            report = Report()
            appendLog("Report cleared.")
        })

        column.addView(heading("Advanced: run one test by hand"))
        column.addView(note("S1 by hand: pick a method and preset, then tap a microphone."))
        methodSpinner = spinner(RouteTest.Method.entries.map { it.name })
        presetSpinner = spinner(presets.map { it.first })
        secondsSpinner = spinner(listOf("10", "5", "20"))
        rateSpinner = spinner(listOf("16000", "8000", "32000", "48000"))
        column.addView(labelled("Method", methodSpinner))
        column.addView(labelled("Capture preset", presetSpinner))
        column.addView(labelled("Seconds", secondsSpinner))
        column.addView(labelled("Requested sample rate (Hz)", rateSpinner))
        deviceButtons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(deviceButtons)
        column.addView(note("S3 by hand: pick the focus type and alert attributes."))
        gainSpinner = spinner(gains.map { it.first })
        usageSpinner = spinner(usages.map { it.first })
        contentSpinner = spinner(contentTypes.map { it.first })
        column.addView(labelled("Focus type", gainSpinner))
        column.addView(labelled("Usage", usageSpinner))
        column.addView(labelled("Content type", contentSpinner))
        column.addView(button("Play alert now") { runManualAlert() })
        column.addView(button("Show active players (usage and content type)") { scanPlayers() })
        column.addView(button("Volume-duck fallback test (lowers music volume briefly)") { runVolumeDuck() })

        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, PAD, 0, PAD)
        }
        column.addView(heading("Log"))
        column.addView(log)
        return ScrollView(this).apply { addView(column) }
    }

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) list += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        return list.toTypedArray()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for ((index, permission) in permissions.withIndex()) {
            val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
            appendLog("Permission ${permission.substringAfterLast('.')}: ${if (granted) "granted" else "DENIED"}")
        }
    }

    private fun scanDevices() {
        report.put("phone", DeviceInfo.phone(this))
        report.put("audioDevices", DeviceInfo.audioDevices(audioManager))
        report.save(this)
        deviceButtons.removeAllViews()
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        for (device in inputs.filter { DeviceInfo.isRealMicrophone(it.type) }) {
            val label = "Test mic: ${DeviceInfo.typeName(device.type)} (${device.productName})"
            deviceButtons.addView(button(label) { runManualRouteTest(device) })
        }
        appendLog("Found ${inputs.size} input and ${audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).size} output device(s):")
        for (device in DeviceInfo.allDevices(audioManager)) {
            appendLog("  ${if (device.isSource) "in " else "out"} ${DeviceInfo.typeName(device.type)} ${device.productName}")
        }
        val hasHeadsetMic = inputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        appendLog(if (hasHeadsetMic) "Earbuds microphone is listed. Go to step 2." else "No earbuds microphone is listed. Connect your earbuds and scan again.")
    }

    // ---------------------------------------------------------------------------------------------------------------
    // S1: microphone route
    // ---------------------------------------------------------------------------------------------------------------

    private class S1Step(val label: String, val device: AudioDeviceInfo, val method: RouteTest.Method, val preset: Pair<String, Int>)

    private fun startGuidedHeadsetTest() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val headset = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        val phone = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        if (headset == null || phone == null) {
            appendLog("Cannot start: no earbuds microphone is listed. Connect the earbuds and tap Scan devices.")
            return
        }
        if (report.lastIn("phone") == null) scanDevices()
        val voiceCommunication = "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION
        val voiceRecognition = "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION
        val steps = listOf(
            S1Step("Earbuds microphone, call route, preset VOICE_COMMUNICATION", headset, RouteTest.Method.COMMUNICATION_DEVICE, voiceCommunication),
            S1Step("Earbuds microphone, call route, preset VOICE_RECOGNITION", headset, RouteTest.Method.COMMUNICATION_DEVICE, voiceRecognition),
            S1Step("Earbuds microphone, preferred device, preset VOICE_COMMUNICATION", headset, RouteTest.Method.PREFERRED_DEVICE, voiceCommunication),
            S1Step("Phone microphone (comparison)", phone, RouteTest.Method.PREFERRED_DEVICE, voiceRecognition),
        )
        runS1Step(steps, 0)
    }

    private fun runS1Step(steps: List<S1Step>, index: Int) {
        if (index >= steps.size) {
            appendLog("Earbuds microphone test finished. Tap Share report at the bottom.")
            return
        }
        val step = steps[index]
        AlertDialog.Builder(this)
            .setTitle("Test ${index + 1} of ${steps.size}")
            .setMessage("${step.label}\n\nKeep your music playing and talk continuously for 10 seconds (read something aloud). Listen to the music while it records.")
            .setCancelable(false)
            .setPositiveButton("Start") { _, _ -> executeRouteTest(step.device, step.method, step.preset, 10, 16000, step.label) { runS1Step(steps, index + 1) } }
            .setNegativeButton("Stop here", null)
            .show()
    }

    private fun runManualRouteTest(device: AudioDeviceInfo) {
        val method = RouteTest.Method.entries[methodSpinner.selectedItemPosition]
        val preset = presets[presetSpinner.selectedItemPosition]
        executeRouteTest(
            device,
            method,
            preset,
            secondsSpinner.selectedItem.toString().toInt(),
            rateSpinner.selectedItem.toString().toInt(),
            "manual",
        ) {}
    }

    private fun executeRouteTest(
        device: AudioDeviceInfo,
        method: RouteTest.Method,
        preset: Pair<String, Int>,
        seconds: Int,
        sampleRate: Int,
        label: String,
        onFinished: () -> Unit,
    ) {
        val request = RouteTest.Request(device, method, preset.second, preset.first, seconds, sampleRate)
        appendLog("Running: $label ... talk now")
        worker.execute {
            val result = RouteTest(this).run(request) { line -> runOnUiThread { appendLog(line) } }
            result.put("guidedStep", label)
            report.add("s1RouteTests", result)
            report.save(this)
            runOnUiThread {
                val error = result.optString("error")
                if (error.isNotEmpty()) appendLog("Test error: $error")
                askMusicQuality(result, onFinished)
            }
        }
    }

    private fun askMusicQuality(result: JSONObject, then: () -> Unit) {
        val options = listOf(
            "Same as before (good)" to "good",
            "Worse, like a phone call" to "phone-call-quality",
            "Stopped or paused" to "stopped-or-paused",
            "Got quieter" to "quieter",
            "Not sure" to "unsure",
        )
        AlertDialog.Builder(this)
            .setTitle("How did your music sound during that test?")
            .setCancelable(false)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                result.put("musicQualityNote", options[which].second)
                report.save(this)
                appendLog("Noted: ${options[which].second}")
                then()
            }
            .show()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // S3: ducking and pausing
    // ---------------------------------------------------------------------------------------------------------------

    private class S3Step(val gain: Pair<String, Int>, val usage: Pair<String, Int>, val content: Pair<String, Int>)

    private fun startGuidedAlertTest() {
        val player = playerField.text.toString().trim()
        if (player.isEmpty()) {
            appendLog("Type the name of the app that is playing (for example Spotify), then tap Start again.")
            return
        }
        if (report.lastIn("phone") == null) scanDevices()
        val steps = listOf(
            S3Step(gains[0], usages[0], contentTypes[0]),
            S3Step(gains[0], usages[3], contentTypes[1]),
            S3Step(gains[1], usages[0], contentTypes[0]),
            S3Step(gains[1], usages[4], contentTypes[1]),
        )
        runS3Step(steps, 0, player)
    }

    private fun runS3Step(steps: List<S3Step>, index: Int, player: String) {
        if (index >= steps.size) {
            appendLog("Alert test finished. Tap Share report at the bottom.")
            return
        }
        val step = steps[index]
        AlertDialog.Builder(this)
            .setTitle("Alert ${index + 1} of ${steps.size}")
            .setMessage("Keep $player playing. An alert chime will play. Listen to what the music does.\n\n${step.gain.first}\n${step.usage.first}\n${step.content.first}")
            .setCancelable(false)
            .setPositiveButton("Play alert") { _, _ ->
                val request = DuckingTest.Request(
                    step.gain.second, step.gain.first, step.usage.second, step.usage.first, step.content.second, step.content.first, player,
                )
                executeAlert(request) { runS3Step(steps, index + 1, player) }
            }
            .setNegativeButton("Stop here", null)
            .show()
    }

    private fun executeAlert(request: DuckingTest.Request, onFinished: () -> Unit) {
        appendLog("Alert: ${request.gainName} / ${request.usageName} / ${request.contentTypeName}")
        worker.execute {
            val result = DuckingTest(this).run(request)
            report.add("s3Tests", result)
            report.save(this)
            runOnUiThread {
                appendLog("Focus result: ${result.optString("focusResult")}")
                askAlertObservation(result, onFinished)
            }
        }
    }

    private fun askAlertObservation(result: JSONObject, then: () -> Unit) {
        val options = listOf(
            "Music got quieter, alert clear (ducked)" to "ducked",
            "Music paused, then resumed" to "paused-and-resumed",
            "Music unchanged, alert on top" to "ignored",
            "Music paused and did NOT resume" to "did-not-resume",
            "Alert was hard to hear" to "alert-hard-to-hear",
            "I did not hear the alert" to "alert-not-heard",
        )
        val chosen = mutableListOf<String>()
        val checked = BooleanArray(options.size)
        AlertDialog.Builder(this)
            .setTitle("What happened? (choose all that apply)")
            .setCancelable(false)
            .setMultiChoiceItems(options.map { it.first }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Done") { _, _ ->
                for ((index, option) in options.withIndex()) if (checked[index]) chosen.add(option.second)
                result.put("observations", JSONArray(chosen))
                report.save(this)
                appendLog("Noted: $chosen")
                then()
            }
            .show()
    }

    private fun runManualAlert() {
        val (gainName, gain) = gains[gainSpinner.selectedItemPosition]
        val (usageName, usage) = usages[usageSpinner.selectedItemPosition]
        val (contentName, content) = contentTypes[contentSpinner.selectedItemPosition]
        val request = DuckingTest.Request(gain, gainName, usage, usageName, content, contentName, playerField.text.toString())
        executeAlert(request) {}
    }

    private fun scanPlayers() {
        val players = JSONArray()
        for (config in audioManager.activePlaybackConfigurations) {
            val attributes = config.audioAttributes
            players.put(JSONObject().put("usage", attributes.usage).put("contentType", attributes.contentType))
        }
        val scan = JSONObject().put("player", playerField.text.toString()).put("musicActive", audioManager.isMusicActive).put("players", players)
        report.add("s3PlayerScans", scan)
        report.save(this)
        appendLog("Active players: $players (usage 1 = media, contentType 1 = speech, 2 = music, 0 = unknown)")
    }

    private fun runVolumeDuck() {
        val stream = AudioManager.STREAM_MUSIC
        val max = audioManager.getStreamMaxVolume(stream)
        val before = audioManager.getStreamVolume(stream)
        worker.execute {
            val info = JSONObject().put("max", max).put("before", before).put("volumeFixed", audioManager.isVolumeFixed)
            try {
                audioManager.setStreamVolume(stream, maxOf(1, before * 3 / 10), 0)
                info.put("during", audioManager.getStreamVolume(stream))
                SystemClock.sleep(1500)
            } finally {
                audioManager.setStreamVolume(stream, before, 0)
            }
            info.put("after", audioManager.getStreamVolume(stream))
            report.add("s3VolumeDuck", info)
            report.save(this)
            runOnUiThread { appendLog("Volume duck: $info. Did the music get quieter? Did it restore?") }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // S2, S5, S6: service
    // ---------------------------------------------------------------------------------------------------------------

    private fun startLabService() {
        val mode = serviceModeSpinner.selectedItem.toString()
        val hop = hopSpinner.selectedItem.toString().toInt()
        val cycle = cycleSpinner.selectedItem.toString().toInt()
        EventLog.append(this, "ui_start_requested", JSONObject().put("mode", mode).put("hopMs", hop).put("cycleSeconds", cycle))
        try {
            startForegroundService(LabService.startIntent(this, mode, hop, cycle))
            appendLog("Service start requested: $mode. Press Home and leave it; read events later.")
        } catch (e: Exception) {
            EventLog.append(this, "ui_start_failed", JSONObject().put("exception", e.javaClass.simpleName).put("message", e.message ?: ""))
            appendLog("Start failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun stopLabService() {
        startService(Intent(this, LabService::class.java).setAction(LabService.ACTION_STOP))
        appendLog("Stop requested.")
    }

    private fun showEvents() {
        val lines = EventLog.lines(this, 40)
        appendLog("--- last ${lines.size} events ---")
        for (line in lines) {
            val event = JSONObject(line)
            appendLog("${event.optString("time").substringAfter(' ')} ${event.optString("type")} ${event.optJSONObject("data")}")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sharing
    // ---------------------------------------------------------------------------------------------------------------

    private fun shareReport(eventsOnly: Boolean) {
        val events = JSONArray()
        for (line in EventLog.lines(this, 400)) events.put(JSONObject(line))
        val text = if (eventsOnly) {
            JSONObject().put("schema", 1).put("app", "EchoCatchLab").put("events", events).toString()
        } else {
            report.put("events", events)
            report.json()
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EchoCatch Lab report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share EchoCatch Lab report"))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Small view helpers
    // ---------------------------------------------------------------------------------------------------------------

    private fun appendLog(line: String) {
        log.append(line + "\n")
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

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun labelled(label: String, view: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(TextView(this@MainActivity).apply { text = label }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private companion object {
        const val PAD = 32
        const val REQUEST_PERMISSIONS = 1
    }
}
