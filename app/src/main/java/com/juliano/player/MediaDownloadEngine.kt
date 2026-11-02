package com.juliano.player

import android.content.Context
import com.yausername.youtubedl_android.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Download engine for publicly accessible, unprotected media.
 * Never bypass DRM or authentication. All exceptions are returned to the UI.
 */
class MediaDownloadEngine(private val context: Context) {
    private val downloader = YoutubeDL.getInstance()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        downloader.init(context)
        FFmpeg.getInstance().init(context)
    }

    suspend fun download(
        url: String,
        audioOnly: Boolean,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://")) { "Use um link HTTPS válido." }
            val outputDir = File(context.getExternalFilesDir(null), "Player").apply { mkdirs() }
            val output = File(outputDir, "%(title).150B.%(ext)s")
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-overwrites")
                addOption("--newline")
                addOption("-o", output.absolutePath)
                if (audioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                } else {
                    addOption("-f", "bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
            }
            downloader.execute(request) { progress, eta, line ->
                onProgress(progress, if (eta > 0) "Restam aproximadamente ${eta}s" else line)
            }
            outputDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
                ?: error("A tarefa terminou sem produzir um arquivo.")
        }
    }
}
