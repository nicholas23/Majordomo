# Majordomo 專案代碼審查與重構改進清單 (improvement.md)

> **審查日期**：2026-09-06  
> **審查角色**：Tech Lead  
> **審查目標**：針對目前 Majordomo 架構、安全性、並發控制、SOC（職責分離）、測試覆蓋率與使用者體驗進行全面診斷，並確立翻修（Renovation）路線圖。

---

## 🏗️ 翻修總結 (Executive Summary)

這棟老宅的「鋼骨架構」（Spring Boot 3.5 + Java 21 Virtual Threads + Spring AI MCP）非常現代且具備強大的擴展性，但目前隱藏著幾處嚴重的**管線破漏與結構性技術債**：
1. **地基隔離不良**：測試未與正式環境隔離，執行測試會直接動到正式環境的 H2 資料庫與使用者個人檔案。
2. **功能線路斷線**：`AgentTodoMcpService` 已經實作，卻完全沒有在 MCP 配置中註冊，導致 Agent 自主排程待辦功能完全癱瘓。
3. **無狀態待辦與重啟遺失**：`agent_todo` 沒有狀態欄位，若系統重啟或非執行時段，待辦將永久沉睡。
4. **並發衝突 (Race Condition)**：缺乏 Workspace 層級的排隊/鎖定機制，同時間多次觸發會造成多個 `gemini --resume` 併發寫入同一個目錄。
5. **測試覆蓋赤字**：專案核心的 MCP 服務、Heartbeat、Chat、RAG 記憶檢索等核心元件皆**缺乏自動化單元測試**（未符合「No testing, no shipping」標準）。

---

## 🚨 嚴重與高優先級缺陷 (P0 / P1)

### 1. [P0] 測試環境未隔離，直接汙染正式資料庫與環境設定
- **問題檔案**：[`src/main/resources/application.properties`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/resources/application.properties), [`MajordomoApplicationTests.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/test/java/com/github/nicholas23/majordomo/MajordomoApplicationTests.java)
- **現況分析**：
  專案缺少 `src/test/resources/application.properties`。執行 `mvn test` 時，`MajordomoApplicationTests` 載入完整 Spring Context，直接連線至生產環境路徑：
  `jdbc:h2:${user.home}/.majordomo/data/database`
  並在初始化時更動實際的 `~/.gemini/trustedFolders.json` 與 `~/.majordomo/.gemini/settings.json`。
- **潛在風險**：在開發機或 CI/CD 上執行測試會直接覆寫或損壞使用者的正式資料與設定。
- **改進方案**：
  1. 在 `src/test/resources/` 建立 `application-test.properties`，將測試 DataSource 指向 `jdbc:h2:mem:testdb;MODE=MySQL`。
  2. 讓 `Initial` 支援可配置的 `majordomo.base-dir`，測試環境指向暫存目錄 (`@TempDir`)。

---

### 2. [P0] AgentTodoMcpService 漏註冊於 McpServiceConfigure
- **問題檔案**：[`McpServiceConfigure.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/config/McpServiceConfigure.java), [`AgentTodoMcpService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/mcp/AgentTodoMcpService.java)
- **現況分析**：
  `McpServiceConfigure` 僅註冊了 `MemoryMcpService`、`WorkspaceMcpService`、`TelegramMcpService` 三個 Bean 的 `ToolCallbackProvider`，缺少了 `agentTodoToolCallbackProvider`。
- **潛在風險**：Spring AI MCP Server 不會將 `addAgentTodo` 發佈給 Gemini CLI，Agent 在 CLI 中根本看不到這個工具，導致 Agent 自主待辦排程功能完全失效。
- **改進方案**：
  在 `McpServiceConfigure` 內加入：
  ```java
  @Bean
  public ToolCallbackProvider agentTodoToolCallbackProvider(AgentTodoMcpService agentTodoMcpService) {
      return MethodToolCallbackProvider.builder().toolObjects(agentTodoMcpService).build();
  }
  ```

---

### 3. [P1] AgentTodo 缺少狀態管理，重啟或停機引發待辦遺失
- **問題檔案**：[`schema.sql`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/resources/schema.sql), [`AgentTodo.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/agent/AgentTodo.java), [`HeartBeat.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/agent/HeartBeat.java)
- **現況分析**：
  1. `agent_todo` 表僅有 `description`, `scheduled_time`, `created_at`，**沒有狀態欄位**（如 `status` 或 `is_completed`）。
  2. `HeartBeat` 依靠記憶體變數 `lastHeartBeatTime` 做 `BETWEEN :lastCheckTime AND :nowTime` 查詢。
  3. 當應用重啟時，`lastHeartBeatTime` 會被初始化為 `now - 10m`。
- **潛在風險**：
  - 若應用重啟、崩潰或夜間未執行心跳（預設 cron 為 `8-22` 時），在間隔期間到期的任務將被永久跳過。
  - 資料庫中歷史待辦永遠累積且無索引，無法清理。
- **改進方案**：
  1. `agent_todo` 增加 `status VARCHAR(20) DEFAULT 'PENDING'` 欄位與 `scheduled_time` 索引。
  2. 查詢改為 `WHERE status = 'PENDING' AND scheduled_time <= :nowTime`。
  3. 注入心跳提示詞後，將狀態更新為 `TRIGGERED` 或 `COMPLETED`。

---

### 4. [P1] 缺少 Workspace / BasicAgent 層級並發防護 (Race Condition)
- **問題檔案**：[`ExecuteService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/exec/ExecuteService.java)
- **現況分析**：
  `executeAsync` 透過 `workspaceCommandExecutor`（Virtual Threads）直接非同步執行。若使用者在 Web 或 Telegram 連續發送多則訊息，或是排程觸發剛好與手動執行重疊，多個線程會**同時針對同一個目錄**啟動 `gemini --resume` 子程序。
- **潛在風險**：
  Gemini CLI 會在目標目錄讀寫 session 狀態，並行執行將導致 session 檔案寫入衝突、Git 狀態錯亂甚至 CLI 崩潰。
- **改進方案**：
  對同一個 `workspaceId`（包括 BasicAgent 的 `-1`）實施佇列機制或依 Workspace 鎖定（例如使用 `Striped<Lock>` 或工作區任務佇列）。

---

## 🏛️ 架構與職責分離改善 (Architecture & SOC)

### 5. [P2] Web 控制器直接依賴 Repository，違反模組依賴原則
- **問題檔案**：[`ScheduleWebController.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/web/ScheduleWebController.java)
- **現況分析**：
  `ScheduleWebController` 直接注入並操作 `ScheduleRepository`（例如 `findByWorkspaceId`, `findById`），違反了 `GEMINI.md` 規範：「`web` → 呼叫 Service 層，不直接操作 Repository」。
- **改進方案**：
  在 `ScheduleService` 中封裝 `listByWorkspaceId(long workspaceId)` 與 `getSchedule(long id)`，並自 `ScheduleWebController` 移除 `ScheduleRepository` 依賴。

---

### 6. [P2] ONE_TIME 排程執行後未更新狀態
- **問題檔案**：[`ScheduleService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/schedule/ScheduleService.java)
- **現況分析**：
  一次性任務在觸發執行後，僅從記憶體中的 `activeFutures` 移除，但**資料庫內的 `enabled` 欄位依然是 `true`**。重啟應用時，初始化排程會檢查到該筆過期排程並在 log 中印出略過，但在 Web UI 上仍顯示為「啟用中」。
- **改進方案**：
  在 `ONE_TIME` 任務觸發後，於 DB 中將該任務標記為 `enabled = false` 或新增 `COMPLETED` 狀態。

---

### 7. [P2] 殘留的死代碼參數 (Dead Code)
- **問題檔案**：[`WorkspaceService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/workspace/WorkspaceService.java), [`WorkspaceWebController.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/web/WorkspaceWebController.java)
- **現況分析**：
  `WorkspaceService.updateWorkspace` 與 Controller 接收 `boolean activeToTelegram` 參數，但 `Workspace` 實體與資料表中早已沒有此欄位，方法內部完全忽略此參數。另外 `listAllActive()` 的註解仍寫為「查詢所有已啟用 Telegram 通知的 Workspace」。
- **改進方案**：
  清理未使用的參數與過時註解，維持 API 精簡一致。

---

### 8. [P2] 缺乏全域例外處理器 (@ControllerAdvice)
- **問題檔案**：[`com.github.nicholas23.majordomo.web`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/web)
- **現況分析**：
  當使用者傳入不存在的 `workspaceId` 時，`WorkspaceService.getWorkspace` 會拋出 `NoSuchElementException`；傳入無效路徑時拋出 `IllegalArgumentException`。目前沒有 `@ControllerAdvice`，使用者在 HTMX 請求下會收到 Spring 預設的 500 Whitelabel Error Page。
- **改進方案**：
  建立 `GlobalWebExceptionHandler`，捕捉常見例外並回傳友好的 HTMX Toast 或 Alert 錯誤通知片段。

---

## ⚡ 系統穩定度與防禦性編程 (Defensive Programming)

### 9. [P2] CLI 輸出 JSON 解析缺乏安全防護，易引發 NPE 中斷流程
- **問題檔案**：[`HistoryService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/history/HistoryService.java)
- **現況分析**：
  在 `getWorkspaceHistories` 中：
  ```java
  GeminiCliJsonOutputParser.GeminiCLiJsonResponse response = GeminiCliJsonOutputParser.parser(output);
  sb.append("Output: ").append(response.getResponse());
  ```
  若 CLI 因崩潰或異常產出非 JSON 文字，`response.getResponse()` 可能為 `null`，甚至 `response` 解析為錯誤物件。這會讓 Heartbeat 注入時印出 `"Output: null"`，甚至在其他地方引發 NPE，導致整個 Heartbeat 中斷。
- **改進方案**：
  比照 `WorkspaceMcpService` 做法，檢查 `response != null && response.getResponse() != null`，若無 JSON 回應則優雅降級為輸出原始輸出或前 500 字摘要。

---

### 10. [P2] Telegram 長訊息（> 4096 字元）未分段處理
- **問題檔案**：[`TelegramHandleService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/telegram/TelegramHandleService.java), [`TelegramMcpService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/mcp/TelegramMcpService.java)
- **現況分析**：
  Telegram Bot API 單條文字上限為 4096 字元。當 Agent 生成詳盡的回覆或長代碼摘要時，`bot.execute(request)` 會拋出 `[400] Bad Request: message is too long`，導致使用者完全收不到訊息。
- **改進方案**：
  在 `TelegramHandleService.sendMessage` 增加分段機制，若訊息長度大於 4000 字元，自動切割成多則訊息依序發送。

---

### 11. [P2] Web Chat 缺乏非同步回覆機制
- **問題檔案**：[`AgentChatController.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/web/AgentChatController.java), [`agent_chat.html`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/resources/templates/fragments/agent_chat.html)
- **現況分析**：
  使用者在 Web Chat 發送訊息後，Controller 送出非同步任務即立刻回傳畫面。但 Agent 執行需數秒至十數秒，且 Agent 僅呼叫 `sendMessageToUser`（預設推送 Telegram），Web 介面沒有輪詢（Polling）、SSE 或 WebSocket 機制，使用者看著畫面永遠不會更新，必須手動切換頁面才會看到 Agent 的回覆。
- **改進方案**：
  1. 在 `agent_chat.html` 中使用 HTMX 的輪詢機制（例如 `hx-trigger="every 3s"`），或實作 SSE 串流推送。
  2. 確保未綁定 Telegram 的純 Web 使用者也能即時在瀏覽器中看到 Agent 的回覆。

---

### 12. [P2] 中文斷詞與關鍵字檢索限制 (Keyword RAG)
- **問題檔案**：[`KeywordExtractor.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/memory/KeywordExtractor.java)
- **現況分析**：
  `KeywordExtractor` 僅利用空白與標點符號進行切詞。在中文自然語言中，詞與詞之間通常沒有空格（例如「幫我排程每週日備份」），切詞後只會產出一個大長句，導致停用詞過濾無效，也無法在 `MemoryRepository` 命中 LIKE 查詢。
- **改進方案**：
  引入輕量級的分詞邏輯（或常見的 N-gram / 詞彙清單比對），提高中文語句在關鍵字記憶庫的召回率。

---

### 13. [P2] TelegramSettingsService 金鑰衍生與頻繁讀盤
- **問題檔案**：[`TelegramSettingsService.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/telegram/TelegramSettingsService.java)
- **現況分析**：
  1. AES-256 金鑰衍生僅使用 `System.getProperty("user.home")` 的 SHA-256，未加鹽，安全性較為薄弱。
  2. 每次收到 Telegram Update 時，`getAllowedUser()` 都會讀取硬碟上的屬性檔並做一次 AES 解密。
- **改進方案**：
  在 Service 內部加入快取機制（讀取後保存在記憶體中，僅在儲存或重新初始化時刷新），減少不必要的磁碟 I/O 與解密計算。

---

### 14. [P3] TaskScheduler 優雅停機延遲導致測試卡頓 30 秒
- **問題檔案**：[`AsyncThreadPoolsConfigure.java`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/src/main/java/com/github/nicholas23/majordomo/config/AsyncThreadPoolsConfigure.java)
- **現況分析**：
  `scheduler.setWaitForTasksToCompleteOnShutdown(true)` 搭配 `scheduler.setAwaitTerminationSeconds(30)`，導致在 Spring Context 關閉時若有背景排程（如 Heartbeat），Surefire 會等待整整 30 秒才強制結束，每次執行 `mvn test` 都花費超過 34 秒。
- **改進方案**：
  將停機緩衝秒數改為可配置項目，或在測試環境下降低為 1~2 秒，大幅縮短測試反饋循環。

---

### 15. [P3] 類別庫依賴重複與警告
- **問題檔案**：[`pom.xml`](file:///Users/chaoshen/myDevelop/myGithub/Majordomo/pom.xml)
- **現況分析**：
  1. `pom.xml` 中引入了 `org.json:json`，而 `spring-boot-starter-test` 自帶了 `android-json`，造成測試時 JVM 印出 `DuplicateJsonObjectContextCustomizer` 警告。
  2. 整個專案僅有 `Initial.java` 使用了 `org.json`，其他模組皆使用 Spring Boot 內建的 Jackson (`ObjectMapper`)。
- **改進方案**：
  將 `Initial.java` 的 JSON 讀寫重構成 Jackson，並自 `pom.xml` 移除 `org.json` 依賴。

---

## 🧪 測試覆蓋率赤字清單 (Testing Gaps - Rule #4)

目前專案共約 25 個核心類別，其中**超過 70% 的類別完全沒有自動化測試**。以下為待補測試清單：

| 模組 | 待補測試類別 | 關鍵測試場景 |
|------|-------------|-------------|
| `agent` | `InitialTest` | 設定檔產生、GEMINI.md/SOUL.md 補齊、信任目錄設定 |
| `agent` | `HeartBeatTest` | 長心跳與短心跳判定條件、待辦過濾、提示詞組裝 |
| `agent` | `AgentPromptBuilderTest` | RAG 記憶注入格式、訊息邊界條件 |
| `mcp` | `MemoryMcpServiceTest` | store (Upsert)、recall (關鍵字檢索)、forget、邊界防呆 |
| `mcp` | `WorkspaceMcpServiceTest` | 派發指令建立 Task、標記已讀、排程設定與驗證 |
| `mcp` | `AgentTodoMcpServiceTest` | 時間格式合法性校驗、例外處理解析、資料庫持久化 |
| `mcp` | `TelegramMcpServiceTest` | 訊息發送與 AgentChatHistory 紀錄連動 |
| `chat` | `AgentChatServiceTest` | 對話儲存、Top 50 排序反轉 (舊到新) 正確性 |
| `memory`| `KeywordExtractorTest` | 中英停用詞過濾、標點符號分詞、最小長度限制 |
| `history`| `HistoryServiceTest` | 歷史紀錄分頁、STDOUT/STDERR 增量寫入與合併讀取 |
| `web` | 各 WebController 測試 | MockMvc 驗證端點狀態碼與 HTMX 渲染邏輯 |

---

## 📋 規範遵循度檢核 (Reviewable Code Standard)

依據 `GEMINI.md` 規範：
- [x] **File Header**：大部分類別具備目的、關鍵項目、模組標註。（少數模組標籤不一致，如 `PromptBuilder` 標為 `utils`，`Initial` 標為 `basicagent`）。
- [x] **Review Checklist**：大部分類別檔案末尾具備檢查清單。
- [!] **WHY / REASONING 註解**：核心決策點具備註解，但部分併發與邊界條件缺少 `// EDGE_CASE:` 標註。
- [x] **虛擬執行緒**：`AsyncThreadPoolsConfigure` 正確配置 Virtual Threads。

---

## 🛠️ 翻修優先順序建議 (Actionable Roadmap)

1. **第一期（穩定地基）**：
   - 建立測試設定檔與 H2 記憶體資料庫隔離，修復 Surefire 30 秒關機卡頓。
   - 補上 `AgentTodoMcpService` 至 `McpServiceConfigure`。
   - 修復 `HistoryService` 的 JSON 解析 NPE 風險。
2. **第二期（修復管線）**：
   - `agent_todo` 新增狀態欄位，改進 Heartbeat 輪詢機制防止遺漏。
   - 加入 Workspace 層級並發防護，防止 CLI session 錯亂。
   - 修正 `ScheduleWebController` 違規直連 Repository 問題。
3. **第三期（裝潢與體驗）**：
   - Telegram 長訊息自動分段截斷。
   - Web Chat 增加輪詢反饋機制。
   - 補齊核心 Service 與 MCP 的單元測試。
