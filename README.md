# ClassNote

學生專用的課堂筆記與提醒管理App。

Made with Claude(程式碼) & Gemini(App icon + App內建AI功能) & Google Stitch(UI)

## 功能

- **課表管理** — 週課表檢視、學期切換
- **提醒事項** — 新增截止日期、多組通知時間、重複提醒（每天 / 每週 / 每月）
- **AI 通知辨識** — Gemini 自動辨識通知並自動加入提醒事項
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
- Gemini API（AI 通知辨識 & AI 音訊摘要）
- CWA 中央氣象署開放資料 API

## 建置

1. 前往https://console.cloud.google.com建立專案
2. 設定OAuth同意畫面
3. 建立OAuth2.0憑證
4. 啟用Google Drive、Google Classroom、Gmail API
5. 前往https://console.firebase.google.com建立專案
6. 選擇Google Cloud專案匯入
7. 下載google-service.json放入"app/"目錄
8. build apk

## 預計開發功能

- 其他類型筆記的支援
- 優化App清單讀取時間
- 加入 Gmail 非 Google Classroom 郵件的AI辨識支援
- 加入收到 Gmail 或 Google Classroom 通知後自動抓取詳細信息
- 加入 Google Tasks 支援
- 加入 Google keep 支援
- 加入 Google 日曆 支援
- 加入 Microsoft Onedrive 備份支援
- 加入 Microsoft OneNote 支援
- 加入 Microsoft To Do 支援
- 加入 Microsoft Outlook 支援
- 加入 Microsoft Teams支援

## 授權

MIT
