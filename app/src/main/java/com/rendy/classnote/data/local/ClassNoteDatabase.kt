package com.rendy.classnote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        ReminderNotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ClassNoteDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun courseOverrideDao(): CourseOverrideDao
    abstract fun periodTimeDao(): PeriodTimeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderNotificationDao(): ReminderNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: ClassNoteDatabase? = null

        fun getDatabase(context: Context): ClassNoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClassNoteDatabase::class.java,
                    "classnote_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
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
