package com.rendy.classnote.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * @param displayName 顯示給使用者的名稱，例如「臺北市 中正區」或「臺北市」
 * @param apiName     實際地名，例如「中正區」或「臺北市」
 * @param datasetId   CWA 資料集 ID
 * @param countyName  所屬縣市（鄉鎮層級用，供解析時避免同名衝突）
 */
data class WeatherLocation(
    val displayName: String,
    val apiName: String,
    val datasetId: String,
    val countyName: String = ""
)

data class ForecastItem(
    val timePeriod: String,
    val description: String,
    val tempMin: Int,
    val tempMax: Int,
    val rainProb: Int
)

object WeatherApi {

    private const val API_KEY = "CWA_API_KEY_REMOVED"
    private const val BASE_URL = "https://opendata.cwa.gov.tw/api/v1/rest/datastore"

    // ── 縣市對應 dataset ID（逐12小時，7天）────────────────────────────────────
    // 實測確認的正確對應（每縣市有兩個 dataset，取後者為7天12小時版本）
    private val COUNTY_DATASET = mapOf(
        "宜蘭縣" to "F-D0047-003",
        "桃園市" to "F-D0047-007",
        "新竹縣" to "F-D0047-011",
        "苗栗縣" to "F-D0047-015",
        "彰化縣" to "F-D0047-019",
        "南投縣" to "F-D0047-023",
        "雲林縣" to "F-D0047-027",
        "嘉義縣" to "F-D0047-031",
        "屏東縣" to "F-D0047-035",
        "臺東縣" to "F-D0047-039",
        "花蓮縣" to "F-D0047-043",
        "澎湖縣" to "F-D0047-047",
        "基隆市" to "F-D0047-051",
        "新竹市" to "F-D0047-055",
        "嘉義市" to "F-D0047-059",
        "臺北市" to "F-D0047-063",
        "高雄市" to "F-D0047-067",
        "新北市" to "F-D0047-071",
        "臺中市" to "F-D0047-075",
        "臺南市" to "F-D0047-079",
        "連江縣" to "F-D0047-083",
        "金門縣" to "F-D0047-087"
    )

    // ── 縣市層級（F-C0032-001，36小時預報）─────────────────────────────────────
    private val COUNTY_LOCATIONS = listOf(
        "臺北市", "新北市", "桃園市", "臺中市", "臺南市", "高雄市",
        "基隆市", "新竹市", "新竹縣", "苗栗縣", "彰化縣", "南投縣",
        "雲林縣", "嘉義市", "嘉義縣", "屏東縣", "宜蘭縣", "花蓮縣",
        "臺東縣", "澎湖縣", "金門縣", "連江縣"
    ).map { WeatherLocation(it, it, "F-C0032-001") }

    // ── 鄉鎮區層級（逐12小時，各縣市獨立 dataset）──────────────────────────────
    private fun districts(county: String, vararg names: String): List<WeatherLocation> {
        val datasetId = COUNTY_DATASET[county] ?: "F-D0047-091"
        return names.map { WeatherLocation("$county $it", it, datasetId, county) }
    }

    private val DISTRICT_LOCATIONS: List<WeatherLocation> = buildList {
        // 臺北市 12 區
        addAll(districts("臺北市",
            "中正區","大同區","中山區","松山區","大安區","萬華區",
            "信義區","士林區","北投區","內湖區","南港區","文山區"))

        // 新北市 29 區
        addAll(districts("新北市",
            "板橋區","三重區","中和區","永和區","新莊區","新店區",
            "樹林區","鶯歌區","三峽區","淡水區","汐止區","瑞芳區",
            "土城區","蘆洲區","五股區","泰山區","林口區","深坑區",
            "石碇區","坪林區","三芝區","石門區","八里區","平溪區",
            "雙溪區","貢寮區","金山區","萬里區","烏來區"))

        // 桃園市 13 區
        addAll(districts("桃園市",
            "桃園區","中壢區","大溪區","楊梅區","蘆竹區","大園區",
            "龜山區","八德區","龍潭區","平鎮區","新屋區","觀音區","復興區"))

        // 臺中市 29 區
        addAll(districts("臺中市",
            "中區","東區","南區","西區","北區","西屯區","南屯區","北屯區",
            "豐原區","后里區","石岡區","東勢區","和平區","新社區","神岡區",
            "潭子區","大雅區","烏日區","大肚區","龍井區","沙鹿區","梧棲區",
            "清水區","大甲區","外埔區","大安區","大里區","太平區","霧峰區"))

        // 臺南市 37 區
        addAll(districts("臺南市",
            "中西區","東區","南區","北區","安平區","安南區","永康區",
            "歸仁區","新化區","左鎮區","玉井區","楠西區","南化區","仁德區",
            "關廟區","龍崎區","官田區","麻豆區","佳里區","西港區","七股區",
            "將軍區","學甲區","北門區","新營區","後壁區","白河區","東山區",
            "六甲區","下營區","柳營區","鹽水區","善化區","大內區","山上區",
            "新市區","安定區"))

        // 高雄市 38 區
        addAll(districts("高雄市",
            "楠梓區","左營區","鼓山區","三民區","鹽埕區","新興區","前金區","苓雅區",
            "前鎮區","旗津區","小港區","鳳山區","林園區","大寮區","大樹區",
            "大社區","仁武區","鳥松區","岡山區","橋頭區","燕巢區","田寮區",
            "阿蓮區","路竹區","湖內區","茄萣區","永安區","彌陀區","梓官區",
            "旗山區","美濃區","六龜區","甲仙區","杉林區","內門區","茂林區",
            "桃源區","那瑪夏區"))

        // 基隆市 7 區
        addAll(districts("基隆市",
            "仁愛區","信義區","中正區","中山區","安樂區","暖暖區","七堵區"))

        // 新竹市 3 區
        addAll(districts("新竹市", "東區","北區","香山區"))
    }

    /** 全部地點（縣市 + 鄉鎮區），供搜尋用 */
    val LOCATIONS: List<WeatherLocation> = COUNTY_LOCATIONS + DISTRICT_LOCATIONS

    /** 所有縣市名稱（供第一層選擇） */
    val COUNTY_NAMES: List<String> = COUNTY_LOCATIONS.map { it.displayName }

    /** 取得某縣市下的所有鄉鎮區 */
    fun districtsOf(county: String): List<WeatherLocation> =
        DISTRICT_LOCATIONS.filter { it.countyName == county }

    // ── 查詢天氣 ──────────────────────────────────────────────────────────────

    suspend fun fetchForecast(displayName: String): Result<List<ForecastItem>> {
        val location = LOCATIONS.find { it.displayName == displayName }
            ?: return Result.failure(Exception("找不到「$displayName」的天氣資料"))
        return fetchForecastForLocation(location)
    }

    private suspend fun fetchForecastForLocation(location: WeatherLocation): Result<List<ForecastItem>> =
        withContext(Dispatchers.IO) {
            try {
                val elements = if (location.datasetId == "F-C0032-001")
                    "Wx,PoP,MinT,MaxT,CI"
                else
                    "天氣現象,最高溫度,最低溫度,12小時降雨機率"
                val url = URL(
                    "$BASE_URL/${location.datasetId}" +
                        "?Authorization=$API_KEY" +
                        "&elementName=${java.net.URLEncoder.encode(elements, "UTF-8")}"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                val code = conn.responseCode
                if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val forecasts = if (location.datasetId == "F-C0032-001") {
                    parseCountyForecast(json, location.apiName)
                } else {
                    parseDistrictForecast(json, location.apiName)
                }
                Result.success(forecasts)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── 縣市解析（F-C0032-001） ────────────────────────────────────────────────

    private fun parseCountyForecast(json: String, locationName: String): List<ForecastItem> {
        val root = JSONObject(json)
        val records = root.getJSONObject("records")
        val locationsArr = records.getJSONArray("location")

        var targetLocation: JSONObject? = null
        for (i in 0 until locationsArr.length()) {
            val loc = locationsArr.getJSONObject(i)
            if (loc.getString("locationName") == locationName) {
                targetLocation = loc
                break
            }
        }
        targetLocation ?: return emptyList()

        val weatherElements = targetLocation.getJSONArray("weatherElement")
        val wxMap = mutableMapOf<String, String>()
        val popMap = mutableMapOf<String, Int>()
        val minTMap = mutableMapOf<String, Int>()
        val maxTMap = mutableMapOf<String, Int>()

        for (i in 0 until weatherElements.length()) {
            val elem = weatherElements.getJSONObject(i)
            val name = elem.getString("elementName")
            val times = elem.getJSONArray("time")
            for (j in 0 until times.length()) {
                val t = times.getJSONObject(j)
                val startTime = t.getString("startTime")
                val param = t.getJSONObject("parameter")
                when (name) {
                    "Wx" -> wxMap[startTime] = param.getString("parameterName")
                    "PoP" -> popMap[startTime] = param.getString("parameterName").toIntOrNull() ?: 0
                    "MinT" -> minTMap[startTime] = param.getString("parameterName").toIntOrNull() ?: 0
                    "MaxT" -> maxTMap[startTime] = param.getString("parameterName").toIntOrNull() ?: 0
                }
            }
        }

        return wxMap.keys.sorted().map { startTime ->
            ForecastItem(
                timePeriod = formatTimePeriod(startTime),
                description = wxMap[startTime] ?: "",
                tempMin = minTMap[startTime] ?: 0,
                tempMax = maxTMap[startTime] ?: 0,
                rainProb = popMap[startTime] ?: 0
            )
        }
    }

    // ── 鄉鎮區解析（F-D0047-XXX，PascalCase keys，中文 element 名）─────────────

    private fun parseDistrictForecast(json: String, apiName: String): List<ForecastItem> {
        val root = JSONObject(json)
        val records = root.getJSONObject("records")
        val locationsArr = records.getJSONArray("Locations")

        var targetLocation: JSONObject? = null
        outer@ for (i in 0 until locationsArr.length()) {
            val locGroup = locationsArr.getJSONObject(i)
            val locationArr = locGroup.optJSONArray("Location") ?: continue
            for (j in 0 until locationArr.length()) {
                val loc = locationArr.getJSONObject(j)
                if (loc.optString("LocationName") == apiName) {
                    targetLocation = loc
                    break@outer
                }
            }
        }
        targetLocation ?: return emptyList()

        val weatherElements = targetLocation.getJSONArray("WeatherElement")
        val wxMap = mutableMapOf<String, String>()
        val popMap = mutableMapOf<String, Int>()
        val minTMap = mutableMapOf<String, Int>()
        val maxTMap = mutableMapOf<String, Int>()

        for (i in 0 until weatherElements.length()) {
            val elem = weatherElements.getJSONObject(i)
            val name = elem.getString("ElementName")
            val times = elem.getJSONArray("Time")
            for (j in 0 until times.length()) {
                val t = times.getJSONObject(j)
                val startTime = t.getString("StartTime")
                val values = t.getJSONArray("ElementValue")
                val valueObj = values.getJSONObject(0)
                when (name) {
                    "天氣現象" -> wxMap[startTime] = valueObj.optString("Weather", "")
                    "最高溫度" -> maxTMap[startTime] = valueObj.optString("MaxTemperature", "0").toIntOrNull() ?: 0
                    "最低溫度" -> minTMap[startTime] = valueObj.optString("MinTemperature", "0").toIntOrNull() ?: 0
                    "12小時降雨機率" -> popMap[startTime] = valueObj.optString("ProbabilityOfPrecipitation", "0").toIntOrNull() ?: 0
                }
            }
        }

        // 以最高溫做主 key（12h 預報最高/最低溫時段跟降雨機率時段可能錯位，用最近的 pop 填入）
        val sortedKeys = maxTMap.keys.sorted()
        val popKeys = popMap.keys.sorted()
        return sortedKeys.map { startTime ->
            val nearestPop = popKeys.minByOrNull { kotlin.math.abs(it.compareTo(startTime)) }
            ForecastItem(
                timePeriod = formatTimePeriod(startTime),
                description = wxMap[startTime] ?: wxMap[wxMap.keys.sorted().firstOrNull { it >= startTime } ?: ""] ?: "",
                tempMin = minTMap[startTime] ?: 0,
                tempMax = maxTMap[startTime] ?: 0,
                rainProb = popMap[startTime] ?: popMap[nearestPop] ?: 0
            )
        }
    }

    // ── 時間格式化 ────────────────────────────────────────────────────────────

    private fun formatTimePeriod(startTime: String): String {
        return try {
            // 格式：2026-04-24T12:00:00+08:00 或 2026-04-24 12:00:00
            val cleaned = startTime.replace("T", " ").take(16)
            val parts = cleaned.split(" ")
            val datePart = parts[0].substring(5) // MM-dd
            val hour = parts[1].substring(0, 2).toInt()
            val period = when {
                hour in 6..11 -> "早上"
                hour in 12..17 -> "下午"
                else -> "晚上"
            }
            "$datePart $period"
        } catch (e: Exception) {
            startTime
        }
    }
}
