package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GithubRelease
import com.example.data.LabelEntity
import com.example.data.LabelRepository
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class HistoryViewModel(
    private val repository: NoteRepository,
    private val labelRepository: LabelRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLabels = MutableStateFlow<Set<String>>(emptySet())
    val selectedLabels: StateFlow<Set<String>> = _selectedLabels.asStateFlow()

    private val _isMultiSelectLabelMode = MutableStateFlow(false)
    val isMultiSelectLabelMode: StateFlow<Boolean> = _isMultiSelectLabelMode.asStateFlow()

    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode.asStateFlow()

    private val _latestRelease = MutableStateFlow<GithubRelease?>(null)
    val latestRelease: StateFlow<GithubRelease?> = _latestRelease.asStateFlow()

    /** Map label name → hex color. Vacío si aún no se cargó. */
    val labelColors: StateFlow<Map<String, String>> = labelRepository.allLabels
        .map { labels -> labels.associate { it.name to it.colorHex } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val trashedNotes: StateFlow<List<NoteEntity>> = repository.trashedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Catálogo de labels únicos del sistema (custom + los que aparecen en notas).
     *
     * Antes: repository.allNotes.map { notes -> ... } cargaba TODAS las entidades
     * (rawText, summary, highlightsInfo) y las recorría para extraer los labels.
     * Ahora: combine de dos queries livianas — la system note (1 fila) y la
     * proyección de la columna label (strings planos). Sin cargar entidades.
     */
    val uniqueLabels: StateFlow<List<String>> = combine(
        repository.getAllLabelStrings(),
        repository.getSystemNote()
    ) { labelStrings, sysNote ->
        val customLabels = sysNote?.rawText
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val noteLabels = labelStrings.flatMap { raw ->
            raw.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }
        (customLabels + noteLabels).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotes: StateFlow<List<NoteEntity>> = combine(
        repository.allNotes, _searchQuery, _selectedLabels, _sortMode
    ) { notes, query, labels, sort ->

        val realNotes = notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }

        val labelFilteredNotes = if (labels.isEmpty()) realNotes else realNotes.filter { note ->
            val noteLabels = note.label?.split("|")?.map { it.trim() }?.toSet() ?: emptySet()
            labels.all { it in noteLabels }
        }

        val searchedNotes = if (query.isBlank()) {
            labelFilteredNotes
        } else {
            labelFilteredNotes.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.rawText.contains(query, ignoreCase = true) ||
                (it.summary?.contains(query, ignoreCase = true) == true)
            }
        }

        when (sort) {
            1 -> searchedNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.timestamp })
            2 -> searchedNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.title.lowercase() })
            else -> searchedNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.timestamp })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Sincroniza el catálogo de labels con los labels existentes en notas.
        // INSERT OR IGNORE, así que es idempotente. Si un label se crea desde el sistema
        // viejo (system note), acá se le asigna color default automáticamente.
        viewModelScope.launch(Dispatchers.IO) {
            uniqueLabels.collect { labels ->
                if (labels.isNotEmpty()) {
                    try {
                        labelRepository.ensureLabelsExist(labels)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // ============================================================
    // Label color API
    // ============================================================

    /** Asigna un color a un label existente. */
    fun setLabelColor(label: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            labelRepository.updateColor(label.trim(), colorHex)
        }
    }

    /** Devuelve el color asignado a un label, o DEFAULT_COLOR si no existe. */
    fun getLabelColor(label: String): String {
        return labelColors.value[label] ?: LabelEntity.DEFAULT_COLOR
    }

    // ============================================================
    // Label filters
    // ============================================================

    fun toggleLabelFilter(label: String) {
        if (_isMultiSelectLabelMode.value) {
            _selectedLabels.value = if (label in _selectedLabels.value) {
                _selectedLabels.value - label
            } else {
                _selectedLabels.value + label
            }
        } else {
            _selectedLabels.value = if (_selectedLabels.value == setOf(label)) {
                emptySet()
            } else {
                setOf(label)
            }
        }
    }

    fun clearLabelFilter() {
        _selectedLabels.value = emptySet()
    }

    fun setMultiSelectLabelMode(enabled: Boolean) {
        _isMultiSelectLabelMode.value = enabled
        if (!enabled) {
            _selectedLabels.value = emptySet()
        }
    }

    fun deleteMultipleLabels(labels: Set<String>) {
        labels.forEach { deleteLabel(it) }
    }

    fun setSortMode(mode: Int) {
        _sortMode.value = mode
    }

    // ============================================================
    // Label CRUD (sincronizado con LabelRepository)
    // ============================================================

    fun createIndependentLabel(label: String, colorHex: String = LabelEntity.DEFAULT_COLOR) {
        val cleanLabel = label.replace("|", "").trim()
        if (cleanLabel.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val sysNote = repository.getSystemNoteSync()

            // 1. Actualizar/crear la nota sintética
            if (sysNote != null) {
                val existingLabels = sysNote.rawText.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                if (!existingLabels.contains(cleanLabel)) {
                    existingLabels.add(cleanLabel)
                    repository.update(sysNote.copy(
                        rawText = existingLabels.joinToString("|"),
                        timestamp = System.currentTimeMillis()
                    ))
                }
            } else {
                val newSysNote = NoteEntity(
                    title = "[[BINOT_SYSTEM_LABELS]]",
                    rawText = cleanLabel,
                    summary = null,
                    timestamp = System.currentTimeMillis()
                )
                repository.insert(newSysNote)
            }

            // 2. Crear la entrada en el catálogo con su color
            labelRepository.createLabel(cleanLabel, colorHex)
        }
    }

    fun renameLabel(oldLabel: String, newLabel: String) {
        val cleanOld = oldLabel.trim()
        val cleanNew = newLabel.replace("|", "").trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return

        viewModelScope.launch(Dispatchers.IO) {
            val notes = repository.getAllNotesSync()

            // 1. Actualizar todas las notas que usan el label viejo
            notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }.forEach { note ->
                val labels = note.label?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (labels.contains(cleanOld)) {
                    val updatedLabels = labels.map { if (it == cleanOld) cleanNew else it }.distinct()
                    repository.update(note.copy(
                        label = updatedLabels.joinToString("|"),
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }

            // 2. Actualizar la nota sintética
            val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            if (sysNote != null) {
                val existingLabels = sysNote.rawText.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                if (existingLabels.remove(cleanOld)) {
                    existingLabels.add(cleanNew)
                    repository.update(sysNote.copy(
                        rawText = existingLabels.joinToString("|"),
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }

            // 3. Renombrar en el catálogo de colores.
            // Importante: como LabelEntity tiene name como PK, renombrar equivale a
            // borrar el viejo y crear el nuevo. Preservamos el color.
            val oldEntity = labelRepository.getLabel(cleanOld)
            if (oldEntity != null) {
                labelRepository.deleteLabel(cleanOld)
                labelRepository.createLabel(cleanNew, oldEntity.colorHex)
            } else {
                // Si no existía en el catálogo, lo creamos con color default
                labelRepository.createLabel(cleanNew)
            }

            // 4. Actualizar filtro activo si corresponde
            if (cleanOld in _selectedLabels.value) {
                _selectedLabels.value = (_selectedLabels.value - cleanOld) + cleanNew
            }
        }
    }

    fun deleteLabel(label: String) {
        val cleanLabel = label.trim()
        if (cleanLabel.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val notes = repository.getAllNotesSync()

            // 1. Quitar el label de todas las notas
            notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }.forEach { note ->
                val labels = note.label?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (labels.contains(cleanLabel)) {
                    val updatedLabels = labels.filter { it != cleanLabel }
                    val newLabelString = if (updatedLabels.isEmpty()) null else updatedLabels.joinToString("|")
                    repository.update(note.copy(
                        label = newLabelString,
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }

            // 2. Quitar de la nota sintética
            val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            if (sysNote != null) {
                val existingLabels = sysNote.rawText.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                if (existingLabels.remove(cleanLabel)) {
                    if (existingLabels.isEmpty()) {
                        repository.deleteById(sysNote.id)
                    } else {
                        repository.update(sysNote.copy(
                            rawText = existingLabels.joinToString("|"),
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            }

            // 3. Borrar del catálogo de colores
            labelRepository.deleteLabel(cleanLabel)

            // 4. Quitar del filtro activo
            if (cleanLabel in _selectedLabels.value) {
                _selectedLabels.value = _selectedLabels.value - cleanLabel
            }
        }
    }

    // ============================================================
    // App update
    // ============================================================

    fun checkForAppUpdate(currentVersion: String) {
        if (_latestRelease.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val release = RetrofitClient.githubService.getLatestRelease()
                if (isVersionGreater(release.tag_name, currentVersion)) {
                    _latestRelease.value = release
                }
            } catch (e: Exception) { e.printStackTrace() }
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

    fun dismissUpdateNotification() {
        _latestRelease.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ============================================================
    // Trash / Undo / Bulk ops
    // ============================================================

    private val _recentlyDeleted = MutableStateFlow<List<NoteEntity>>(emptyList())
    val recentlyDeleted: StateFlow<List<NoteEntity>> = _recentlyDeleted.asStateFlow()

    fun deleteMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            val notesToTrash = ids.mapNotNull { repository.getNoteById(it) }
            _recentlyDeleted.value = notesToTrash
            notesToTrash.forEach { repository.update(it.copy(isTrashed = true)) }
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _recentlyDeleted.value.forEach { note ->
                repository.update(note.copy(isTrashed = false))
            }
            _recentlyDeleted.value = emptyList()
        }
    }

    fun clearRecentlyDeleted() {
        _recentlyDeleted.value = emptyList()
    }

    fun restoreMultipleFromTrash(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { id ->
                val note = repository.getNoteById(id)
                if (note != null) repository.update(note.copy(isTrashed = false))
            }
        }
    }

    fun deletePermanentlyMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { id -> repository.deleteById(id) }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun cloneMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { id ->
                val note = repository.getNoteById(id)
                if (note != null) {
                    val clonedNote = note.copy(id = 0, title = "${note.title} (Copy)", isPinned = false)
                    repository.insert(clonedNote)
                }
            }
        }
    }

    fun togglePinMultiple(ids: Set<Int>, pinState: Boolean) {
        viewModelScope.launch {
            ids.forEach { id ->
                val note = repository.getNoteById(id)
                if (note != null) {
                    repository.update(note.copy(isPinned = pinState))
                }
            }
        }
    }

    fun importAudio(context: Context, uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = "Binot_Import_${System.currentTimeMillis()}.mp3"
                val file = File(context.cacheDir, fileName)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val note = NoteEntity(title = "Imported Audio", rawText = "", summary = null, audioPath = file.absolutePath)
                val id = repository.insert(note).toInt()
                launch(Dispatchers.Main) { onResult(id) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    companion object {
        fun provideFactory(
            repository: NoteRepository,
            labelRepository: LabelRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(repository, labelRepository) as T
                }
            }
    }
}