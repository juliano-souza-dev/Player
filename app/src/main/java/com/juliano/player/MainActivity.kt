package com.juliano.player

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

class MainActivity : ComponentActivity() {
    private var transformer: Transformer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    @Composable
    private fun Screen() {
        var url by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf<Uri?>(null) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Pronto para começar") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            selected = uri
            if (uri != null) status = "Vídeo selecionado"
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Player", style = MaterialTheme.typography.headlineLarge)
            Text("Vídeos autorizados e áudio offline")
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Link HTTPS direto de vídeo") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )
            Button(onClick = {
                try {
                    val uri = Uri.parse(url.trim())
                    val host = uri.host?.lowercase().orEmpty()
                    require(uri.scheme == "https" && host.isNotBlank())
                    require(!host.endsWith("youtube.com") && host != "youtu.be" && !host.endsWith(".youtube.com"))
                    require(uri.path.orEmpty().lowercase().endsWith(".mp4")) {
                        "Use um link direto HTTPS para um arquivo .mp4"
                    }
                    val name = "player_${System.currentTimeMillis()}.mp4"
                    val request = DownloadManager.Request(uri)
                        .setTitle(name)
                        .setMimeType("video/mp4")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    status = "Download iniciado. Acompanhe as notificações."
                } catch (e: Exception) {
                    status = e.message ?: "Link inválido"
                }
            }) { Text("Baixar vídeo MP4") }
            HorizontalDivider()
            Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !busy) {
                Text("Selecionar vídeo do celular")
            }
            Text(selected?.lastPathSegment ?: "Nenhum vídeo selecionado")
            Button(onClick = {
                val input = selected ?: return@Button
                busy = true
                status = "Convertendo áudio para M4A..."
                convertToM4a(input, { message ->
                    status = message
                    busy = false
                })
            }, enabled = selected != null && !busy) {
                Text("Extrair áudio M4A")
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    transformer?.cancel()
                    busy = false
                    status = "Conversão cancelada"
                }) { Text("Cancelar") }
            }
            Text(status)
            Text("MP3: codificador ainda não integrado. Não há download de páginas do YouTube.",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun convertToM4a(uri: Uri, done: (String) -> Unit) {
        val output = File(cacheDir, "player_${System.currentTimeMillis()}.m4a")
        try {
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setRemoveVideo(true)
                .build()
            transformer = Transformer.Builder(this)
                .setAudioMimeType("audio/mp4a-latm")
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        try {
                            val values = ContentValues().apply {
                                put(MediaStore.Audio.Media.DISPLAY_NAME, output.name)
                                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Player")
                                put(MediaStore.Audio.Media.IS_PENDING, 1)
                            }
                            val resolver = contentResolver
                            val saved = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                                ?: error("Não foi possível criar o arquivo")
                            try {
                                resolver.openOutputStream(saved)?.use { dest ->
                                    output.inputStream().use { it.copyTo(dest) }
                                } ?: error("Não foi possível abrir o destino")
                                resolver.update(saved, ContentValues().apply {
                                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                                }, null, null)
                                done("Áudio salvo em Música/Player: ${output.name}")
                            } catch (e: Exception) {
                                resolver.delete(saved, null, null)
                                throw e
                            }
                        } catch (e: Exception) {
                            done("Erro ao salvar: ${e.localizedMessage}")
                        } finally {
                            output.delete()
                        }
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                        output.delete()
                        done("Erro na conversão: ${exportException.localizedMessage}")
                    }
                })
                .build()
            transformer?.start(item, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            done("Falha: ${e.localizedMessage}")
        }
    }
}
