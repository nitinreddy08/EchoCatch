// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Collects phone and audio-device facts. Bluetooth addresses are deliberately left out. */
object DeviceInfo {
    fun phone(context: Context): JSONObject {
        val pm = context.packageManager
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("securityPatch", Build.VERSION.SECURITY_PATCH)
            .put("buildDisplay", Build.DISPLAY)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("hasBluetoothLe", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            .put("hasMicrophone", pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
            .put("hasLowLatencyAudio", pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY))
    }

    fun audioDevices(audioManager: AudioManager): JSONArray {
        val array = JSONArray()
        for (device in allDevices(audioManager)) {
            array.put(describe(device))
        }
        return array
    }

    /** Inputs and outputs together (lint rejects GET_DEVICES_ALL as a flag value, so combine the two valid flags). */
    fun allDevices(audioManager: AudioManager): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() +
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

    fun describe(device: AudioDeviceInfo): JSONObject = JSONObject()
        .put("id", device.id)
        .put("type", typeName(device.type))
        .put("product", device.productName.toString())
        .put("isSource", device.isSource)
        .put("isSink", device.isSink)
        .put("sampleRates", JSONArray(device.sampleRates.toList()))
        .put("channelCounts", JSONArray(device.channelCounts.toList()))
        .put("encodings", JSONArray(device.encodings.toList()))

    @SuppressLint("InlinedApi")
    fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        else -> "TYPE_$type"
    }
}
