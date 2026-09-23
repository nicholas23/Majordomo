# 提升 BasicAgent 對 Memory MCP 與 AgentTodo 的使用率

> 日期：2026-03-11  
> 狀態：Draft

## 背景與問題

BasicAgent 目前很少主動使用 Memory MCP（`store`/`recall`/`forget`）和 `addAgentTodo` MCP 工具。
經分析，三個根因同時作用導致此現象：

| # | 根因 | 影響 |
|---|------|------|
| 1 | Tool Description 充滿抑制性語言（大量「避免」） | LLM 判斷「大多數時候不該使用」 |
| 2 | `gemini.md` 提供了更簡單的替代路徑（直接寫 MEMORY.md） | Agent 選擇阻力最小的路 |
| 3 | 日常對話 Prompt（`AgentPromptBuilder`）完全不提及這兩個工具 | 僅心跳時才有工具存在感，頻率太低 |

## 問題詳述

### 1. Tool Description 的抑制效應

Memory MCP 三個工具的 `@Tool(description)` 都使用「限制優先」的寫法：

| 工具 | 描述中的抑制語言 |
|------|------------------|
| `store` | 「**避免**資訊短暫、吵雜或敏感及**無明確需要儲存**的資訊」 |
| `recall` | 「**避免**頻繁或過度回顧…**答案己存在目前的對話或文件時**使用」 |
| `forget` | 「**避免**頻繁或過度刪除…資訊可能仍有價值或未明確需要刪除時使用」 |

LLM 在選擇工具時，這些「避免」字眼構成心理門檻，讓它傾向「不冒險」而跳過工具。

### 2. `gemini.md` 的替代路徑

`gemini.md` 第 32 行寫道：

```
當你學到一個教訓 -> 寫入 MEMORY.md 的「經驗教訓」段落，或使用 Memory MCP 工具來做紀錄。
```

**「或」** 字讓 Memory MCP 變成次選。Gemini CLI 本身就有檔案讀寫能力，直接寫 `MEMORY.md` 更直覺、阻力更小。

類似地，`addAgentTodo` 在 `memory.md` 第 18 行與 `HEARTBEAT.md` 之間的區分雖然清楚，但 Agent 在日常對話中**缺乏主動使用它的動機** — 大多數互動是「問答即結束」，不會自然想到「我需要後續跟進」。

### 3. Prompt 中工具可見度的斷層

```
使用者對話 → AgentPromptBuilder.build()
  → 注入：Auto-Context 記憶 + 回覆規則 + 使用者訊息
  → ❌ 完全不提及 Memory MCP
  → ❌ 完全不提及 addAgentTodo

心跳觸發 → HeartBeat.build()
  → ✅ 提及 recall（建議回想背景資訊）
  → ✅ 提及 addAgentTodo（強烈建議設定待辦）
  → 但心跳頻率低（長心跳每 2 小時，短心跳無事跳過）
```

Agent 大部分的互動來自使用者對話，但 `AgentPromptBuilder` 完全不促使 Agent 使用這兩類工具。

---

## 改善方案

### Task 1：改寫 Memory MCP 的 Tool Description

**檔案**：`MemoryMcpService.java`  
**原則**：從「限制優先」改為「鼓勵 + 給具體範例」  

```diff
  // store
- @Tool(description = "儲存在記憶庫：請在儲存持久的偏好設定、決策或關鍵情境下使用，"
-     + "但避免資訊短暫、吵雜或敏感及無明確需要儲存的資訊。")
+ @Tool(description = "永久記住重要資訊。當使用者表達偏好、做出決策、或對話中出現值得長期保存的經驗時，"
+     + "主動使用此工具儲存。例如：使用者的工作習慣、技術決定、重要的經驗教訓。"
+     + "比直接寫 MEMORY.md 更好，因為支援關鍵字搜尋與分類管理。")

  // recall
- @Tool(description = "找回記憶：請在需要回顧過去資訊以輔助決策時使用，"
-     + "但避免頻繁或過度回顧，尤其是當資訊已過時、無關，"
-     + "可能引起混淆以及答案己存在目前的對話或文件時使用。")
+ @Tool(description = "搜尋過去記住的資訊。當使用者提到之前聊過的話題、"
+     + "你需要回想過去的決策或偏好來輔助當前對話時，積極使用此工具。")

  // forget
- @Tool(description = "忘記記憶：忘掉錯誤，過時與明確被要求忘掉的記憶，"
-     + "避免頻繁或過度刪除，尤其是當資訊可能仍有價值或未明確需要刪除時使用。")
+ @Tool(description = "刪除錯誤或過時的記憶。當使用者明確要求忘記某事、"
+     + "或你發現已儲存的資訊已不正確時使用。")
```

### Task 2：改寫 `addAgentTodo` 的 Tool Description

**檔案**：`AgentTodoMcpService.java`  

```diff
- @Tool(description = "這是用來提供加入ToDoList的功能。加入的待辦事項，會在指定時間，
-     經由短心跳觸發，加入到prompt中。scheduledTime 必須以「絕對時間」輸入
-     (例如: 2024-12-31 23:59:00)，描述請簡短精確。")
+ @Tool(description = "設定未來提醒——當你需要稍後跟進某件事時使用。"
+     + "例如：等待指令結果回報、答應使用者稍後檢查、需要定時提醒。"
+     + "系統會在指定時間透過短心跳喚醒你。"
+     + "scheduledTime 必須以絕對時間輸入 (例如: 2025-05-15 14:30:00)，描述請簡短精確。")
```

### Task 3：在 `AgentPromptBuilder` 中加入工具使用提示

**檔案**：`AgentPromptBuilder.java`  
**位置**：在回覆規則之後、使用者訊息之前

```diff
  sb.append(String.format("""
          【回覆規則】
          請一定要使用 **`sendMessageToUser`**，來回覆使用者的訊息，並且只能回覆一次。
+
+         【記憶與待辦】
+         - 若對話中出現值得長期記住的資訊（決策、偏好、經驗教訓），請使用 `store` 記憶工具儲存。
+         - 若有需要稍後跟進的事項，請使用 `addAgentTodo` 設定提醒時間。
+
          【來自%s的訊息（現在時間：%s 】
          %s
          """, username, dtf.format(LocalDateTime.now()), message));
```

### Task 4：修正 `gemini.md` 中 Memory MCP 的定位

**檔案**：`src/main/resources/basicagent/gemini.md`  
**目的**：消除「或」帶來的模糊性，明確 Memory MCP 為主要記憶工具

```diff
  ### 寫下來－不要只記在腦子裡！

- - 當你學到一個教訓 -> 寫入 MEMORY.md 的「經驗教訓」段落，或使用 Memory MCP 工具來做紀錄。
+ - 當你學到一個教訓 -> 使用 Memory MCP 的 `store` 工具儲存（優先），
+   並可同步更新 MEMORY.md 作為人工可讀的備份。
```

同時在「記憶操作規則」增加 Memory MCP 的優先地位：

```diff
  ### 記憶操作規則

  - **長期記憶 (MEMORY.md)**：當對話產生具有延續價值的重要資訊時...追加寫入 MEMORY.md。
+ - **結構化記憶 (Memory MCP)**：對於需要日後精確檢索的關鍵資訊（偏好、決策、經驗教訓），
+   請優先使用 `store` MCP 工具儲存，它支援分類與關鍵字搜尋，比純文字檔更容易回想。
  - **每日記錄 (memory/YYYY-MM-DD.md)**：你**必須**主動將每天的重要進展寫入當日日誌。
```

---

## 模組歸屬

| 變更 | 檔案 | 模組 |
|------|------|------|
| 改寫 Memory MCP Tool Description | `MemoryMcpService.java` | `mcp` |
| 改寫 AgentTodo Tool Description | `AgentTodoMcpService.java` | `mcp` |
| 加入工具提示到日常 Prompt | `AgentPromptBuilder.java` | `agent` |
| 調整記憶優先順序 | `gemini.md` | `resources/basicagent` |

## 預期效果

| 指標 | 改善前 | 改善後（預期） |
|------|--------|----------------|
| Memory `store` 使用頻率 | 幾乎為零 | 每次有決策/偏好的對話後觸發 |
| Memory `recall` 使用頻率 | 僅心跳時偶爾 | 日常對話中遇到相關話題時主動使用 |
| `addAgentTodo` 使用頻率 | 幾乎為零 | 有跟進需求時主動設定 |
| Token 成本 | 不變 | 略增（多了工具呼叫），但記憶品質顯著提升 |

## 風險與注意事項

- **過度使用風險**：改為鼓勵性描述後，Agent 可能過度呼叫 `store`。可透過觀察日誌頻率，若過高再微調描述。
- **向下相容**：本改動不涉及 Schema 或 API 變更，純粹是 Prompt/Description 層面的調整。
- **驗證方式**：修改後透過 Telegram 或 Web 介面與 Agent 對話，觀察 log 中 `[Memory]` 和 `[AgentTodoMcpService]` 的出現頻率。
