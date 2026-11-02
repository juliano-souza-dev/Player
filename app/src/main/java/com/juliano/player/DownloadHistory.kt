package com.juliano.player

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class DownloadRecord(val id: Long, val title: String, val state: Int, val reason: Int, val bytes: Long, val total: Long)

@Composable
fun DownloadHistory(context: Context, manager: DownloadManager, onMessage: (String) -> Unit) {
    val prefs = remember { context.getSharedPreferences("player_downloads", Context.MODE_PRIVATE) }
    var records by remember { mutableStateOf(emptyList<DownloadRecord>()) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision) {
        while (true) {
            val ids = prefs.getString("history", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }
            records = ids.mapNotNull { id ->
                try {
                    manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                        if (!cursor.moveToFirst()) null else {
                            fun int(name: String) = cursor.getInt(cursor.getColumnIndexOrThrow(name))
                            fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                            fun string(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name)) ?: "Vídeo"
                            DownloadRecord(id, string(DownloadManager.COLUMN_TITLE),
                                int(DownloadManager.COLUMN_STATUS), int(DownloadManager.COLUMN_REASON),
                                long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                                long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        }
                    }
                } catch (_: Exception) { null }
            }
            delay(1200)
        }
    }
    if (records.isEmpty()) Text("Nenhum download registrado")
    records.forEach { item ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                val label = when (item.state) {
                    DownloadManager.STATUS_PENDING -> "Aguardando início"
                    DownloadManager.STATUS_RUNNING -> "Transferindo dados"
                    DownloadManager.STATUS_PAUSED -> "Pausado (motivo ${item.reason})"
                    DownloadManager.STATUS_SUCCESSFUL -> "Concluído"
                    DownloadManager.STATUS_FAILED -> if (item.reason in 400..599)
                        "Falhou: servidor respondeu HTTP ${item.reason}" else "Falhou: código Android ${item.reason}"
                    else -> "Estado desconhecido"
                }
                Text(label)
                Text("${item.bytes / 1024} KB recebidos" +
                    if (item.total > 0) " de ${item.total / 1024} KB" else "")
                if (item.total > 0) LinearProgressIndicator(
                    progress = { (item.bytes.toFloat() / item.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                ) else if (item.state == DownloadManager.STATUS_RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row {
                    if (item.state == DownloadManager.STATUS_SUCCESSFUL) {
                        TextButton(onClick = {
                            try {
                                val uri = manager.getUriForDownloadedFile(item.id)
                                if (uri != null) context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }) else onMessage("Arquivo indisponível")
                            } catch (e: Exception) { onMessage("Não foi possível abrir: ${e.localizedMessage}") }
                        }) { Text("Abrir") }
                    }
                    TextButton(onClick = {
                        if (item.state != DownloadManager.STATUS_SUCCESSFUL && item.state != DownloadManager.STATUS_FAILED)
                            manager.remove(item.id)
                        val ids = prefs.getString("history", "").orEmpty().split(",")
                            .filter { it.isNotBlank() && it != item.id.toString() }
                        prefs.edit().putString("history", ids.joinToString(",")).apply()
                        revision++
                    }) { Text(if (item.state == DownloadManager.STATUS_SUCCESSFUL) "Ocultar" else "Remover/Cancelar") }
                }
            }
        }
    }
}
