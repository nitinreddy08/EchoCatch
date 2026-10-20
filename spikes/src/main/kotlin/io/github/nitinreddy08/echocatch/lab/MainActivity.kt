// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * EchoCatch Lab: the experiment app for milestone M0. Each section matches a spike in docs/plan/04-spikes.md.
 * Results accumulate in a JSON report that can be shared from the "Share report" button. No audio is stored or shared.
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
    private lateinit var serviceModeSpinner: Spinner
    private lateinit var hopSpinner: Spinner
    private lateinit var cycleSpinner: Spinner

    private val presets = listOf(
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
        "MIC" to MediaRecorder.AudioSource.MIC,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        setContentView(buildUi())
        appendLog("EchoCatch Lab ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLog("Start with: Grant permissions, then Scan devices.")
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD, PAD, PAD, PAD)
        }
        column.addView(heading("EchoCatch Lab"))
        column.addView(note("Experiment app for the earbuds, service, ducking and power spikes. Nothing is recorded to disk or sent anywhere."))
        column.addView(button("Grant permissions") { requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS) })
        column.addView(button("Scan devices (phone info + audio devices)") { scanDevices() })

        column.addView(heading("S1: microphone route"))
        column.addView(note("1) Connect your earbuds. 2) Start music in another app. 3) Pick a method and preset, then tap a microphone below. Listen to the music while it records and note the quality."))
        methodSpinner = spinner(RouteTest.Method.entries.map { it.name })
        presetSpinner = spinner(presets.map { it.first })
        secondsSpinner = spinner(listOf("5", "10", "20"))
        rateSpinner = spinner(listOf("16000", "8000", "32000", "48000"))
        column.addView(labelled("Method", methodSpinner))
        column.addView(labelled("Capture preset", presetSpinner))
        column.addView(labelled("Seconds", secondsSpinner))
        column.addView(labelled("Requested sample rate (Hz)", rateSpinner))
        deviceButtons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(deviceButtons)
        column.addView(note("After a test, record what you heard (applies to the last S1 test):"))
        column.addView(button("Music quality stayed good") { noteQuality("good") })
        column.addView(button("Music sounded like a phone call") { noteQuality("phone-call-quality") })
        column.addView(button("Music stopped or paused") { noteQuality("stopped-or-paused") })

        column.addView(heading("S2, S5, S6: service, coexistence, power"))
        column.addView(note("Starts a microphone foreground service and logs every event (start path, mic open and close, silenced capture, device and playback changes, battery). Modes: idle = service only; mic_open = microphone open all the time, audio discarded; cycle = opens the mic briefly every N seconds."))
        serviceModeSpinner = spinner(listOf(LabService.MODE_CYCLE, LabService.MODE_MIC_OPEN, LabService.MODE_IDLE))
        hopSpinner = spinner(listOf("100", "20", "200"))
        cycleSpinner = spinner(listOf("60", "30", "300"))
        column.addView(labelled("Mode", serviceModeSpinner))
        column.addView(labelled("Read size, mic_open (ms)", hopSpinner))
        column.addView(labelled("Cycle every (s)", cycleSpinner))
        column.addView(button("Start service (from this screen)") { startService() })
        column.addView(button("Stop service") { stopServiceFromUi() })
        column.addView(button("Show last events") { showEvents() })
        column.addView(button("Clear events") {
            EventLog.clear(this)
            appendLog("Events cleared.")
        })
        column.addView(note("Other start paths to try: (a) stop the service, then tap Start in the notification that appears; (b) add the EchoCatch Lab tile to Quick Settings and tap it; (c) restart the phone and read the events (boot_start_failed is expected)."))

        column.addView(heading("Report"))
        column.addView(button("Share report (JSON, no audio)") { shareReport() })
        column.addView(button("Clear report") {
            report = Report()
            appendLog("Report cleared.")
        })

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

    private fun startService() {
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

    private fun stopServiceFromUi() {
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
        for (device in inputs) {
            val label = "Test mic: ${DeviceInfo.typeName(device.type)} (${device.productName})"
            deviceButtons.addView(button(label) { runRouteTest(device) })
        }
        appendLog("Found ${inputs.size} input device(s) and ${audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).size} output device(s).")
        for (device in DeviceInfo.allDevices(audioManager)) {
            appendLog("  ${if (device.isSource) "in " else "out"} ${DeviceInfo.typeName(device.type)} ${device.productName}")
        }
    }

    private fun runRouteTest(device: AudioDeviceInfo) {
        val method = RouteTest.Method.entries[methodSpinner.selectedItemPosition]
        val (presetName, presetSource) = presets[presetSpinner.selectedItemPosition]
        val request = RouteTest.Request(
            device = device,
            method = method,
            source = presetSource,
            sourceName = presetName,
            seconds = secondsSpinner.selectedItem.toString().toInt(),
            requestedSampleRate = rateSpinner.selectedItem.toString().toInt(),
        )
        appendLog("Running S1: ${DeviceInfo.typeName(device.type)} / $method / $presetName ...")
        worker.execute {
            val result = RouteTest(this).run(request) { line -> runOnUiThread { appendLog(line) } }
            report.add("s1RouteTests", result)
            report.save(this)
            runOnUiThread {
                val error = result.optString("error")
                if (error.isNotEmpty()) appendLog("S1 error: $error") else appendLog("S1 done. Tap how the music sounded.")
            }
        }
    }

    private fun noteQuality(value: String) {
        val last: JSONObject? = report.lastIn("s1RouteTests")
        if (last == null) {
            appendLog("No S1 test yet.")
            return
        }
        last.put("musicQualityNote", value)
        report.save(this)
        appendLog("Noted for last S1 test: $value")
    }

    private fun shareReport() {
        val events = JSONArray()
        for (line in EventLog.lines(this, 400)) events.put(JSONObject(line))
        report.put("events", events)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EchoCatch Lab report")
            putExtra(Intent.EXTRA_TEXT, report.json())
        }
        startActivity(Intent.createChooser(send, "Share EchoCatch Lab report"))
    }

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
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = Unit
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
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
