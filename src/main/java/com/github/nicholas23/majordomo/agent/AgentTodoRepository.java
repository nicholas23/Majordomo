/**
 * 目的：主 Agent 自排任務（待辦事項）的資料庫操作介面
 * 關鍵項目：
 * 1. 繼承 CrudRepository 管理 AgentTodo 資料
 * 2. 提供給 HeartBeat 短心跳時段查詢應觸發的待辦事項
 * 模組：agent
 */
package com.github.nicholas23.majordomo.agent;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentTodoRepository extends CrudRepository<AgentTodo, Long> {

    /**
     * 目的：查詢目前時間點需要觸發的待辦事項（狀態為 PENDING 且預定時間已到）
     * WHY: 透過狀態欄位過濾，徹底避免應用重啟或夜間非心跳時段導致待辦遺漏
     * 輸入：
     * - nowTime: 目前檢查時間
     * 輸出：List<AgentTodo> - 待觸發的待辦事項
     * 限制：只返回狀態為 PENDING 的項目
     * 副作用：無
     */
    @Query("SELECT * FROM agent_todo WHERE status = 'PENDING' AND scheduled_time <= :nowTime ORDER BY scheduled_time ASC")
    List<AgentTodo> findPendingTodosToTrigger(@Param("nowTime") LocalDateTime nowTime);

    /**
     * 目的：查詢目前時間點需要觸發的待辦事項（舊版時間視窗方式，保留相容性）
     * 輸入：
     * - lastCheckTime: 上次心跳檢查時間
     * - nowTime: 目前檢查時間
     * 輸出：List<AgentTodo> - 落在時間區間內的待辦事項
     * 限制：時間為封閉區間
     * 副作用：無
     */
    @Query("SELECT * FROM agent_todo WHERE scheduled_time BETWEEN :lastCheckTime AND :nowTime ORDER BY scheduled_time ASC")
    List<AgentTodo> findTodosToTrigger(@Param("lastCheckTime") LocalDateTime lastCheckTime, @Param("nowTime") LocalDateTime nowTime);
}

/* ### Review Checklist ###
 * 1. 職責：處理資料庫查詢操作？ ✓
 * 2. 查詢邏輯：利用排程時間做為時間視窗查詢範圍？ ✓
 */
