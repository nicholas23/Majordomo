/**
 * 目的：提供 AgentCommandTask 的基本 CRUD 功能
 * 關鍵項目：
 * 1. 繼承 CrudRepository
 * 2. 定義查詢方法：尋找未讀取且已完成狀態的任務 (Heartbeat 用)
 * 模組：workspace
 */
package com.github.nicholas23.majordomo.workspace;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentCommandTaskRepository extends CrudRepository<AgentCommandTask, Long> {

    /**
     * 目的：尋找未讀取且已完成狀態的任務。
     * WHY: 供 Heartbeat 搜尋未讀的結束任務 (COMPLETED 或 FAILED)。
     * 輸入：無
     * 輸出：List<AgentCommandTask> - 未讀且已結束的任務列表
     * 限制：無
     * 副作用：無
     */
    @Query("SELECT * FROM agent_command_task WHERE is_read = false AND status IN ('COMPLETED', 'FAILED') ORDER BY id ASC")
    List<AgentCommandTask> findUnreadCompletedTasks();
}

/* ### Review Checklist ###
 * 1. 查詢邏輯：已包含 COMPLETED 和 FAILED 狀態？ ✓
 * 2. 查詢效能：是否有適當的索引支持 is_read 與 status 查詢？ 
 */
