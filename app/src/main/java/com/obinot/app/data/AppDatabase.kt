package com.obinot.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, LabelEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN originalRawText TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN highlightsInfo TEXT DEFAULT NULL")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS labels (
                        name TEXT NOT NULL PRIMARY KEY,
                        colorHex TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                )
                """.trimIndent()
                )
            }
        }

        /**
         * 7 → 8: agrega índices compuestos para la lista de notas, el trash, y el
         * lookup de la system note. Los nombres coinciden exactamente con la
         * convención de Room (`index_<tabla>_<cols>`), si no la validación de
         * schema falla al abrir la DB.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_notes_isTrashed_isPinned_timestamp` " +
                    "ON `notes` (`isTrashed`, `isPinned`, `timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_notes_isTrashed_timestamp` " +
                    "ON `notes` (`isTrashed`, `timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_notes_title` " +
                    "ON `notes` (`title`)"
                )
            }
        }
    }
}