# Majordomo Context (GEMINI.md)

本檔案為專案特定的 AI 上下文指南。當你在本專案進行開發、除錯或重構時，請嚴格遵守以下規範與架構原則。

## 1. 專案使命與背景

Majordomo 是一個基於 Spring Boot 的數位管家 / 管家系統，透過 `gemini` CLI 工具自動化管理與執行位於不同檔案目錄下的任務（Workspaces）。

- **核心目標**：提供 Web 與 Telegram 介面，讓使用者遠端操控 AI Agent。Majordomo 旨在成為協助使用者打理工作環境、適應使用者習慣的貼心夥伴，而不僅僅是冷冰冰的管理工具。
- **參考專案**：`agent` 模組參考了 [zeroClow](https://github.com/chaoshen/zeroClow) 專案設計，實作了具備自主決策能力的代理人。

## 2. 技術棧與環境規範

| 項目 | 規範 |
|------|------|
| Java 版本 | Java 21（必須使用 Virtual Threads） |
| 框架 | Spring Boot 3.5.11、Spring Data JDBC (CrudRepository)、Spring AI 1.1.2 (MCP) |
| 資料庫 | H2 (Local file mode)，所有持久化邏輯透過 Repository 介面 |
| 前端 | Thymeleaf + HTMX + Bootstrap |
| CLI 整合 | 直接呼叫系統層級的 `gemini` 指令 |
| Telegram | Telegram Bot API 9.4.0，Token 以 AES-256/GCM 加密儲存 |

## 3. 模組職責分配 (SOC)

當你需要修改程式碼時，請確保邏輯位於正確的模組：

| 模組 | 職責 | 關鍵類別 |
|------|------|---------|
| `agent` | AI 代理人的核心邏輯：Prompt 組裝、Heartbeat 心跳、初始化設定 | `AgentPromptBuilder`, `HeartBeat`, `Initial`, `TemplateLoader` |
| `chat` | 管理 Agent 對話紀錄的持久化與查詢 | `AgentChatHistory`, `AgentChatService` |
| `config` | 執行緒池配置（Virtual Threads）、MCP 服務 Bean 註冊 | `AsyncThreadPoolsConfigure`, `McpServiceConfigure` |
| `exec` | **僅**負責執行 `gemini` CLI 指令與日誌管理。不得包含持久化邏輯 | `ExecuteService`, `CommandExecutor`, `GeminiCliJsonOutputParser`, `PromptBuilder` |
| `history` | 統一管理執行歷史、結果 (`result_text`) | `HistoryService`, `History`, `ResultText` |
| `mcp` | MCP Tool 實作，暴露 Memory / Workspace / Telegram 功能供外部 AI 呼叫 | `MemoryMcpService`, `WorkspaceMcpService`, `TelegramMcpService` |
| `memory` | 管理 Agent 的持久化關鍵字記憶（store / recall / forget） | `MemoryRepository`, `KeywordExtractor` |
| `schedule` | 管理 TaskScheduler 排程邏輯（Cron + One-time），包含 Heartbeat 排程 | `ScheduleService`, `Schedule` |
| `telegram` | Telegram Bot API 交互：訊息監聽、白名單驗證、設定加密 | `TelegramHandleService`, `TelegramSettingsService` |
| `web` | 負責所有 HTTP 請求、HTMX 片段渲染與 Controller 邏輯 | `WorkspaceWebController`, `AgentChatController`, `AgentWebController` 等 |
| `workspace` | 工作區實體、CRUD、`AgentCommandTask` 非同步任務追蹤 | `WorkspaceService`, `Workspace`, `AgentCommandTask` |

### 模組間依賴原則

- `web` → 呼叫 Service 層，不直接操作 Repository
- `exec` → 依賴 `history` 寫入紀錄，依賴 `workspace` 取得 `AgentCommandTask` 狀態
- `mcp` → 包裝 Service 層方法為 MCP Tool，不包含獨立業務邏輯
- `schedule` → 依賴 `exec` 執行指令，依賴 `agent.HeartBeat` 處理心跳
- `telegram` → 依賴 `exec` 執行 Agent，依賴 `memory` 做 RAG 記憶注入

## 4. 資料庫 Schema 概覽

```
workspace         → 工作區實體（軟刪除: active）
history           → 執行歷史（workspace_id = -1 表示 BasicAgent）
result_text       → 執行輸出（STDOUT/STDERR），外鍵關聯 history
schedule          → 排程任務（CRON_JOB / ONE_TIME）
memory            → Agent 長期記憶（key-category-content）
agent_command_task → Agent 指派的非同步任務追蹤
agent_chat_history → Agent 對話紀錄（含 role 與 source）
```

## 5. AI 開發規範 (Critical Guidelines)

### Reviewable Code 標準
- 每個 Java 檔案頂部必須包含 **File Header**（目的 + 關鍵項目 + 模組）
- 每個公開方法與重要 private 方法必須包含 **Function Contract**（目的 / 輸入 / 輸出 / 限制 / 副作用）
- 檔案末尾必須包含 **Review Checklist**（`/* ### Review Checklist ### */`）
- 關鍵決策必須標註 `// WHY:` 或 `// REASONING:`
- 邊界條件必須標註 `// EDGE_CASE:`
- 使用 Slf4j 記錄關鍵日誌

### 測試規範 (TDD)
- 修改業務邏輯前，必須先在 `src/test/java` 建立或更新測試
- 測試指令：`mvn test`

### 非同步處理
- 所有耗時操作（CLI 執行、日誌刷寫）必須使用虛擬執行緒
- 執行緒池定義在 `AsyncThreadPoolsConfigure`：
  - `taskScheduler`：供排程任務使用
  - `streamReaderExecutor`：供 CLI stdout/stderr 串流讀取
  - `workspaceCommandExecutor`：供工作區指令執行

### 事務管理
- 所有涉及跨表寫入的 Service 方法必須標註 `@Transactional`

### 語言偏好
- 回應使用者時，請優先使用**正體中文**
- 程式碼註解使用正體中文或英文均可，但 Comment Tag（`WHY:` 等）使用英文

## 6. 常用開發指令

```bash
# 執行測試
mvn test

# 編譯打包
mvn clean package

# 啟動開發伺服器（Port: 8088）
mvn spring-boot:run

```

---
*本檔案旨在幫助 AI 代理人（如你）更好地理解專案脈絡，確保程式碼產出符合專案的一致性與品質標準。*
