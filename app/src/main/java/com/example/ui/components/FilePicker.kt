package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Representa un archivo de audio encontrado por MediaStore.
 */
data class AudioFileInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val dateModified: Long = 0L
) {
    val durationFormatted: String
        get() {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }

    val formatFormatted: String
        get() = mimeType.substringAfterLast("/").uppercase()
}

/** Representa un archivo .binot (nota exportada) encontrado por MediaStore. */
data class BinotNoteFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateModified: Long
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

enum class AudioSortOrder(val label: String) {
    DATE_MODIFIED_DESC("Newest"),
    DATE_MODIFIED_ASC("Oldest"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DURATION_DESC("Longest"),
    DURATION_ASC("Shortest"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest")
}

/** Qué pestaña del picker unificado está activa. */
enum class PickerTab(val label: String) {
    AUDIO("Audio"),
    NOTES(".binot Notes")
}

/**
 * Picker unificado: audios del dispositivo + notas .binot exportadas (el formato nativo
 * de Obinot, compatible con el Binot original). Usado tanto por RecordScreen como por
 * HistoryScreen para que la experiencia de importar sea idéntica en ambos lugares.
 *
 * LIMITACIÓN REAL DE ANDROID (no es un bug): a partir de Android 13, sin el permiso
 * MANAGE_EXTERNAL_STORAGE (que Play Store restringe fuertemente y no tiene sentido pedir
 * para esta app), MediaStore solo puede listar de forma fiable archivos .binot que la
 * propia Obinot exportó. Un .binot compartido por otra app (WhatsApp, un navegador, etc.)
 * puede no aparecer en la lista. Por eso la pestaña de Notas siempre incluye un botón
 * "Browse files" que abre el selector del sistema como respaldo garantizado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObinotFilePickerSheet(
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    initialTab: PickerTab = PickerTab.AUDIO
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(initialTab) }

    var audioFiles by remember { mutableStateOf<List<AudioFileInfo>>(emptyList()) }
    var binotFiles by remember { mutableStateOf<List<BinotNoteFileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(AudioSortOrder.DATE_MODIFIED_DESC) }

    // Sin este permiso el cursor de MediaStore vuelve vacío para ambas colecciones.
    val storagePermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, storagePermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(storagePermission)
    }

    LaunchedEffect(hasPermission, activeTab) {
        isLoading = true
        if (hasPermission) {
            when (activeTab) {
                PickerTab.AUDIO -> audioFiles = withContext(Dispatchers.IO) { queryAudioFiles(context) }
                PickerTab.NOTES -> binotFiles = withContext(Dispatchers.IO) { queryBinotFiles(context) }
            }
        }
        isLoading = false
    }

    val sortedAudio = remember(audioFiles, sortOrder) {
        when (sortOrder) {
            AudioSortOrder.DATE_MODIFIED_DESC -> audioFiles.sortedByDescending { it.dateModified }
            AudioSortOrder.DATE_MODIFIED_ASC -> audioFiles.sortedBy { it.dateModified }
            AudioSortOrder.NAME_ASC -> audioFiles.sortedBy { it.name.lowercase() }
            AudioSortOrder.NAME_DESC -> audioFiles.sortedByDescending { it.name.lowercase() }
            AudioSortOrder.DURATION_DESC -> audioFiles.sortedByDescending { it.durationMs }
            AudioSortOrder.DURATION_ASC -> audioFiles.sortedBy { it.durationMs }
            AudioSortOrder.SIZE_DESC -> audioFiles.sortedByDescending { it.sizeBytes }
            AudioSortOrder.SIZE_ASC -> audioFiles.sortedBy { it.sizeBytes }
        }
    }
    val sortedNotes = remember(binotFiles) { binotFiles.sortedByDescending { it.dateModified } }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onFileSelected(uri) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = if (activeTab == PickerTab.AUDIO) Icons.Default.Audiotrack else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Import to Obinot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PickerTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PickerTab.entries.size),
                        onClick = { activeTab = tab },
                        selected = activeTab == tab
                    ) { Text(tab.label) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (activeTab == PickerTab.AUDIO) {
                SortOrderRow(current = sortOrder, onSelect = { sortOrder = it })
                Spacer(Modifier.height(8.dp))
            } else {
                // Respaldo garantizado: el selector del sistema siempre encuentra el archivo,
                // aunque MediaStore no lo haya indexado (ver nota de la limitación arriba).
                OutlinedButton(
                    onClick = { safLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Browse files")
                }
                Spacer(Modifier.height(8.dp))
            }

            val isEmpty = if (activeTab == PickerTab.AUDIO) sortedAudio.isEmpty() else sortedNotes.isEmpty()

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (isEmpty) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (!hasPermission) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Obinot needs permission to read files on this device.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { permissionLauncher.launch(storagePermission) }) {
                                Text("Grant access")
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (activeTab == PickerTab.AUDIO) "No audio files found."
                                       else "No .binot notes found here. On newer Android versions, only notes exported by Obinot itself show up automatically — use \"Browse files\" above for anything else.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else if (activeTab == PickerTab.AUDIO) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedAudio, key = { it.uri.toString() }) { file ->
                        AudioFileRow(file = file, onSelect = { onFileSelected(file.uri) })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedNotes, key = { it.uri.toString() }) { file ->
                        BinotFileRow(file = file, onSelect = { onFileSelected(file.uri) })
                    }
                }
            }
        }
    }
}

/** Alias retrocompatible: abre el picker unificado directamente en la pestaña de Audio. */
@Composable
fun AudioFilePickerSheet(onDismiss: () -> Unit, onFileSelected: (Uri) -> Unit) {
    ObinotFilePickerSheet(onDismiss = onDismiss, onFileSelected = onFileSelected, initialTab = PickerTab.AUDIO)
}

@Composable
private fun SortOrderRow(
    current: AudioSortOrder,
    onSelect: (AudioSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(current.label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AudioSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioFileRow(
    file: AudioFileInfo,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.durationFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(file.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(file.formatFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BinotFileRow(
    file: BinotNoteFileInfo,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(file.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Consulta MediaStore para obtener la lista de archivos de audio, sin filtrar por tipo (música/podcast). */
private fun queryAudioFiles(context: Context): List<AudioFileInfo> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_MODIFIED
    )
    val sort = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

    val result = mutableListOf<AudioFileInfo>()
    try {
        context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                result.add(
                    AudioFileInfo(
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "Unnamed",
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "audio/*",
                        dateModified = cursor.getLong(dateCol)
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Cursor puede fallar si el permiso se revocó justo antes de la query.
    }
    return result
}

/**
 * Consulta MediaStore.Files buscando archivos .binot. Best-effort: en Android 13+, sin
 * MANAGE_EXTERNAL_STORAGE, esto solo ve archivos que la propia app indexó (los que ella
 * misma exportó). Es una limitación de la plataforma, no del código — por eso el picker
 * siempre ofrece "Browse files" como respaldo.
 */
private fun queryBinotFiles(context: Context): List<BinotNoteFileInfo> {
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    // LIKE es case-insensitive para ASCII en SQLite, así que esto cubre .binot y .BINOT.
    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
    val args = arrayOf("%.binot")
    val sort = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

    val result = mutableListOf<BinotNoteFileInfo>()
    try {
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                result.add(
                    BinotNoteFileInfo(
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "Unnamed.binot",
                        sizeBytes = cursor.getLong(sizeCol),
                        dateModified = cursor.getLong(dateCol)
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Igual que arriba: se degrada a lista vacía y el botón "Browse files" sigue disponible.
    }
    return result
}