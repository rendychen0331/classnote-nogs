# ClassNote

歡迎

## 說明
這是一個課堂筆記與提醒管理App

目前使用前置較為繁瑣，Google 服務需要手動放入google-service.json再build

僅使用Xiaomi HyperOS 測試（沒其他手機）

天氣僅支援CWA API

Made with Claude(程式碼) & Gemini(App icon) & Google Stitch(UI)

## 正在實作
Microsoft Acount & Microsoft Onedrive 支援

## AI功能支援模型(API key)
- Xiaomi MiMo
- Google Gemini
- Anthropic Claude
- OpenAI ChatGPT

## 功能

- **課表管理** — 週課表檢視、學期切換
- **提醒事項** — 新增截止日期、多組通知時間、重複提醒（每天 / 每週 / 每月）
- **AI 通知辨識** — AI 自動辨識通知並自動加入提醒事項
- **公式本** — 數學公式儲存，支援 MathQuill 視覺化輸入與 KaTeX 離線渲染
- **天氣** — CWA 36 小時預報，22 縣市查詢
- **上課紀錄** — 拍照、錄音、文字筆記，支援 AI 摘要
- **Gmail / Google Classroom 同步** — 自動抓取作業截止日，WorkManager 定時背景執行
- **Google Drive 備份** — 資料庫備份與還原，支援 WiFi / 行動數據 / 任何網路選擇
- **桌面小工具** — 月曆小工具顯示近三週提醒事項

## 技術棧

- Kotlin + Android Jetpack（Room、Navigation、WorkManager、ViewModel）
- Material Design 3
- Google Sign-In / Drive API / Gmail API / Classroom API
- Gemini API
- MiMo API (OpenAI API)
- Anthropic API
- OpenAI API
- CWA 開放資料 API

## 建置

1. 前往https://console.cloud.google.com建立專案
2. 設定OAuth同意畫面
3. 建立OAuth2.0憑證
4. 啟用Google Drive、Google Classroom、Gmail API
5. 前往https://console.firebase.google.com建立專案
6. 選擇Google Cloud專案匯入
7. 下載google-service.json放入"app/"目錄
8. build apk
9. 前往CWA開放資料平台官網申請API key

## 預計開發功能

- [x] 其他類型筆記的支援
- [ ] 優化App清單讀取時間
- [ ] 加入 Gmail 非 Google Classroom 郵件的AI辨識支援
- [ ] 加入收到 Gmail 或 Google Classroom 通知後自動抓取詳細信息
- [x] 加入 Google Tasks 支援
- [ ] 加入 Google keep 支援
- [x] 加入 Google 日曆 支援
- [x] 加入 Microsoft Onedrive 備份支援
- [ ] 加入 Microsoft OneNote 支援
- [ ] 加入 Microsoft To Do 支援
- [ ] 加入 Microsoft Outlook 支援
- [ ] 加入 Microsoft Teams 支援
- [ ] ~~加入 Xiaomi Account 支援~~(無小米開發者帳號，暫時擱置)
- [ ] ~~加入 Xiaomi 日曆 支援~~(無小米開發者帳號，暫時擱置)
- [x] 加入 Microsoft Account 支援
- [ ] 加入其他天氣 API 支援
- [ ] 新增多 Google Account 支援
## 授權

MIT
