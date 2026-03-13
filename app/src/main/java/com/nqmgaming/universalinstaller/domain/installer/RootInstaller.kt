package com.nqmgaming.universalinstaller.domain.installer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class RootInstaller(
    private val context: Context
) : AppInstaller {

    override suspend fun install(uris: List<Uri>, options: InstallOptions): Flow<InstallState> = channelFlow {
        try {
            trySend(InstallState.Processing)
            trySend(InstallState.Progress(0))

            val isRooted = checkRootAccess()
            if (!isRooted) {
                trySend(InstallState.Error("Root access is not available or granted."))
                close()
                return@channelFlow
            }

            withContext(Dispatchers.IO) {
                if (uris.size == 1) {
                    installSingleApk(uris.first(), { progress ->
                        trySend(InstallState.Progress(progress))
                    })
                } else {
                    installMultipleApks(uris, { progress ->
                        trySend(InstallState.Progress(progress))
                    })
                }
                trySend(InstallState.Success)
            }
        } catch (e: Exception) {
            Timber.e(e, "Root installation failed")
            trySend(InstallState.Error(e.message ?: "Root installation failed", e))
        } finally {
            close()
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_test"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun installSingleApk(uri: Uri, progressCallback: (Int) -> Unit) {
        // Copy the URI to a temporary file accessible by root
        val tempFile = copyToTempFile(uri, "root_install_single.apk", progressCallback)
        
        try {
            val command = "pm install -r \"${tempFile.absolutePath}\""
            executeSuCommand(command)
        } finally {
            tempFile.delete()
        }
    }

    private fun installMultipleApks(uris: List<Uri>, progressCallback: (Int) -> Unit) {
        val tempDir = File(context.cacheDir, "root_install_session").apply { mkdirs() }
        
        try {
            val totalSize = uris.sumOf { getFileSize(it) }
            var currentBytesCopied = 0L

            // 1. Copy all splits to temp dir
            val tempFiles = uris.mapIndexed { index, uri ->
                val tempFile = File(tempDir, "split_$index.apk")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(tempFile).use { outStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inStream.read(buffer).also { bytesRead = it } != -1) {
                            outStream.write(buffer, 0, bytesRead)
                            currentBytesCopied += bytesRead
                            val progress = ((currentBytesCopied.toFloat() / totalSize) * 50).toInt() // File copy is 50% of the process
                            progressCallback(progress)
                        }
                    }
                }
                tempFile
            }

            // 2. Create install session
            progressCallback(60)
            val createOutput = executeSuCommandWithOutput("pm install-create")
            val sessionIdMatch = Regex("Success: created install session \\[(\\d+)\\]").find(createOutput)
                ?: throw Exception("Failed to create root install session: $createOutput")
            val sessionId = sessionIdMatch.groupValues[1]

            // 3. Write splits
            tempFiles.forEachIndexed { index, file ->
                executeSuCommand("pm install-write $sessionId split_$index \"${file.absolutePath}\"")
                val progress = 60 + ((index + 1).toFloat() / tempFiles.size * 30).toInt() // Writing splits takes 30%
                progressCallback(progress)
            }

            // 4. Commit session
            progressCallback(95)
            val commitOutput = executeSuCommandWithOutput("pm install-commit $sessionId")
            if (!commitOutput.contains("Success")) {
                throw Exception("Failed to commit root session: $commitOutput")
            }
            progressCallback(100)

        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun executeSuCommand(command: String) {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            throw Exception("SU command failed: $error (exit code: $exitCode)")
        }
    }

    private fun executeSuCommandWithOutput(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().readText()
        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            throw Exception("SU command failed: $error (exit code: $exitCode)")
        }
        return output
    }

    private fun copyToTempFile(uri: Uri, fileName: String, progressCallback: (Int) -> Unit): File {
        val tempFile = File(context.cacheDir, fileName)
        val targetSize = getFileSize(uri)
        
        context.contentResolver.openInputStream(uri)?.use { inStream ->
            FileOutputStream(tempFile).use { outStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var currentBytes = 0L
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead
                    progressCallback(((currentBytes.toFloat() / targetSize) * 50).toInt()) // Assume copying is 50% of the single APK install process
                }
            }
        } ?: throw Exception("Failed to copy uri to temp file: $uri")
        
        return tempFile
    }

    private fun getFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1) return cursor.getLong(sizeIndex)
            }
        }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            return fd.statSize
        }
        return 1024L // Fake size to prevent division by zero if query fails
    }
}
