/**
 * 目的：通用文字儲存實體
 * 關鍵項目：
 * 1. 對應 result_text 資料表
 * 2. 儲存執行輸出 log 或錯誤 log
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("result_text")
public class ResultText {
    @Id
    private Long id;

    private Long historyId;
    private ResultTextType type;
    private LocalDateTime createAt;
    private String content;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public ResultTextType getType() {
        return type;
    }

    public void setType(ResultTextType type) {
        this.type = type;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }    
    
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}

/* ### Review Checklist ###
 * 1. 欄位定義：必要欄位齊全並與資料庫對應？ ✓
 * 2. 存取權限：Getter/Setter 正確設置？ ✓
 */
