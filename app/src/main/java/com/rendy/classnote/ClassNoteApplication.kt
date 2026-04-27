package com.rendy.classnote

import android.app.Application
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.WeatherPreferences
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.data.repository.ClassRecordRepository
import com.rendy.classnote.data.repository.CourseRepository
import com.rendy.classnote.data.repository.FormulaRepository
import com.rendy.classnote.data.repository.ReminderRepository
import com.rendy.classnote.notification.NotificationHelper

class ClassNoteApplication : Application() {

    val database by lazy { ClassNoteDatabase.getDatabase(this) }

    val courseRepository by lazy {
        CourseRepository(
            courseDao = database.courseDao(),
            overrideDao = database.courseOverrideDao(),
            periodTimeDao = database.periodTimeDao()
        )
    }

    val reminderRepository by lazy {
        ReminderRepository(
            reminderDao = database.reminderDao(),
            notificationDao = database.reminderNotificationDao()
        )
    }

    val formulaRepository by lazy { FormulaRepository(database.formulaDao()) }

    val classRecordRepository by lazy {
        ClassRecordRepository(
            recordDao = database.classRecordDao(),
            mediaDao = database.classRecordMediaDao()
        )
    }

    val appPreferences by lazy { AppPreferences(this) }
    val weatherPreferences by lazy { WeatherPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        ApiLogger.init(database.apiLogDao())
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val bypassDnd = appPreferences.bypassDndEnabled && nm.isNotificationPolicyAccessGranted
        NotificationHelper.createNotificationChannel(this, bypassDnd)
    }
}
