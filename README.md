# ClassNote

大學生專用的課堂筆記與提醒管理 Android App。

## 功能

- **課表管理** — 週課表檢視、學期切換、臨時換課 / 取消課程
- **提醒事項** — 新增截止日期、多組通知時間、重複提醒（每天 / 每週 / 每月）
- **公式本** — 數學公式儲存，支援 MathQuill 視覺化輸入與 KaTeX 離線渲染
- **天氣** — 中央氣象署 36 小時預報，22 縣市查詢
- **上課紀錄** — 拍照、錄音、文字筆記，支援 Gemini AI 摘要
- **Gmail / Google Classroom 同步** — 自動抓取作業截止日，WorkManager 定時背景執行
- **Google Drive 備份** — 資料庫備份與還原，支援 WiFi / 行動數據 / 任何網路選擇
- **桌面小工具** — 月曆小工具顯示近三週提醒事項

## 技術棧

- Kotlin + Android Jetpack（Room、Navigation、WorkManager、ViewModel）
- Material Design 3
- Google Sign-In / Drive API / Gmail API / Classroom API
- Gemini API（AI 音訊摘要）
- CWA 中央氣象署開放資料 API

## 建置

1. 在 `local.properties` 填入 SDK 路徑
2. 在設定頁填入 Gemini API Key 與 CWA API Key
3. `./gradlew assembleDebug`

## 授權

MIT
