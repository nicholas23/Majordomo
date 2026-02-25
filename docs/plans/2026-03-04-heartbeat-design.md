# 雙層心跳機制設計

> 日期：2026-03-04  
> 狀態：Draft

## 背景與問題

目前心跳每 15 分鐘固定觸發 CLI（每天 ~56 次），即便「沒事做」也消耗 token，成本過高。

## 設計核心

將心跳分為**長心跳**與**短心跳**兩層，搭配**系統閘門**與**Agent 自排待辦**機制。

## 長心跳（每 2 小時）

```
Cron: 0 0 8,10,12,14,16,18,20,22 * * *
```

- **一定執行 CLI**（無閘門）
- 做所有事：HEARTBEAT.md 例行任務、未讀結果、workspace status 注入、Agent 自排待辦
- 每天固定 8 次

## 短心跳（每 15 分鐘 + 系統閘門）

```
Cron: 0 */15 8-22 * * *（排除長心跳的整點時段）
```

- **系統先檢查**，有待處理事項才啟動 CLI
- 沒事 → 跳過，零 token 成本
- 取代事件驅動機制

### 閘門檢查項目

| 檢查項目 | 來源 |
|---|---|
| 未讀的 AgentCommandTask | 子 Agent 任務完成 |
| 到期的 Agent 待辦事項 | 主 Agent 自己排的 |

全部沒有 → 跳過，零 token 成本。

## 主 Agent 待辦事項（自排任務）

主 Agent 透過 MCP 建立自己的「待辦事項」，帶有預定時間。
短心跳閘門用時間窗口查詢，**不需要標記已讀或刪除**。

### MCP Tool 介面

```
addAgentTodo(description, scheduledTime)
  → 例如：「14:30 檢查 workspace A 的重構進度」
```

### 閘門查詢邏輯

```sql
SELECT * FROM agent_todo
WHERE scheduled_time BETWEEN :lastCheckTime AND :now
```

時間窗口自動滑動，不需額外狀態管理。

### DB Schema

```sql
CREATE TABLE agent_todo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(500) NOT NULL,
    scheduled_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP
);
```

## 成本對比

| | 現在 | 新設計 |
|---|---|---|
| 固定 CLI 啟動 | ~56 次/天 | 8 次/天（長心跳） |
| 事件處理 | 等 15 分鐘心跳 | 最多等 15 分鐘（短心跳有事時觸發） |
| 空跑成本 | 全部 56 次 | 短心跳沒事就跳過 |

## 模組歸屬

| 變更 | 模組 |
|---|---|
| `AgentTodo` 實體 + Repository | `agent` |
| Agent Todo MCP Tool | `mcp` |
| 長/短心跳排程管理 | `schedule` |
| 心跳閘門邏輯 | `agent` (HeartBeat) |

## 架構圖

```
                          系統 (Java)                     主 Agent (BasicAgent)
                               │                              │
  每15分鐘短心跳:                │                              │
  有待處理? ──YES──► 啟動CLI ──►│                              │
      │                        │       注入事件相關資訊 ──────►│
     NO → 跳過（零成本）        │                              │
                               │                              │
  每2小時長心跳:                │                              │
  ──── always ──► 啟動CLI ────►│                              │
                               │  注入 status + todo + 全部 ──►│
                               │                              │
                               │ ◄── addAgentTodo ────────────│
                               │     「14:30 檢查 workspace A」│
```
