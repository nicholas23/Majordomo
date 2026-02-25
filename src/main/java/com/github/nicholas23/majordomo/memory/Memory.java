/**
 * 目的：記憶體 (Memory) 的資料模型
 * 關鍵項目：
 * 1. 對應 memory 資料表，儲存 AI 的長期記憶（偏好、事實、經驗等）
 * 2. 遵循 ZeroClaw 記憶體分類規範（core / daily / conversation / custom）
 * 3. 使用 memory_key 作為記憶的唯一辨識鍵，遵循 domain_category_detail 命名慣例
 * 模組：memory
 */
package com.github.nicholas23.majordomo.memory;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

// REASONING: 使用傳統 POJO (非 record) 以便 Spring Data JDBC 進行 set-based 更新操作，
// 與專案中 History、Workspace 等既有實體保持一致的風格
@Table("memory")
public class Memory {
    @Id
    private Long id;
    private String memoryKey;
    private String category;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

/* ### Review Checklist ###
 * 1. 欄位命名：memory_key 避開 SQL 保留字 ✓
 * 2. 時間欄位：createdAt / updatedAt 使用 LocalDateTime ✓
 * 3. 風格一致性：與專案既有 Entity (History, Workspace) POJO 風格一致 ✓
 */
