package com.juliano.player

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class AudioEntry(val id: Long, val name: String)

@Composable
fun AudioLibrary() {
    val context = LocalContext.current
    var files by remember { mutableStateOf(emptyList<AudioEntry>()) }
    var message by remember { mutableStateOf("") }
    fun refresh() {
        files = try {
            val result = mutableListOf<AudioEntry>()
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, arrayOf("Music/Player/%"),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    result.add(AudioEntry(cursor.getLong(idIndex), cursor.getString(nameIndex)))
                }
            }
            message = if (result.isEmpty()) "Nenhum áudio exportado ainda" else ""
            result
        } catch (e: Exception) {
            message = "Não foi possível ler a biblioteca: ${e.localizedMessage}"
            emptyList()
        }
    }
    LaunchedEffect(Unit) { refresh() }
    OutlinedButton(onClick = { refresh() }) { Text("Atualizar biblioteca") }
    if (message.isNotEmpty()) Text(message)
    files.forEach { file ->
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, file.id)
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "audio/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (e: Exception) {
                        message = "Nenhum reprodutor disponível"
                    }
                }) { Text("Reproduzir") }
                TextButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Compartilhar áudio"))
                }) { Text("Compartilhar") }
            }
        }
        HorizontalDivider()
    }
}
