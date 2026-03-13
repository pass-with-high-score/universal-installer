package com.nqmgaming.universalinstaller.domain.parser

import android.content.Context
import android.net.Uri
import com.nqmgaming.universalinstaller.domain.model.app.AppInfo

interface AppParser {
    suspend fun parse(context: Context, uri: Uri): AppInfo?
}
