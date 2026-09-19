package com.example.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LabelEntity
import com.example.data.LabelRepository
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.RetrofitClient
import com.example.data.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class UpdateState { Idle, Checking, Available, Downloading, Downloaded, Error }

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val noteRepository: NoteRepository,
    private val labelRepository: LabelRepository
) : ViewModel() {

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.userNameFlow.collect {
                _isDataLoaded.value = true
            }
        }
    }

    val userName: StateFlow<String> = settingsRepository.userNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val apiKey: StateFlow<String> = settingsRepository.geminiApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val groqApiKey: StateFlow<String> = settingsRepository.groqApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val themeMode: StateFlow<Int> = settingsRepository.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val recordMode: StateFlow<Int> = settingsRepository.recordModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val aiProvider: StateFlow<Int> = settingsRepository.aiProviderFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val aiLanguage: StateFlow<String> = settingsRepository.aiLanguageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "English"
    )

    val aiTask: StateFlow<Int> = settingsRepository.aiTaskFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val aiFormat: StateFlow<Int> = settingsRepository.aiFormatFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val backgroundRecordingEnabled: StateFlow<Boolean> = settingsRepository.backgroundRecordingFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val liveTranscriptEnabled: StateFlow<Boolean> = settingsRepository.liveTranscriptFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val autoCompressionMode: StateFlow<Int> = settingsRepository.autoCompressionModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1
    )

    val colorStyle: StateFlow<Int> = settingsRepository.colorStyleFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val nativePickerEnabled: StateFlow<Boolean> = settingsRepository.nativePickerFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _updateState = MutableStateFlow(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _latestVersionStr = MutableStateFlow("")
    val latestVersionStr: StateFlow<String> = _latestVersionStr.asStateFlow()

    private var apkDownloadUrl: String? = null
    private var downloadedApkUri: Uri? = null

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val type = Types.newParameterizedType(List::class.java, NoteEntity::class.java)
    private val adapter = moshi.adapter<List<NoteEntity>>(type)

    companion object {
        /** Buffer compartido para copias de stream en ZIP. */
        private const val STREAM_BUFFER_SIZE = 64 * 1024

        /** Umbral a partir del cual se usa temp file en vez de RAM para STORED entries. */
        private const val MEMORY_COPY_THRESHOLD = 5L * 1024 * 1024

        /** Versión actual del formato de backup. */
        private const val BACKUP_FORMAT_VERSION = 2
    }

    // ============================================================
    // Setters
    // ============================================================

    fun saveUserName(name: String) {
        viewModelScope.launch { settingsRepository.saveUserName(name) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { settingsRepository.saveGeminiApiKey(key) }
    }

    fun saveGroqApiKey(key: String) {
        viewModelScope.launch { settingsRepository.saveGroqApiKey(key) }
    }

    fun saveThemeMode(mode: Int) {
        viewModelScope.launch { settingsRepository.saveThemeMode(mode) }
    }

    fun saveRecordMode(mode: Int) {
        viewModelScope.launch { settingsRepository.saveRecordMode(mode) }
    }

    fun saveAiProvider(provider: Int) {
        viewModelScope.launch { settingsRepository.saveAiProvider(provider) }
    }

    fun saveBackgroundRecording(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveBackgroundRecording(enabled) }
    }

    fun saveLiveTranscript(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveLiveTranscript(enabled) }
    }

    fun saveAiLanguage(language: String) {
        viewModelScope.launch { settingsRepository.saveAiLanguage(language) }
    }

    fun saveAiTask(task: Int) {
        viewModelScope.launch { settingsRepository.saveAiTask(task) }
    }

    fun saveAiFormat(format: Int) {
        viewModelScope.launch { settingsRepository.saveAiFormat(format) }
    }

    fun saveAutoCompressionMode(mode: Int) {
        viewModelScope.launch { settingsRepository.saveAutoCompressionMode(mode) }
    }

    fun saveNativePicker(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveNativePicker(enabled) }
    }

    fun saveColorStyle(style: Int) {
        viewModelScope.launch { settingsRepository.saveColorStyle(style) }
    }

    fun applyAiPreferencesToAllNotes(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.resetAllSummaries()
                launch(Dispatchers.Main) { onResult("Preferences applied! Old AI results have been reset.") }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult("Failed to apply preferences: ${e.message}") }
            }
        }
    }

    // ============================================================
    // Backups — formato v2 (.obinotbak extendido)
    // ============================================================

    /**
     * Exporta un backup completo en formato .obinotbak v2:
     *
     *   notes.json               — notas, con audioPath como filename (no path absoluto)
     *   audio/{archivo}.mp4      — audio de cada nota
     *   labels.json              — catálogo completo de labels (nombre + color)
     *   settings.json            — preferencias del usuario (SIN API keys)
     *   obinot_backup_meta.json  — metadatos del formato
     *
     * Los lectores v1 (Binot 1.x) pueden restaurar este archivo porque ignoran
     * las entradas que no conocen. Los lectores v2 leen todo.
     */
    fun exportBackup(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notes = noteRepository.getAllNotesSync()
                val labels = labelRepository.getAllLabelsSync()

                val userName = settingsRepository.userNameFlow.first()
                val themeMode = settingsRepository.themeModeFlow.first()
                val recordMode = settingsRepository.recordModeFlow.first()
                val aiProvider = settingsRepository.aiProviderFlow.first()
                val aiLanguage = settingsRepository.aiLanguageFlow.first()
                val aiTask = settingsRepository.aiTaskFlow.first()
                val aiFormat = settingsRepository.aiFormatFlow.first()
                val autoCompressionMode = settingsRepository.autoCompressionModeFlow.first()
                val liveTranscript = settingsRepository.liveTranscriptFlow.first()
                val backgroundRecording = settingsRepository.backgroundRecordingFlow.first()
                val nativePicker = settingsRepository.nativePickerFlow.first()
                val colorStyle = settingsRepository.colorStyleFlow.first()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zos ->
                        // --- notes.json ---
                        val notesForJson = notes.map { note ->
                            val audioFileName = note.audioPath?.let { File(it).name }
                            note.copy(audioPath = audioFileName)
                        }
                        val notesJson = adapter.toJson(notesForJson)
                        writeBytesToZip(zos, "notes.json", notesJson.toByteArray(Charsets.UTF_8))

                        // --- audio/* ---
                        notes.forEach { note ->
                            val audioPath = note.audioPath ?: return@forEach
                            val audioFile = File(audioPath)
                            if (audioFile.exists()) {
                                writeStoredFileToZip(zos, "audio/${audioFile.name}", audioFile)
                            }
                        }

                        // --- labels.json ---
                        val labelsArray = org.json.JSONArray()
                        labels.forEach { label ->
                            labelsArray.put(JSONObject().apply {
                                put("name", label.name)
                                put("colorHex", label.colorHex)
                            })
                        }
                        val labelsObj = JSONObject().apply {
                            put("version", BACKUP_FORMAT_VERSION)
                            put("labels", labelsArray)
                        }
                        writeBytesToZip(zos, "labels.json", labelsObj.toString().toByteArray(Charsets.UTF_8))

                        // --- settings.json (SIN API keys) ---
                        // Las keys nunca se exportan. El import respeta las keys que
                        // ya tiene el dispositivo destino.
                        val settingsObj = JSONObject().apply {
                            put("userName", userName)
                            put("themeMode", themeMode)
                            put("recordMode", recordMode)
                            put("aiProvider", aiProvider)
                            put("aiLanguage", aiLanguage)
                            put("aiTask", aiTask)
                            put("aiFormat", aiFormat)
                            put("autoCompressionMode", autoCompressionMode)
                            put("liveTranscriptEnabled", liveTranscript)
                            put("backgroundRecordingEnabled", backgroundRecording)
                            put("nativePickerEnabled", nativePicker)
                            put("colorStyle", colorStyle)
                        }
                        writeBytesToZip(zos, "settings.json", settingsObj.toString().toByteArray(Charsets.UTF_8))

                        // --- obinot_backup_meta.json ---
                        val metaObj = JSONObject().apply {
                            put("formatVersion", BACKUP_FORMAT_VERSION)
                            put("createdBy", "Obinot")
                            put("exportedAt", System.currentTimeMillis())
                            put("appVersion", "2.0.0")
                        }
                        writeBytesToZip(zos, "obinot_backup_meta.json", metaObj.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                launch(Dispatchers.Main) { onResult("Backup successful!") }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult("Backup failed: ${e.message}") }
            }
        }
    }

    // ============================================================
    // Backups — formato legacy (.binotbak de Binot 1.x)
    // ============================================================

    /**
     * Exporta un backup en el formato original de Binot 1.x:
     *
     *   notes.json           — notas
     *   audio/{archivo}.mp4  — audio de cada nota
     *
     * Sin labels, sin settings, sin meta. Es el formato exacto que espera
     * Binot 1.x para restaurar. Se ofrece como opción explícita en Settings
     * para usuarios que necesiten máxima compatibilidad hacia atrás.
     */
    fun exportBackupLegacy(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notes = noteRepository.getAllNotesSync()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zos ->
                        val notesForJson = notes.map { note ->
                            val audioFileName = note.audioPath?.let { File(it).name }
                            note.copy(audioPath = audioFileName)
                        }
                        val jsonStr = adapter.toJson(notesForJson)
                        writeBytesToZip(zos, "notes.json", jsonStr.toByteArray(Charsets.UTF_8))

                        notes.forEach { note ->
                            val audioPath = note.audioPath ?: return@forEach
                            val audioFile = File(audioPath)
                            if (audioFile.exists()) {
                                writeStoredFileToZip(zos, "audio/${audioFile.name}", audioFile)
                            }
                        }
                    }
                }
                launch(Dispatchers.Main) { onResult("Legacy backup successful!") }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult("Legacy backup failed: ${e.message}") }
            }
        }
    }

    // ============================================================
    // Import — detecta v2 vs v1 automáticamente
    // ============================================================

    /**
     * Importa un backup .obinotbak / .binotbak.
     *
     * Detección de formato:
     *   - v2: tiene labels.json o settings.json o obinot_backup_meta.json
     *   - v1: solo notes.json + audio/*
     *
     * En v2, los settings y labels se aplican ANTES de las notas, para que las
     * notas importadas resuelvan sus colores correctamente desde el catálogo nuevo.
     *
     * Las API keys NO se importan — el JSON exportado no las contiene y las
     * keys actuales del dispositivo quedan intactas.
     *
     * Fallback final: si el archivo no es un ZIP válido, se intenta leer como
     * JSON puro (backups muy viejos de Binot pre-ZIP).
     */
    fun importBackup(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                var notesJson: String? = null
                var labelsJson: String? = null
                var settingsJson: String? = null
                val extractedAudioFiles = mutableMapOf<String, File>()

                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                when {
                                    entry.name == "notes.json" -> {
                                        notesJson = zis.bufferedReader(Charsets.UTF_8).readText()
                                    }
                                    entry.name == "labels.json" -> {
                                        labelsJson = zis.bufferedReader(Charsets.UTF_8).readText()
                                    }
                                    entry.name == "settings.json" -> {
                                        settingsJson = zis.bufferedReader(Charsets.UTF_8).readText()
                                    }
                                    entry.name.startsWith("audio/") -> {
                                        val fileName = entry.name.removePrefix("audio/")
                                        if (fileName.isNotEmpty()) {
                                            val destFile = File(audioDir, fileName)
                                            destFile.outputStream().use { zis.copyTo(it) }
                                            extractedAudioFiles[fileName] = destFile
                                        }
                                    }
                                    // obinot_backup_meta.json y cualquier entrada futura
                                    // se ignoran silenciosamente.
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Exception) {
                    // No es un ZIP válido. Se intenta el fallback de JSON puro abajo.
                }

                if (notesJson == null) {
                    val sb = StringBuilder()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line = reader.readLine()
                            while (line != null) { sb.append(line); line = reader.readLine() }
                        }
                    }
                    notesJson = sb.toString()
                }

                val notes = adapter.fromJson(notesJson!!)
                if (notes == null) {
                    launch(Dispatchers.Main) { onResult("Invalid backup file.") }
                    return@launch
                }

                // Aplicar settings primero (v2). Si el JSON es v1 (no tiene
                // settings.json), este bloque no se ejecuta y no se toca nada.
                if (settingsJson != null) {
                    try {
                        applyImportedSettings(settingsJson)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Aplicar labels después (v2). Antes de insertar las notas para
                // que el catálogo ya tenga los colores correctos.
                if (labelsJson != null) {
                    try {
                        applyImportedLabels(labelsJson)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val remappedNotes = notes.map { note ->
                    val audioFileName = note.audioPath
                    val finalAudioPath = if (audioFileName != null) {
                        extractedAudioFiles[audioFileName]?.absolutePath ?: note.audioPath
                    } else null
                    note.copy(audioPath = finalAudioPath)
                }
                noteRepository.deleteAllNotes()
                noteRepository.insertNotes(remappedNotes)

                val isV2 = labelsJson != null || settingsJson != null
                val suffix = if (isV2) "" else " (legacy format)"
                launch(Dispatchers.Main) { onResult("Restore successful!$suffix") }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult("Restore failed: ${e.message}") }
            }
        }
    }

    private suspend fun applyImportedSettings(settingsJson: String) {
        val obj = JSONObject(settingsJson)
        if (obj.has("userName")) settingsRepository.saveUserName(obj.optString("userName", ""))
        if (obj.has("themeMode")) settingsRepository.saveThemeMode(obj.optInt("themeMode", 0))
        if (obj.has("recordMode")) settingsRepository.saveRecordMode(obj.optInt("recordMode", 0))
        if (obj.has("aiProvider")) settingsRepository.saveAiProvider(obj.optInt("aiProvider", 0))
        if (obj.has("aiLanguage")) settingsRepository.saveAiLanguage(obj.optString("aiLanguage", "English"))
        if (obj.has("aiTask")) settingsRepository.saveAiTask(obj.optInt("aiTask", 0))
        if (obj.has("aiFormat")) settingsRepository.saveAiFormat(obj.optInt("aiFormat", 0))
        if (obj.has("autoCompressionMode")) settingsRepository.saveAutoCompressionMode(obj.optInt("autoCompressionMode", 1))
        if (obj.has("liveTranscriptEnabled")) settingsRepository.saveLiveTranscript(obj.optBoolean("liveTranscriptEnabled", false))
        if (obj.has("backgroundRecordingEnabled")) settingsRepository.saveBackgroundRecording(obj.optBoolean("backgroundRecordingEnabled", false))
        if (obj.has("nativePickerEnabled")) settingsRepository.saveNativePicker(obj.optBoolean("nativePickerEnabled", false))
        if (obj.has("colorStyle")) settingsRepository.saveColorStyle(obj.optInt("colorStyle", 0))
        // Nota: las API keys NO se importan. El JSON exportado no las contiene
        // por diseño, y las keys actuales del dispositivo quedan intactas.
    }

    private suspend fun applyImportedLabels(labelsJson: String) {
        val obj = JSONObject(labelsJson)
        val labelsArray = obj.optJSONArray("labels") ?: return
        for (i in 0 until labelsArray.length()) {
            val labelObj = labelsArray.getJSONObject(i)
            val name = labelObj.optString("name", "").trim()
            if (name.isBlank()) continue
            val hex = labelObj.optString("colorHex", LabelEntity.DEFAULT_COLOR)
            val existing = labelRepository.getLabel(name)
            if (existing == null) {
                labelRepository.createLabel(name, hex)
            } else {
                labelRepository.updateColor(name, hex)
            }
        }
    }

    // ============================================================
    // ZIP helpers
    // ============================================================

    /** Escribe bytes con DEFLATED (bueno para JSONs de texto). */
    private fun writeBytesToZip(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName).apply { method = ZipEntry.DEFLATED }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    /**
     * Escribe un archivo con STORED (sin compresión). Bueno para audio, que ya
     * viene comprimido y solo gastaría CPU con DEFLATE.
     *
     * STORED requiere CRC32 y size precalculados. Para archivos chicos (≤ 5 MB)
     * se lee todo a RAM y se calcula ahí. Para archivos grandes se lee a un
     * temp file calculando CRC en la misma pasada, después se copia del temp
     * al ZIP (con el page cache caliente, así que la segunda lectura no toca disco).
     */
    private fun writeStoredFileToZip(zos: ZipOutputStream, entryName: String, source: File) {
        val fileSize = source.length()

        if (fileSize <= MEMORY_COPY_THRESHOLD) {
            val data = source.readBytes()
            val crc = CRC32().apply { update(data) }
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = data.size.toLong()
                this.crc = crc.value
            }
            zos.putNextEntry(entry)
            zos.write(data)
            zos.closeEntry()
            return
        }

        val tempFile = File.createTempFile("obinot_backup_", ".tmp", source.parentFile)
        try {
            val crc = CRC32()
            var size = 0L
            source.inputStream().buffered(STREAM_BUFFER_SIZE).use { input ->
                tempFile.outputStream().buffered(STREAM_BUFFER_SIZE).use { output ->
                    val buf = ByteArray(STREAM_BUFFER_SIZE)
                    var len: Int
                    while (input.read(buf).also { len = it } > 0) {
                        crc.update(buf, 0, len)
                        output.write(buf, 0, len)
                        size += len
                    }
                }
            }
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                this.size = size
                compressedSize = size
                this.crc = crc.value
            }
            zos.putNextEntry(entry)
            tempFile.inputStream().buffered(STREAM_BUFFER_SIZE).use { input ->
                val buf = ByteArray(STREAM_BUFFER_SIZE)
                var len: Int
                while (input.read(buf).also { len = it } > 0) {
                    zos.write(buf, 0, len)
                }
            }
            zos.closeEntry()
        } finally {
            tempFile.delete()
        }
    }

    // ============================================================
    // App update
    // ============================================================

    fun checkForUpdate(currentVersion: String) {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val release = RetrofitClient.githubService.getLatestRelease()
                _latestVersionStr.value = release.tag_name
                if (isVersionGreater(release.tag_name, currentVersion)) {
                    apkDownloadUrl = release.assets?.firstOrNull()?.browser_download_url
                    if (apkDownloadUrl != null) {
                        _updateState.value = UpdateState.Available
                    } else {
                        _latestVersionStr.value = "No APK File found in Release"
                        _updateState.value = UpdateState.Error
                    }
                } else {
                    delay(500)
                    _updateState.value = UpdateState.Idle
                }
            } catch (e: HttpException) {
                e.printStackTrace()
                if (e.code() == 403) _latestVersionStr.value = "Server Limit (Try again in 1 hour)"
                else if (e.code() == 404) _latestVersionStr.value = "No Release Available"
                else _latestVersionStr.value = "HTTP Error: ${e.code()}"
                _updateState.value = UpdateState.Error
            } catch (e: Exception) {
                e.printStackTrace()
                _latestVersionStr.value = "Network Error (Check Internet)"
                _updateState.value = UpdateState.Error
            }
        }
    }

    private fun isVersionGreater(latest: String, current: String): Boolean {
        val l = latest.replace("v", "").replace("V", "").split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.replace("v", "").replace("V", "").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lVal = l.getOrNull(i) ?: 0
            val cVal = c.getOrNull(i) ?: 0
            if (lVal > cVal) return true
            if (lVal < cVal) return false
        }
        return false
    }

    fun startDownload(context: Context) {
        val url = apkDownloadUrl ?: return
        _updateState.value = UpdateState.Downloading
        _downloadProgress.value = 0

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Obinot Update ${_latestVersionStr.value}")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Obinot_${_latestVersionStr.value}.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        viewModelScope.launch(Dispatchers.IO) {
            var isDownloading = true
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1 && statusIndex != -1) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val bytesTotal = cursor.getInt(bytesTotalIndex)
                        val status = cursor.getInt(statusIndex)

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            _downloadProgress.value = 100
                            downloadedApkUri = downloadManager.getUriForDownloadedFile(downloadId)
                            _updateState.value = UpdateState.Downloaded
                            isDownloading = false
                            downloadedApkUri?.let { uri -> promptInstall(context, uri) }
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            _latestVersionStr.value = "Download Failed by System"
                            _updateState.value = UpdateState.Error
                            isDownloading = false
                        } else {
                            if (bytesTotal > 0) {
                                _downloadProgress.value = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                            }
                        }
                    }
                    cursor.close()
                }
                delay(500)
            }
        }
    }

    fun promptInstall(context: Context, uri: Uri? = downloadedApkUri) {
        if (uri == null) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _latestVersionStr.value = "Installation Failed"
            _updateState.value = UpdateState.Error
        }
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val MEMORY_COPY_THRESHOLD = 5L * 1024 * 1024
        private const val BACKUP_FORMAT_VERSION = 2

        fun provideFactory(
            settingsRepository: SettingsRepository,
            noteRepository: NoteRepository,
            labelRepository: LabelRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(settingsRepository, noteRepository, labelRepository) as T
                }
            }
    }
}