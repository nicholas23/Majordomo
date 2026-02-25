/**
 * 目的：記憶體 (Memory) 的資料存取層（使用 Spring Data JDBC CrudRepository）
 * 關鍵項目：
 * 1. 繼承 CrudRepository 提供基本 CRUD 操作
 * 2. 透過 @Query 提供 keyword-based 關鍵字模糊查詢（Keyword RAG）
 * 3. 支援依 memoryKey 精確查詢與依 category 分類查詢
 * 模組：memory
 */
package com.github.nicholas23.majordomo.memory;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// REASONING: 從手動 JdbcTemplate 操作改為 CrudRepository，
// 由 Spring Data JDBC 自動處理物件-關聯映射，與專案現有 Repository 風格一致
@Repository
public interface MemoryRepository extends CrudRepository<Memory, Long> {

    /**
     * 目的：依據 memoryKey 精確查詢記憶。
     * 輸入：
     * - memoryKey: String - 記憶的唯一辨識鍵
     * 輸出：Optional<Memory>
     * 限制：無
     * 副作用：無
     */
    Optional<Memory> findByMemoryKey(String memoryKey);

    /**
     * 目的：依據分類查詢所有記憶。
     * 輸入：
     * - category: String - 記憶分類 (core / daily / conversation / custom)
     * 輸出：List<Memory>
     * 限制：無
     * 副作用：無
     */
    List<Memory> findByCategory(String category);

    /**
     * 目的：依據單一關鍵字進行模糊查詢，並限制回傳筆數。
     * REASONING: H2 不支援 Vector Embedding，因此使用 LIKE 進行基礎關鍵字匹配，作為輕量級 RAG 的檢索基礎
     * 輸入：
     * - keyword: String - 搜尋關鍵字
     * - limit: int - 回傳上限
     * 輸出：List<Memory>
     * 限制：使用 LIKE "%keyword%" 會進行全表掃描，效能隨著資料增加可能下降
     * 副作用：無
     */
    @Query("SELECT * FROM memory WHERE LOWER(content) LIKE LOWER(CONCAT('%', :keyword, '%'))"
            + " OR LOWER(memory_key) LIKE LOWER(CONCAT('%', :keyword, '%'))"
            + " ORDER BY updated_at DESC LIMIT :limit")
    List<Memory> searchByKeyword(@Param("keyword") String keyword,
                                 @Param("limit") int limit);

    /**
     * 目的：依據 memoryKey 刪除指定記憶。
     * 輸入：
     * - memoryKey: String - 記憶的唯一辨識鍵
     * 輸出：無
     * 限制：無
     * 副作用：刪除該筆記憶資料
     */
    void deleteByMemoryKey(String memoryKey);
}

/* ### Review Checklist ###
 * 1. SQL 注入防護：使用 @Param 綁定參數 ✓
 * 2. 查詢效能：memory_key 與 category 有索引支持 ✓
 * 3. 風格一致性：與 HistoryRepository / WorkspaceRepository 一致 ✓
 */
