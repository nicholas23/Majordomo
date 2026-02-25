/**
 * 目的：執行結果文字紀錄的資料存取層（使用 Spring Data JDBC CrudRepository）
 * 關鍵項目：
 * 1. 繼承 CrudRepository 提供基本 CRUD 操作
 * 2. 透過 @Query 提供附加內容的功能
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ResultTextRepository extends CrudRepository<ResultText, Long> {
    /**
     * 目的：按照 historyId 和 type 將儲存在 ResultText 中的所有對應內容依據 Timestamp 排序後回傳。
     * 輸入：
     * - historyId: long - 所屬的 History ID
     * - type: ResultTextType - (STDOUT 或 STDERR)
     * 輸出：List<String> - 排序後的字串清單
     * 限制：對應紀錄過大時可能會消耗記憶體
     * 副作用：無
     */
    @Query("SELECT content FROM result_text WHERE history_id = :historyId AND type = :type ORDER BY create_at ASC")
    List<String> findByHistoryIdAndTypeOrderByCreateAt(long historyId, ResultTextType type);
}

/* ### Review Checklist ###
 * 1. 職責確認：此介面僅處理 ResultText 表的資料庫互動？ ✓
 * 2. 效能與安全性：使用 Spring Data JDBC 機制避免 SQL Injection？ ✓
 */
