package com.rendy.classnote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rendy.classnote.data.local.dao.*
import com.rendy.classnote.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CourseEntity::class,
        CourseOverrideEntity::class,
        PeriodTimeEntity::class,
        ReminderEntity::class,
        ReminderNotificationEntity::class,
        com.rendy.classnote.data.local.entity.FormulaEntity::class,
        com.rendy.classnote.data.local.entity.ClassRecordEntity::class,
        com.rendy.classnote.data.local.entity.ClassRecordMediaEntity::class,
        com.rendy.classnote.data.local.entity.ApiLogEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class ClassNoteDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun courseOverrideDao(): CourseOverrideDao
    abstract fun periodTimeDao(): PeriodTimeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderNotificationDao(): ReminderNotificationDao
    abstract fun formulaDao(): com.rendy.classnote.data.local.dao.FormulaDao
    abstract fun classRecordDao(): com.rendy.classnote.data.local.dao.ClassRecordDao
    abstract fun classRecordMediaDao(): com.rendy.classnote.data.local.dao.ClassRecordMediaDao
    abstract fun apiLogDao(): com.rendy.classnote.data.local.dao.ApiLogDao

    companion object {
        @Volatile
        private var INSTANCE: ClassNoteDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1→v2 無 schema 變更，空 migration 避免 destructive fallback
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN colorHex TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN startDate TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN fullScreenAlarm INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN externalId TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN syncSource TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS formulas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        latex TEXT NOT NULL,
                        explanation TEXT NOT NULL DEFAULT '',
                        subject TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN dueTime TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN sourceName TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS class_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        timeLabel TEXT NOT NULL DEFAULT '',
                        textNote TEXT NOT NULL DEFAULT '',
                        aiSummary TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS class_record_media (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        isUploaded INTEGER NOT NULL DEFAULT 0,
                        durationMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE class_records ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS api_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        model TEXT NOT NULL,
                        requestPreview TEXT NOT NULL DEFAULT '',
                        responsePreview TEXT NOT NULL DEFAULT '',
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        isSuccess INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }

        fun getDatabase(context: Context): ClassNoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClassNoteDatabase::class.java,
                    "classnote_database"
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDefaultPeriodTimes(database.periodTimeDao())
                }
            }
        }

        /**
         * 預設節次時間（依規格）
         * 分鐘數 = 小時 × 60 + 分鐘
         */
        private suspend fun populateDefaultPeriodTimes(dao: PeriodTimeDao) {
            val defaults = listOf(
                PeriodTimeEntity(1, 8 * 60 + 10, 9 * 60),       // 08:10–09:00
                PeriodTimeEntity(2, 9 * 60 + 10, 10 * 60),      // 09:10–10:00
                PeriodTimeEntity(3, 10 * 60 + 10, 11 * 60),     // 10:10–11:00
                PeriodTimeEntity(4, 11 * 60 + 10, 12 * 60),     // 11:10–12:00
                PeriodTimeEntity(5, 13 * 60, 13 * 60 + 50),     // 13:00–13:50
                PeriodTimeEntity(6, 14 * 60, 14 * 60 + 50),     // 14:00–14:50
                PeriodTimeEntity(7, 15 * 60 + 10, 16 * 60)      // 15:10–16:00
            )
            dao.insertAll(defaults)
        }
    }
}
