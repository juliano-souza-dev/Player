package com.juliano.player

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    private var transformer: Transformer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFC247), background = Color(0xFF101116), surface = Color(0xFF1B1D26))) { Screen() } }
    }

    @Composable
    private fun Screen() {
        var url by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf<Uri?>(null) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Pronto para começar") }
        val preferences = remember { getSharedPreferences("player_downloads", Context.MODE_PRIVATE) }
        var downloadId by remember { mutableStateOf(preferences.getLong("active_id", -1L).takeIf { it > 0 }) }
        var downloadError by remember { mutableStateOf("") }
        var downloadBytes by remember { mutableLongStateOf(0L) }
        var downloadTotal by remember { mutableLongStateOf(-1L) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }
        var downloadState by remember { mutableStateOf("") }
        var page by remember { mutableIntStateOf(0) }
        val manager = remember { getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            selected = uri
            if (uri != null) status = "Vídeo selecionado"
        }
        LaunchedEffect(downloadId) {
            val id = downloadId ?: return@LaunchedEffect
            while (true) {
                val cursor = manager.query(DownloadManager.Query().setFilterById(id))
                val finished = cursor?.use {
                    if (!it.moveToFirst()) {
                        downloadState = "Download não encontrado"
                        true
                    } else {
                        val state = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        downloadBytes = downloaded
                        downloadTotal = total
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        downloadError = if (state == DownloadManager.STATUS_FAILED) when (reason) {
                            1001 -> "Arquivo não encontrado no servidor."
                            1002 -> "Erro de conexão ou resposta HTTP inválida."
                            1004 -> "Não foi possível acessar o destino do arquivo."
                            1005 -> "Espaço de armazenamento insuficiente."
                            1006 -> "Dispositivo de armazenamento indisponível."
                            1007 -> "O arquivo já existe no destino."
                            1008 -> "Não foi possível continuar o download."
                            1009 -> "Erro de armazenamento ou gravação."
                            in 400..599 -> "Servidor respondeu HTTP $reason."
                            else -> "Erro do Android: código $reason."
                        } else ""
                        downloadProgress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        downloadState = when (state) {
                            DownloadManager.STATUS_PENDING -> "Na fila"
                            DownloadManager.STATUS_RUNNING -> if (total > 0) "Baixando: ${(downloadProgress * 100).toInt()}%" else "Baixando: ${downloaded / 1024} KB"
                            DownloadManager.STATUS_PAUSED -> "Download pausado pelo sistema"
                            DownloadManager.STATUS_SUCCESSFUL -> "Download concluído! Confira Downloads."
                            DownloadManager.STATUS_FAILED -> "Falha no download: $downloadError"
                            else -> "Verificando download"
                        }
                        state == DownloadManager.STATUS_SUCCESSFUL || state == DownloadManager.STATUS_FAILED
                    }
                } ?: true
                if (finished) break
                delay(700)
            }
        }
        Scaffold(
            containerColor = Color(0xFF101116),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1B1D26)) {
                    NavigationBarItem(selected = page == 0, onClick = { page = 0 },
                        icon = { Text("⌂") }, label = { Text("Início") })
                    NavigationBarItem(selected = page == 1, onClick = { page = 1 },
                        icon = { Text("↓") }, label = { Text("Downloads") })
                    NavigationBarItem(selected = page == 2, onClick = { page = 2 },
                        icon = { Text("♫") }, label = { Text("Biblioteca") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("PLAYER", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFC247))
                Text("Seu vídeo. Seu áudio. Tudo em um lugar.", color = Color.LightGray)
                when (page) {
                    0 -> {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Baixar vídeo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = url, onValueChange = { url = it },
                                    label = { Text("Cole um link direto de MP4") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                                Button(onClick = {
                                    val uri = Uri.parse(url.trim())
                                    val host = uri.host?.lowercase().orEmpty()
                                    when {
                                        uri.scheme != "https" || host.isBlank() ->
                                            status = "Insira um endereço HTTPS válido."
                                        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ->
                                            status = "Links de páginas do YouTube não são arquivos MP4 diretos. Use uma fonte que autorize o download."
                                        !uri.path.orEmpty().lowercase().endsWith(".mp4") ->
                                            status = "Este endereço não aponta para um arquivo .mp4 direto."
                                        else -> try {
                                            val name = "player_${System.currentTimeMillis()}.mp4"
                                            val request = DownloadManager.Request(uri)
                                                .setTitle(name).setMimeType("video/mp4")
                                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                                            downloadId = manager.enqueue(request)
                                            preferences.edit().putLong("active_id", downloadId!!).apply()
                                            downloadProgress = 0f
                                            downloadState = "Na fila"
                                            status = "Download enviado ao Android. Aguardando transferência."
                                            page = 1
                                        } catch (e: Exception) {
                                            status = "Não foi possível iniciar o download: ${e.localizedMessage}"
                                        }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("BAIXAR MP4") }
                                if (status.isNotBlank()) Text(status, color = Color(0xFFFFCF70))
                            }
                        }
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Extrair áudio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) },
                                    enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("Selecionar vídeo do celular")
                                }
                                Text(selected?.lastPathSegment ?: "Nenhum vídeo selecionado")
                                Button(onClick = {
                                    val input = selected ?: return@Button
                                    busy = true
                                    status = "Extraindo áudio..."
                                    convertToM4a(input) { message -> status = message; busy = false }
                                }, enabled = selected != null && !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("SALVAR ÁUDIO M4A")
                                }
                                if (busy) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    OutlinedButton(onClick = {
                                        transformer?.cancel()
                                        busy = false
                                        status = "Conversão cancelada"
                                    }) { Text("Cancelar") }
                                }
                                Text("MP3 ainda não disponível nesta versão.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    1 -> {
                        Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                        Card {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(if (downloadId == null) "Nenhum download ativo" else downloadState)
                                if (downloadId != null) {
                                    Text("Recebidos: ${downloadBytes / 1024} KB" +
                                        if (downloadTotal > 0) " de ${downloadTotal / 1024} KB" else " (tamanho desconhecido)")
                                    Text("Tarefa Android: #$downloadId", style = MaterialTheme.typography.bodySmall)
                                }
                                if (downloadId != null) {
                                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                                    if (downloadState != "Download concluído! Confira Downloads.") {
                                        OutlinedButton(onClick = {
                                            downloadId?.let { manager.remove(it) }
                                            downloadId = null
                                            preferences.edit().remove("active_id").apply()
                                            downloadState = "Download cancelado"
                                        }) { Text("Cancelar download") }
                                    }
                                }
                            }
                        }
                        Text("O download é acompanhado pelo Android e a tarefa atual é recuperada ao reabrir o aplicativo.")
                    }
                    2 -> {
                        Text("Meus áudios", style = MaterialTheme.typography.headlineSmall)
                        AudioLibrary()
                    }
                }
            }
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
