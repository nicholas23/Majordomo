/**
 * 目的：工作空間（Workspace）的資料模型
 * 關鍵項目：
 * 1. 對應 workspace 資料表，記錄工作空間的名稱、路徑及狀態
 * 2. 使用 active 軟刪除機制管理工作空間生命週期
 * 模組：workspace
 */
package com.github.nicholas23.majordomo.workspace;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("workspace")
public class Workspace {
    @Id
    private long id;
    private String name;
    private String absolutePath;
    private String description;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    // REASONING: 使用 Boolean 包裝型別搭配軟刪除策略，而非物理刪除資料
    private Boolean active;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

}

/* ### Review Checklist ###
 * 1. 資料型別：active 使用 Boolean 支援 Null 狀態？ ✓
 * 2. 存取權限：Getter/Setter 是否正確設置？ ✓
 */
