package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.LabelRepository
import com.example.data.NoteRepository
import com.example.data.SettingsRepository
import com.example.utils.AudioRecorderManager

class BinotApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val application: Application) {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "binot_db")
        .addMigrations(
            AppDatabase.MIGRATION_3_4, // Jembatan migrasi buat fitur Trash
            AppDatabase.MIGRATION_4_5, // Jembatan migrasi buat originalRawText
            AppDatabase.MIGRATION_5_6, // Jembatan migrasi buat highlightsInfo
            AppDatabase.MIGRATION_6_7, // Jembatan migrasi buat catálogo de labels con color
            AppDatabase.MIGRATION_7_8  // Jembatan migrasi buat índices compuestos
        )
        .fallbackToDestructiveMigration() // Pengaman terakhir: kalau ada migrasi yg kelewat/corrupt, di-reset alih-alih Force Close
        .build()
    }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(database.noteDao())
    }

    val labelRepository: LabelRepository by lazy {
        LabelRepository(database.labelDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(application)
    }

    val audioRecorderManager: AudioRecorderManager by lazy {
        AudioRecorderManager(application)
    }
}