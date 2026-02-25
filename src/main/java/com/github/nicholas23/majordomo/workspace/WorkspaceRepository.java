/**
 * 目的：工作空間的資料存取層（使用 Spring Data JDBC CrudRepository）
 * 關鍵項目：
 * 1. 繼承 CrudRepository 提供基本 CRUD 操作
 * 2. 提供分頁查詢及按名稱查詢
 * 模組：workspace
 */
package com.github.nicholas23.majordomo.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// REASONING: 使用 Spring Data JDBC 的 CrudRepository 簡化資料存取邏輯
@Repository
public interface WorkspaceRepository extends CrudRepository<Workspace, Long> {

    /**
     * 目的：分頁查詢所有 Workspace。
     * 輸入：
     * - pageable: Pageable - 分頁參數
     * 輸出：Page<Workspace>
     * 限制：無
     * 副作用：無
     */
    Page<Workspace> findAll(Pageable pageable);

    /**
     * 目的：根據名稱查詢 Workspace。
     * 輸入：
     * - name: String - Workspace 名稱
     * 輸出：Optional<Workspace>
     * 限制：無
     * 副作用：無
     */
    Optional<Workspace> findByName(String name);

    /**
     * 目的：查詢所有啟用 Telegram 功能的 Workspace。
     * 輸入：無
     * 輸出：List<Workspace>
     * 限制：無
     * 副作用：無
     */
    List<Workspace> findByActiveTrue();

}

/* ### Review Checklist ###
 * 1. Spring Data JDBC 查詢方法名稱是否符合規範？ ✓
 * 2. Optional 回傳值宣告是否正確？ ✓
 */
