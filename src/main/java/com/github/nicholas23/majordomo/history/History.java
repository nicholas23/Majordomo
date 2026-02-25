/**
 * 目的：執行歷史紀錄的資料模型
 * 關鍵項目：
 * 1. 對應 history 資料表，記錄每次 Gemini CLI 執行的指令與時間
 * 2. 透過 resultId 關聯到 history_result 表取得執行結果內容
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("history")
public class History {
    @Id
    private long id;
    private long workspaceId;
    private String command;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private HistoryStatus status;

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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public HistoryStatus getStatus() {
        return status;
    }

    public void setStatus(HistoryStatus status) {
        this.status = status;
    }
}

/* ### Review Checklist ###
 * 1. 欄位定義：必要欄位齊全並與資料庫對應？ ✓
 * 2. 存取權限：Getter/Setter 正確設置？ ✓
 */
