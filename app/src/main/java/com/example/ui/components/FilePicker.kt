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

/**
 * Opciones de ordenamiento disponibles.
 */
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

/**
 * Bottom sheet que muestra la lista de audios del dispositivo.
 * Al tocar un archivo, se devuelve su Uri a través de [onFileSelected].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFilePickerSheet(
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf<List<AudioFileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(AudioSortOrder.DATE_MODIFIED_DESC) }

    // FIX: sin este permiso el cursor de MediaStore vuelve vacío y la hoja
    // se veía siempre como "no hay archivos de audio".
    val audioPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, audioPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(audioPermission)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            audioFiles = withContext(Dispatchers.IO) { queryAudioFiles(context) }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    val sortedFiles = remember(audioFiles, sortOrder) {
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
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Choose audio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* ciclo de sort orders */ }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }
            }

            SortOrderRow(
                current = sortOrder,
                onSelect = { sortOrder = it }
            )

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (sortedFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasPermission) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Obinot needs permission to read audio files on this device.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { permissionLauncher.launch(audioPermission) }) {
                                Text("Grant access")
                            }
                        }
                    } else {
                        Text(
                            text = "No audio files found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedFiles, key = { it.uri.toString() }) { file ->
                        AudioFileRow(file = file, onSelect = { onFileSelected(file.uri) })
                    }
                }
            }
        }
    }
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
                Text(
                    text = file.durationFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = file.sizeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = file.formatFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Consulta MediaStore para obtener la lista de archivos de audio.
 * Devuelve solo metadatos, no lee el contenido de los archivos.
 */
private fun queryAudioFiles(
    context: Context
): List<AudioFileInfo> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_MODIFIED
    )
    // FIX: el filtro IS_MUSIC/IS_PODCAST dejaba fuera justamente lo que la gente quiere
    // importar (notas de voz, audios de WhatsApp, grabaciones). Ahora se listan todos
    // los audios y el orden se aplica en memoria, no en SQL.
    val selection: String? = null
    val sort = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

    val result = mutableListOf<AudioFileInfo>()
    context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
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
    return result
}