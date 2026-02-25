/**
 * 目的：排程任務的資料模型
 * 關鍵項目：
 * 1. 對應 schedule 資料表，記錄排程的指令、類型、時間設定
 * 2. 支援 CRON_JOB（使用 cron 欄位）和 ONE_TIME（使用 startTime 欄位）兩種排程模式
 * 模組：schedule
 */
package com.github.nicholas23.majordomo.schedule;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("schedule")
public class Schedule {
    @Id
    private long id;
    private long workspaceId;
    private String command;
    private ScheduleType type;
    // REASONING: startTime 用於 ONE_TIME 排程的執行時間點；CRON_JOB 則使用 cron 欄位
    private LocalDateTime startTime;
    private String cron;
    private boolean enabled;

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

    public ScheduleType getType() {
        return type;
    }

    public void setType(ScheduleType type) {
        this.type = type;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

/* ### Review Checklist ###
 * 1. 欄位定義：必要欄位齊全並與資料庫對應？ ✓
 * 2. 存取權限：Getter/Setter 正確設置？ ✓
 */
