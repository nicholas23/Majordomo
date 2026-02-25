/**
 * 目的：Agent 對話紀錄實體
 * 關鍵項目：
 * 1. 記錄 User 與 Agent 之間的對話歷史
 * 2. 欄位包含 role (USER/AGENT) 及 source (TELEGRAM/WEB)
 * 模組：history
 */
package com.github.nicholas23.majordomo.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("agent_chat_history")
public class AgentChatHistory {
    @Id
    private long id;
    private String role; // 'USER' or 'AGENT'
    private String message;
    private String source; // 'TELEGRAM' or 'WEB'
    private LocalDateTime createAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }
}

/* ### Review Checklist ###
 * 1. 欄位定義：必要欄位齊全並與資料庫對應？ ✓
 * 2. 存取權限：Getter/Setter 正確設置？ ✓
 */
