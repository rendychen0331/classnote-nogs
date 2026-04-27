package com.rendy.classnote.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiApi {

    private const val TAG = "GeminiApi"
    private const val NOTIFICATION_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"
    private const val SUMMARY_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"

    private const val SYSTEM_INSTRUCTION = """你是一個提醒事項擷取助理，專門從手機通知中提取需要記錄的待辦事項或提醒。

嚴格規則：
1. 只能使用通知文字中明確出現的資訊，絕對不能猜測、推測或補充通知裡沒有的內容。
2. 截止日期（dueDate）和時間（dueTime）：通知中有提到日期或時間時必須填入（包含「明天」「下週」等相對日期需換算為絕對日期），沒有提到才填 null。
3. title 必須使用通知中的事件名稱，不得自行創造或修改。
4. 沒有明確的待辦、截止、提醒等時間性意圖時，回傳 isEvent:false（例如純廣告、新聞、聊天訊息）。
5. 只回傳純 JSON，不含任何解釋、標記符號或多餘文字。"""

    data class NotificationInput(
        val appLabel: String,
        val title: String,
        val text: String
    )

    data class EventInfo(
        val title: String,
        val dueDate: String?,   // "YYYY-MM-DD" or null
        val dueTime: String?,   // "HH:MM" 24小時制 or null
        val category: String,   // "HOMEWORK" | "EXAM" | "PAYMENT" | "EVENT" | "REMINDER"
        val note: String        // Gemini 萃取的補充說明
    )

    /**
     * 批次分析多則通知（省 token：system instruction 只送一次）。
     * 回傳清單長度與 inputs 相同，null 表示該則不是學校事件。
     */
    suspend fun analyzeNotifications(
        apiKey: String,
        inputs: List<NotificationInput>
    ): List<EventInfo?> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || inputs.isEmpty()) return@withContext emptyList()
        try {
            val prompt = buildBatchPrompt(inputs)
            val responseJson = callGemini(apiKey, prompt, NOTIFICATION_ENDPOINT) ?: return@withContext List(inputs.size) { null }
            parseBatchResponse(responseJson, inputs.size)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeNotifications failed", e)
            List(inputs.size) { null }
        }
    }

    private fun buildBatchPrompt(inputs: List<NotificationInput>): String {
        val today = java.time.LocalDate.now().toString()  // YYYY-MM-DD
        val notifList = inputs.mapIndexed { i, n ->
            "通知 ${i + 1}｜來源：${n.appLabel}｜標題：${n.title}｜內容：${n.text}"
        }.joinToString("\n")

        return """
今天日期：$today

以下是 ${inputs.size} 則手機通知，請逐一判斷是否為需要記錄的提醒事項（作業截止、考試、繳費期限、活動、吃藥、約會、任何有時間性的待辦等）。

$notifList

---
回傳一個 JSON 陣列，長度必須等於通知數量（${inputs.size} 個元素），每個元素對應一則通知：

若是提醒事項：{"isEvent":true,"title":"事件名稱","dueDate":"YYYY-MM-DD 或 null","dueTime":"HH:MM 或 null","category":"類別","note":"補充說明，最多 80 字"}
若不是：{"isEvent":false}

category 值：HOMEWORK（作業）、EXAM（考試）、PAYMENT（繳費）、EVENT（活動）、REMINDER（其他）

示例（2 則通知）：
[
  {"isEvent":true,"title":"第三章習題","dueDate":"2026-05-10","dueTime":"23:59","category":"HOMEWORK","note":"請完成所有題目"},
  {"isEvent":false}
]

只回傳 JSON 陣列，不含任何其他文字。
        """.trimIndent()
    }

    private fun callGemini(apiKey: String, prompt: String, endpoint: String = SUMMARY_ENDPOINT): String? {
        val url = URL("$endpoint?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.0)
                put("maxOutputTokens", 800)
                put("responseMimeType", "application/json")
            })
        }.toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

        val code = conn.responseCode
        if (code != 200) {
            Log.e(TAG, "Gemini API HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
            return null
        }

        return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    /**
     * 將音訊檔案 base64 inline 傳給 Gemini，回傳課堂重點摘要文字，失敗回傳 null。
     */
    suspend fun summarizeAudio(apiKey: String, audioPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(audioPath)
            val audioBytes = file.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val url = URL("$SUMMARY_ENDPOINT?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/mp4")
                                    put("data", base64Audio)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", "這是一段上課錄音，請用繁體中文整理出課堂重點筆記，以條列式呈現，每點不超過 50 字。若音訊無法辨識或內容不清楚，請直接說明。")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 1000)
                })
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code != 200) {
                Log.e(TAG, "summarizeAudio HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "summarizeAudio failed", e)
            null
        }
    }

    /**
     * 將多則筆記內容整理成一份課堂重點總結，回傳繁體中文條列摘要，失敗回傳 null。
     */
    suspend fun summarizeSession(apiKey: String, combinedContent: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUMMARY_ENDPOINT?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "以下是一堂課的所有筆記內容，請用繁體中文整理成課堂重點總結，以條列式呈現，每點不超過 60 字：\n\n$combinedContent")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 800)
                })
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code != 200) {
                Log.e(TAG, "summarizeSession HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
                return@withContext null
            }

            JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "summarizeSession failed", e)
            null
        }
    }

    private fun parseBatchResponse(json: String, expectedCount: Int): List<EventInfo?> {
        return try {
            val text = JSONObject(json)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            val arr = JSONArray(text)
            (0 until expectedCount).map { i ->
                if (i >= arr.length()) return@map null
                val obj = arr.getJSONObject(i)
                if (!obj.optBoolean("isEvent", false)) return@map null

                val title = obj.optString("title", "").trim()
                if (title.isBlank()) return@map null

                EventInfo(
                    title = title,
                    dueDate = obj.optString("dueDate").takeIf { it != "null" && it.isNotBlank() },
                    dueTime = obj.optString("dueTime").takeIf { it != "null" && it.isNotBlank() },
                    category = obj.optString("category", "REMINDER").let { cat ->
                        if (cat in setOf("HOMEWORK", "EXAM", "PAYMENT", "EVENT", "REMINDER")) cat else "REMINDER"
                    },
                    note = obj.optString("note", "").trim()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseBatchResponse failed: $json", e)
            List(expectedCount) { null }
        }
    }
}
