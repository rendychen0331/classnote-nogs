package com.rendy.classnote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class OverviewRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return try {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            OverviewRemoteViewsFactory(applicationContext, widgetId)
        } catch (_: Exception) {
            // Factory 建立失敗時回傳空 Factory，避免系統顯示「載入小工具時發生問題」
            EmptyRemoteViewsFactory()
        }
    }

    private class EmptyRemoteViewsFactory : RemoteViewsFactory {
        override fun onCreate() {}
        override fun onDataSetChanged() {}
        override fun onDestroy() {}
        override fun getCount() = 0
        override fun getViewAt(position: Int): RemoteViews? = null
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
