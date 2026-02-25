/**
 * 目的：執行歷史紀錄的資料存取層（使用 Spring Data JDBC CrudRepository）
 * 關鍵項目：
 * 1. 繼承 CrudRepository 提供基本 CRUD 操作
 * 2. 透過 @Query 提供分頁查詢、最後一筆查詢、總數查詢
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// REASONING: 從手動 JdbcTemplate 改為 CrudRepository，
// 由 Spring Data JDBC 自動處理物件-關聯映射，大幅簡化程式碼
@Repository
public interface HistoryRepository extends CrudRepository<History, Long> {

    /**
     * 目的：分頁查詢指定 Workspace 的歷史紀錄（依 ID 降序）。
     * 輸入：
     * - workspaceId: long
     * - pageable: Pageable
     * 輸出：Page<History>
     * 限制：無
     * 副作用：無
     */
    Page<History> findByWorkspaceIdOrderByIdDesc(long workspaceId, Pageable pageable);

    /**
     * 目的：取得指定 Workspace 的最後一筆歷史紀錄。
     * 輸入：
     * - workspaceId: long
     * 輸出：Optional<History>
     * 限制：無
     * 副作用：無
     */
    @Query("SELECT * FROM history WHERE workspace_id = :workspaceId ORDER BY id DESC LIMIT 1")
    Optional<History> findTopByWorkspaceIdOrderByIdDesc(@Param("workspaceId") long workspaceId);


    List<History> findByWorkspaceIdGreaterThan(long workspaceId);

    List<History> findByWorkspaceIdGreaterThanAndEndTimeGreaterThanEqual(long workspaceId, LocalDateTime sinse);
}

/* ### Review Checklist ###
 * 1. 查詢語法是否使用 Spring Data JDBC 機制 ✓
 * 2. 方法命名是否符合規範 ✓
 */
