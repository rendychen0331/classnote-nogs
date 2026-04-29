package com.rendy.classnote.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

object MimoApi {

    private const val TAG = "MimoApi"
    private const val ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
    // 如模型名稱有誤，請依平台文件更新
    private const val MODEL = "mimo-v2.5"

    /**
     * 以課堂筆記摘要為背景，進行多輪問答對話。
     * history: 依時序排列的 (text, isUser) pair，不含最新 userMessage。
     */
    suspend fun chatWithContext(
        apiKey: String,
        noteContext: String,
        history: List<Pair<String, Boolean>>,
        userMessage: String
    ): String? = withContext(Dispatchers.IO) {
        var result: String? = null
        val duration = measureTimeMillis {
            try {
                val url = URL(ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000

                val messages = JSONArray()

                // System message with note context
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是一個課堂筆記助理，以繁體中文回答問題。以下是本堂課的課堂筆記總結，作為對話背景參考：\n\n$noteContext")
                })

                // Conversation history
                history.forEach { (text, isUser) ->
                    messages.put(JSONObject().apply {
                        put("role", if (isUser) "user" else "assistant")
                        put("content", text)
                    })
                }

                // Current user message
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })

                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", messages)
                    put("temperature", 0.5)
                    put("max_tokens", 1000)
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText()
                    Log.e(TAG, "chatWithContext HTTP $code: $err")
                    ApiLogger.log("mimo(筆記對話)", userMessage.take(100), "HTTP $code: $err", 0, false)
                    return@measureTimeMillis
                }

                result = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } catch (e: Exception) {
                Log.e(TAG, "chatWithContext failed", e)
            }
        }
        ApiLogger.log("mimo(筆記對話)", userMessage.take(100), result?.take(200), duration, result != null)
        result
    }
}
