# Agent2Agent 通訊機制設計

> 日期：2026-03-04  
> 狀態：Draft

## 背景與問題

主 Agent（BasicAgent）透過 MCP Tool 派發指令給各 Workspace 的子 Agent。
子 Agent 使用 `--resume` 執行，具備持續累積的 context，但主 Agent **無法得知**子 Agent 的累積經驗與現況，造成資訊不對稱。

## 設計核心

- 以 `workspaceId` 為 key，在系統（DB）中集中管理 workspace 狀態
- 所有 Agent 透過 MCP Tool 讀寫，不需特別設定
- 每個 section 限 500 字，局部覆寫以節省 token

## 預定義 Section

| Section Key | 用途 | 心跳注入 | 派發指令注入 |
|---|---|:---:|:---:|
| `current_status` | 子 Agent 回報工作區現況 | ✅（有更新時） | ✅ |
| `last_result` | 上次執行的重點結論 | ❌ | ✅ |
| `agent_notes` | 子 Agent → 主 Agent 的溝通管道 | ✅ | ❌ |

## MCP Tool 介面

```
readWorkspaceStatus(workspaceId, sectionKey?)
  → 讀取某 workspace 的全部或指定 section

updateWorkspaceStatus(workspaceId, sectionKey, content)
  → 覆寫指定 section（限 500 字）

clearWorkspaceStatus(workspaceId, sectionKey?)
  → 清除指定 section 或全部
```

## DB Schema

```sql
CREATE TABLE workspace_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    section_key VARCHAR(50) NOT NULL,
    content VARCHAR(500),
    updated_at TIMESTAMP,
    is_dirty BOOLEAN DEFAULT FALSE,
    UNIQUE (workspace_id, section_key)
);
```

`is_dirty`：被更新時設為 `true`，主 Agent 心跳讀取後設回 `false`。

## 注入機制

- **心跳注入**：掃描 `is_dirty = true` 的 `current_status` 與 `agent_notes`
- **派發指令注入**：在 `PromptBuilder.build()` 中注入目標 workspace 的 `current_status` + `last_result`

## 模組歸屬

| 變更 | 模組 |
|---|---|
| `WorkspaceStatus` 實體 + Repository | `workspace` |
| Workspace Status MCP Tool | `mcp` |
| 派發指令注入邏輯 | `exec` (PromptBuilder) |

## 架構圖

```
子 Agent (workspace)           系統 (Java)                  主 Agent (BasicAgent)
      │                            │                              │
      │── updateWorkspaceStatus ──►│                              │
      │   (current_status)         │  ┌───────────────────┐       │
      │                            │  │ workspace_status  │       │
      │                            │  └───────────────────┘       │
      │                            │                              │
      │                            │ ◄── 心跳觸發 ──              │
      │                            │     注入 dirty status ──────►│
      │                            │                              │
      │                            │ ◄── 派發指令 ──              │
      │  ◄── prompt + context ──── │     注入 current_status +    │
      │                            │     last_result              │
```
