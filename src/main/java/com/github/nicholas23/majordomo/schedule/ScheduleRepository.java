/**
 * 目的：排程任務的資料存取層（使用 Spring Data JDBC CrudRepository）
 * 關鍵項目：
 * 1. 繼承 CrudRepository 提供基本 CRUD 操作
 * 2. 透過衍生查詢及 @Query 提供依 Workspace、類型查詢
 * 3. 透過 @Modifying @Query 提供停用排程功能
 * 模組：schedule
 */
package com.github.nicholas23.majordomo.schedule;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// REASONING: 從手動 JdbcTemplate 改為 CrudRepository，
// 由 Spring Data JDBC 自動處理物件-關聯映射及 boolean/enum 轉換
@Repository
public interface ScheduleRepository extends CrudRepository<Schedule, Long> {

    /**
     * 目的：查詢指定 Workspace 的所有排程。
     * 輸入：
     * - workspaceId: long
     * 輸出：List<Schedule>
     * 限制：無
     * 副作用：無
     */
    List<Schedule> findByWorkspaceId(long workspaceId);

    /**
     * 目的：查詢所有已啟用的排程。
     * 輸入：無
     * 輸出：List<Schedule> - 所有 enabled = true 的排程
     * 限制：無
     * 副作用：無
     */
    List<Schedule> findByEnabledTrue();

    /**
     * 目的：依排程類型查詢所有未來待執行的排程。
     * WHY: 只載入 start_time >= 現在的排程，避免重複執行已過期的一次性任務
     * 輸入：
     * - type: String - 排程類型名稱
     * - now: String - 當前時間字串
     * 輸出：List<Schedule> - 啟動時間在當前時間之後的排程清單
     * 限制：now 必須為可比較的時間字串
     * 副作用：無
     */
    @Query("SELECT * FROM schedule WHERE type = :type AND start_time >= :now")
    List<Schedule> findActiveByType(@Param("type") String type, @Param("now") String now);

    /**
     * 目的：停用指定排程。
     * 輸入：
     * - id: long - 排程 ID
     * 輸出：無
     * 限制：無
     * 副作用：更新 schedule 資料表的 enabled 欄位為 false
     */
    @Modifying
    @Query("UPDATE schedule SET enabled = false WHERE id = :id")
    void disableById(@Param("id") long id);
}

/* ### Review Checklist ###
 * 1. 查詢語法是否使用 Spring Data JDBC 機制 ✓
 * 2. 方法命名是否符合規範 ✓
 */
