package app.pwhs.universalinstaller.remoteconfig

import app.pwhs.core.network.NoOpVtRemoteConfigSource
import app.pwhs.core.network.VtRemoteConfigSource

fun createVtRemoteConfigSource(isDebug: Boolean): VtRemoteConfigSource = NoOpVtRemoteConfigSource
