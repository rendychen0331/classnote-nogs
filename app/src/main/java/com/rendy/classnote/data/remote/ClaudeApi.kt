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

object ClaudeApi {

    private const val TAG = "ClaudeApi"
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-4-5-20251001"
    private const val API_VERSION = "2023-06-01"

    suspend fun analyzeNotifications(
        apiKey: String,
        inputs: List<GeminiApi.NotificationInput>
    ): List<GeminiApi.EventInfo?> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || inputs.isEmpty()) return@withContext emptyList()
        val prompt = GeminiApi.buildBatchPrompt(inputs)
        var responseText: String? = null
        var success = false
        val duration = measureTimeMillis {
            try {
                val url = URL(ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", API_VERSION)
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000

                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 1000)
                    put("system", GeminiApi.SYSTEM_INSTRUCTION)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText()
                    Log.e(TAG, "analyzeNotifications HTTP $code: $err")
                    return@measureTimeMillis
                }
                responseText = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    .getJSONArray("content").getJSONObject(0).getString("text").trim()
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "analyzeNotifications failed", e)
            }
        }
        ApiLogger.log("claude(通知辨識)", prompt.take(300), responseText?.take(300), duration, success)
        if (!success || responseText == null) return@withContext List(inputs.size) { null }
        GeminiApi.parseNotificationJsonText(responseText!!, inputs.size)
    }

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
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", API_VERSION)
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000

                val messages = JSONArray()
                history.forEach { (text, isUser) ->
                    messages.put(JSONObject().apply {
                        put("role", if (isUser) "user" else "assistant")
                        put("content", text)
                    })
                }
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })

                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 1000)
                    put("system", "你是一個課堂筆記助理，以繁體中文回答問題。以下是本堂課的課堂筆記總結，作為對話背景參考：\n\n$noteContext")
                    put("messages", messages)
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText()
                    Log.e(TAG, "chatWithContext HTTP $code: $err")
                    ApiLogger.log("claude(筆記對話)", userMessage.take(100), "HTTP $code: $err", 0, false)
                    return@measureTimeMillis
                }

                result = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            } catch (e: Exception) {
                Log.e(TAG, "chatWithContext failed", e)
            }
        }
        ApiLogger.log("claude(筆記對話)", userMessage.take(100), result?.take(200), duration, result != null)
        result
    }
}
