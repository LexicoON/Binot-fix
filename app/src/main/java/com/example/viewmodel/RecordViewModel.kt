package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.GroqChatRequest
import com.example.data.GroqMessage
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.Part
import com.example.data.RetrofitClient
import com.example.data.SettingsRepository
import com.example.utils.AudioRecorderManager
import com.example.utils.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordViewModel(
    private val audioRecorderManager: AudioRecorderManager,
    private val repository: NoteRepository,
    private val geminiApiKey: String,
    private val groqApiKey: String,
    private val appContext: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = audioRecorderManager.isRecording
    val amplitude: StateFlow<Float> = audioRecorderManager.amplitude
    val recognizedText: StateFlow<String> = audioRecorderManager.recognizedText

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()
    private var timerJob: Job? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var pendingAudioPath: String? = null

    // State untuk 16 Catatan Terbaru
    private val _recentNotes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val recentNotes: StateFlow<List<NoteEntity>> = _recentNotes.asStateFlow()
    private var pollJob: Job? = null

    init {
        startPollingRecentNotes()
    }

    private fun startPollingRecentNotes() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    // Tarik data secara sinkronus lalu ambil 16 terbaru
                    val notes = repository.getAllNotesSync()
                    val latest16 = notes.sortedByDescending { it.timestamp }.take(16)
                    _recentNotes.value = latest16
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1500) // Polling tiap 1.5 detik agar UI tetep update
            }
        }
    }

    fun toggleRecording(isEmulator: Boolean, recordMode: Int) {
        if (isRecording.value) {
            pendingAudioPath = audioRecorderManager.stopRecording()
            stopTimer()
            _isPaused.value = false
            RecordingService.stop(appContext)
        } else {
            _isPaused.value = false
            pendingAudioPath = null
            audioRecorderManager.startRecording(isEmulator, recordMode)
            startTimer()
            maybeStartBackgroundService()
        }
    }

    fun stopRecordingInstant() {
        pendingAudioPath = audioRecorderManager.stopRecording()
        stopTimer()
        _isPaused.value = false
        _recordingSeconds.value = 0
        RecordingService.stop(appContext)
    }

    private fun maybeStartBackgroundService() {
        viewModelScope.launch {
            if (settingsRepository.backgroundRecordingFlow.first()) {
                RecordingService.start(appContext)
            }
        }
    }

    fun pauseRecording() {
        if (!isRecording.value || _isPaused.value) return
        _isPaused.value = true
        stopTimer()
        audioRecorderManager.pauseRecording()
    }

    fun resumeRecording() {
        if (!isRecording.value || !_isPaused.value) return
        _isPaused.value = false
        resumeTimer()
        audioRecorderManager.resumeRecording()
    }

    fun stopFromPaused(isEmulator: Boolean) {
        if (!_isPaused.value) return
        pendingAudioPath = audioRecorderManager.stopRecording()
        _isPaused.value = false
        _recordingSeconds.value = 0
        RecordingService.stop(appContext)
    }

    private fun startTimer() {
        _recordingSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun resumeTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    suspend fun saveNote(recordMode: Int, provider: Int = 0): Boolean {
        delay(300)

        val text = if (recordMode == 1) "Pending Transcription" else recognizedText.value.trim()
        val path = pendingAudioPath

        // Mencegah save kalau mode Google tapi teksnya kosong
        if (recordMode == 0 && text.isEmpty()) {
            return false
        }

        val note = NoteEntity(
            title = "",
            rawText = text,
            summary = null,
            isPinned = false,
            audioPath = path
        )

        val id = withContext(Dispatchers.IO) { repository.insert(note).toInt() }

        // MIX (provider == 2): titles use Groq (fast, lightweight), same as ResultViewModel
        val effectiveProvider = if (provider == 2) 1 else provider
        val apiKey = if (effectiveProvider == 1) groqApiKey else geminiApiKey

        if (apiKey.isNotBlank() && recordMode == 0) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val prompt = """
                        Buat judul singkat 3-5 kata dalam bahasa yang sama dengan teks berikut.
                        RULES:
                        - Hanya output judulnya saja, tanpa tanda kutip, tanpa penjelasan apapun.
                        - Maksimal 5 kata, padat dan informatif.
                        - Gunakan bahasa yang sama dengan teks input.
                        Teks: ${text.take(500)}
                    """.trimIndent()
                    val aiTitle = if (effectiveProvider == 1) {
                        // Groq path
                        val request = GroqChatRequest(
                            model = "openai/gpt-oss-20b",
                            messages = listOf(
                                GroqMessage(role = "system", content = "You are a title generator. Output ONLY a 3-5 word title in the same language as the input. No quotes, no explanation."),
                                GroqMessage(role = "user", content = prompt)
                            )
                        )
                        RetrofitClient.groqService.generateContent("Bearer $apiKey", request)
                            .choices?.firstOrNull()?.message?.content?.trim()
                    } else {
                        // Gemini path
                        val request = GenerateContentRequest(
                            contents = listOf(Content(parts = listOf(Part(text = prompt))))
                        )
                        RetrofitClient.service.generateContent(apiKey, request)
                            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    }

                    if (!aiTitle.isNullOrBlank()) {
                        val savedNote = repository.getNoteById(id)
                        if (savedNote != null) {
                            repository.update(savedNote.copy(title = aiTitle))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        pendingAudioPath = null
        return true
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.stopRecording()
        stopTimer()
        pollJob?.cancel()
        _isPaused.value = false
        RecordingService.stop(appContext)
    }

    companion object {
        fun provideFactory(
            audioRecorderManager: AudioRecorderManager,
            repository: NoteRepository,
            geminiApiKey: String,
            groqApiKey: String,
            appContext: Context,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordViewModel(audioRecorderManager, repository, geminiApiKey, groqApiKey, appContext, settingsRepository) as T
                }
            }
    }
}
