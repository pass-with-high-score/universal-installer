package app.pwhs.universalinstaller.wearos.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// GMS unbind WearableListenerService ngay khi onChannelOpened return -> scope của service chết giữa chừng.
internal object WearReceiveScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
