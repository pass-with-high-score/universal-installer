package app.pwhs.universalinstaller.presentation.sync

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile to toggle the Sync & Share server on/off
 * directly from the notification shade.
 */
class SyncTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        // Create a fresh scope each time the tile becomes visible
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        newScope.launch {
            SyncManager.state.collect { state ->
                updateTile(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        super.onClick()
        val currentState = SyncManager.state.value
        val intent = Intent(this, SyncService::class.java)

        if (currentState == SyncState.RUNNING || currentState == SyncState.STARTING) {
            // Stop the server
            intent.action = "STOP"
            startService(intent)
        } else {
            // Start the server
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun updateTile(state: SyncState) {
        val tile = qsTile ?: return
        when (state) {
            SyncState.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = SyncManager.serverUrl.value ?: "Running"
                }
            }
            SyncState.STARTING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Starting..."
                }
            }
            SyncState.STOPPED -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Tap to start"
                }
            }
            SyncState.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Error"
                }
            }
        }
        tile.updateTile()
    }
}
