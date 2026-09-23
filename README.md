# Majordomo 🧹 — 你的數位管家 / 管家系統

> 基於 Spring Boot 的 AI 工作區管理系統，透過 Google Antigravity CLI (`agy`) 自動化執行任務，並以 Web UI 與 Telegram Bot 提供遠端操控介面。

Majordomo 不只是工具——她是你的數位管家。她學習你的偏好、記住你的指示、主動巡視工作環境，並透過 Web 或 Telegram 隨時回應你的指令。

## ✨ 核心功能

### 🤖 BasicAgent（Majordomo 核心）
- 參考 [zeroclaw](https://github.com/zeroclaw-labs/zeroclaw) 設計的自主決策 AI Agent
- **心跳機制 (Heartbeat)**：定期巡視未讀任務與排程歷史，主動回報結果
- **長期記憶 (Memory)**：基於關鍵字的持久化記憶系統，學習使用者偏好
- **對話介面**：透過 Web Chat 或 Telegram 與使用者自然對話

### 📁 工作區管理 (Workspace)
- 新增、編輯、軟刪除工作區目錄
- 每個工作區獨立管理排程與執行歷史
- 支援在指定工作目錄下呼叫 Antigravity CLI (`agy`) 執行任務

### 📊 執行與歷史 (Execution & History)
- 同步/非同步執行 `agy` 指令，並保留 JSON 結構化輸出
- 完整記錄每次執行的指令、時間、狀態與輸出（stdout/stderr）
- 支援 JSON 結構化解析 CLI 回應

### ⏰ 任務排程 (Schedule)
- **CRON_JOB**：週期性排程（使用 Cron 表達式）
- **ONE_TIME**：一次性定時排程
- 動態註冊 / 取消排程，無需重啟

### 🔗 MCP 整合 (Model Context Protocol)
Majordomo 內建 MCP Server，提供以下 Tool 供外部 AI 模型呼叫：
- **Memory Tools**：`store` / `recall` / `forget` — 管理 Agent 長期記憶
- **Workspace Tools**：`listWorkspaces` / `viewHistory` / `executeCommand` / `scheduleTask` 等 — 管理工作區
- **Telegram Tool**：`sendMessageToUser` — 向使用者發送訊息

### 📱 多端介面
- **Web UI**：Thymeleaf + HTMX + Bootstrap 構建的現代化響應式介面
- **Telegram Bot**：整合 Telegram Bot API，支援行動端對話與指令控制
- **Telegram 安全**：Bot Token 使用 AES-256/GCM 加密儲存，僅白名單使用者可操作

## 🛠 技術棧

| 項目 | 版本 / 技術 |
|------|-----------|
| 語言 | Java 21（全系統 Virtual Threads） |
| 框架 | Spring Boot 3.5.11 |
| AI 整合 | Spring AI 1.1.2（MCP Server） |
| 持久化 | H2 Database (Local file)、Spring Data JDBC |
| 前端 | Thymeleaf + HTMX + Bootstrap |
| 排程 | Spring TaskScheduler（Virtual Threads） |
| 機器人 | Telegram Bot API 9.4.0 |
| CLI 工具 | Google Antigravity CLI (`agy`，系統層級呼叫) |

## 🚀 快速開始

### 前置需求

1. **Java 21+**
2. **Antigravity CLI**：必須在系統 PATH 中可執行 `agy` 指令。應用程式啟動時會自動檢查。
3. **Maven 3.8+**

### 啟動應用程式

```bash
# 編譯與打包
mvn clean package

# 啟動（預設 Port: 8088）
mvn spring-boot:run
```

啟動後開啟瀏覽器訪問 `http://localhost:8088`，首次使用將引導你完成 BasicAgent 初始化設定。

### 設定 Telegram Bot（選填）

1. 在 Web UI 側邊欄點擊 **Settings**
2. 輸入 Telegram Bot Token 與你的 Telegram User ID
3. 儲存後 Bot 會自動連線，即刻可用

## 📂 資料儲存

所有資料存放於 `~/.majordomo/`：
- `data/database` — H2 資料庫檔案
- `telegram.properties` — 加密的 Telegram 設定
- BasicAgent 的設定、日誌與 Markdown 檔案

## 📐 專案結構

```
src/main/java/com/github/nicholas23/majordomo/
├── agent/        # BasicAgent 核心（Prompt、Heartbeat、初始化）
├── chat/         # Agent 對話紀錄持久化
├── config/       # 執行緒池、MCP 服務註冊
├── exec/         # Antigravity CLI 執行引擎
├── history/      # 執行歷史與結果管理
├── mcp/          # MCP Tool 實作（Memory、Workspace、Telegram）
├── memory/       # Agent 長期記憶系統
├── schedule/     # 任務排程（Cron / One-time）
├── telegram/     # Telegram Bot 訊息處理與設定加密
├── web/          # Web 控制器（HTMX 片段渲染）
└── workspace/    # 工作區實體與業務邏輯
```

## 📄 授權協定

本專案採用 [MIT License](LICENSE) 授權。
