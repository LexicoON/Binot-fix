package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.GeminiModels
import com.example.data.GroqChatRequest
import com.example.data.GroqMessage
import com.example.data.GroqModels
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

    /**
     * Estado del toggle "Live Transcript" (Settings → Recording Mode → Accurate).
     * RecordScreen lo usa para decidir qué cartel mostrar durante la grabación:
     * - ON:  "Listening..." (hay recognizer corriendo)
     * - OFF: "Recording... (will transcribe with AI)" (solo se graba audio)
     */
    val liveTranscriptEnabled: StateFlow<Boolean> = settingsRepository.liveTranscriptFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()
    private var timerJob: Job? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var pendingAudioPath: String? = null

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
                    val notes = repository.getAllNotesSync()
                    val realNotes = notes.filterNot { it.title == "[[BINOT_SYSTEM_LABELS]]" }
                    val latest16 = realNotes.sortedByDescending { it.timestamp }.take(16)
                    _recentNotes.value = latest16
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1500)
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
            // Leer el flag de live transcript para pasárselo al manager.
            // Fast (mode 0) siempre corre el recognizer; Accurate (mode 1) lo hace
            // solo si el usuario lo pidió.
            val liveTranscript = liveTranscriptEnabled.value
            audioRecorderManager.startRecording(
                isEmulator = isEmulator,
                mode = recordMode,
                liveTranscriptEnabled = liveTranscript
            )
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

        val path = pendingAudioPath

        // Modo Accurate (1): se guarda el audio y, si el live transcript estaba
        // activado y el recognizer capturó algo, se adjunta como transcripción
        // preliminar con un marcador. Si no hay texto, se guarda como
        // "Pending Transcription" y el ResultViewModel dispara la transcripción
        // con IA automáticamente al abrir la nota.
        //
        // Modo Fast (0): se guarda solo el texto del recognizer. Si está vacío,
        // no se guarda la nota.
        val text = if (recordMode == 1) {
            val phoneText = recognizedText.value.trim()
            if (phoneText.isNotEmpty()) {
                "${AudioRecorderManager.PHONE_TRANSCRIPTION_MARKER}\n$phoneText"
            } else {
                AudioRecorderManager.PENDING_TRANSCRIPTION
            }
        } else {
            recognizedText.value.trim()
        }

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

        // Dynamic (provider == 2): los títulos se alternan entre Groq y Gemini para
        // repartir carga. Fuera de Dynamic, se usa el provider directo.
        if (provider == 2) {
            val effectiveProvider = if (settingsRepository.incrementMixCounter() % 2 == 0) 0 else 1
            val apiKey = if (effectiveProvider == 1) groqApiKey else geminiApiKey
            if (apiKey.isNotBlank() && recordMode == 0) {
                generateTitleForNote(id, text, effectiveProvider, apiKey)
            }
        } else {
            val apiKey = if (provider == 1) groqApiKey else geminiApiKey
            if (apiKey.isNotBlank() && recordMode == 0) {
                generateTitleForNote(id, text, provider, apiKey)
            }
        }

        pendingAudioPath = null
        return true
    }

    private fun generateTitleForNote(noteId: Int, text: String, provider: Int, apiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = "You are a title generator. Output ONLY a 3-5 word title in the same language as the input. No quotes, no explanation."
                val userPrompt = "Text:\n${text.take(500)}"

                val aiTitle = if (provider == 1) {
                    val request = GroqChatRequest(
                        model = GroqModels.GPT_OSS_20B,
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userPrompt)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $apiKey", request)
                        .choices?.firstOrNull()?.message?.content?.trim()
                } else {
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
                    )
                    RetrofitClient.service.generateContent(
                        model = GeminiModels.FLASH_LITE,
                        apiKey = apiKey,
                        request = request
                    ).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                }

                if (!aiTitle.isNullOrBlank()) {
                    val savedNote = repository.getNoteById(noteId)
                    if (savedNote != null) {
                        repository.update(savedNote.copy(title = aiTitle))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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