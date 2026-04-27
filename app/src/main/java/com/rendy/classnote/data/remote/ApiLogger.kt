package com.rendy.classnote.data.remote

import com.rendy.classnote.data.local.dao.ApiLogDao
import com.rendy.classnote.data.local.entity.ApiLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ApiLogger {

    private var dao: ApiLogDao? = null

    fun init(apiLogDao: ApiLogDao) {
        dao = apiLogDao
    }

    fun log(model: String, request: String, response: String?, durationMs: Long, isSuccess: Boolean) {
        val d = dao ?: return
        CoroutineScope(Dispatchers.IO).launch {
            d.insert(
                ApiLogEntity(
                    model = model,
                    requestPreview = request.take(300),
                    responsePreview = response?.take(300) ?: "",
                    durationMs = durationMs,
                    isSuccess = isSuccess
                )
            )
            d.pruneOldLogs()
        }
    }
}
