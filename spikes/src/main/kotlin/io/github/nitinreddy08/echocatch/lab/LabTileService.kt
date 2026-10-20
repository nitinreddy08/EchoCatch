// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.json.JSONObject

/** Spike S2: does a Quick Settings tile tap count as a way to start the microphone service from the background? */
class LabTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        EventLog.append(this, "tile_clicked", JSONObject().put("serviceRunning", LabService.isRunning))
        try {
            if (LabService.isRunning) {
                startService(android.content.Intent(this, LabService::class.java).setAction(LabService.ACTION_STOP))
            } else {
                startForegroundService(LabService.startIntent(this, LabService.MODE_CYCLE))
                EventLog.append(this, "tile_start_requested")
            }
        } catch (e: Exception) {
            EventLog.append(this, "tile_start_failed", JSONObject().put("exception", e.javaClass.simpleName).put("message", e.message ?: ""))
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.state = if (LabService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "EchoCatch Lab"
        tile.updateTile()
    }
}
