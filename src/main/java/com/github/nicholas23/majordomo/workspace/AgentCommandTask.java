/**
 * 目的：此實體用來記錄 Agent 透過 MCP 指派的工作區執行任務
 * 關鍵項目：
 * 1. 支援非同步：Agent MCP 只負責建立此任務與排程執行，並立即回傳 Token/ID。
 * 2. 心跳注入：執行結果寫回此表，並透過 `isRead` 判斷是否需要在推播給 Agent。
 * 模組：workspace
 */
package com.github.nicholas23.majordomo.workspace;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("agent_command_task")
public class AgentCommandTask {
    @Id
    private long id;
    private long workspaceId;
    private String command;
    private Long historyId;
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private boolean isRead;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }
}

/* ### Review Checklist ###
 * 1. 狀態管理：status 欄位定義是否清晰？ ✓
 * 2. 存取權限：Getter/Setter 是否正確設置？ ✓
 */
