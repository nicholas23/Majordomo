/**
 * 目的：主 Agent 自排任務（待辦事項）實體
 * 關鍵項目：
 * 1. 供 Agent 設定未來需要檢查的任務。
 * 2. 由短心跳（Short Heartbeat）檢查時間視窗，決定是否喚醒 Agent。
 * 模組：agent
 */
package com.github.nicholas23.majordomo.agent;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("agent_todo")
public class AgentTodo {
    @Id
    private long id;
    private String description;
    private LocalDateTime scheduledTime;
    private String status = "PENDING";
    private LocalDateTime createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

/* ### Review Checklist ###
 * 1. 職責：作為待辦事項紀錄實體？ ✓
 * 2. 存取權限：Getter/Setter 是否正確設置？ ✓
 */
