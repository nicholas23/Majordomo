/**
 * 目的：工作空間的業務邏輯層
 * 關鍵項目：
 * 1. 新增 Workspace 並初始化時間戳
 * 2. 分頁列出 Workspace
 * 3. 軟刪除 Workspace（設定 active = false）
 * 模組：workspace
 */
package com.github.nicholas23.majordomo.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;


@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * 目的：建立新的 Workspace。
     * 輸入：
     * - name: String - 工作區名稱
     * - description: String - 工作區描述
     * - absolutePath: String - 工作區絕對路徑
     * 輸出：Workspace - 新建立的物件
     * 限制：路徑必須存在且為目錄，否則拋出例外
     * 副作用：寫入資料庫
     */
    @Transactional
    public Workspace newWorkspace(String name, String description, String absolutePath) {
        log.info("[WorkspaceService] 建立 Workspace: name={}, path={}", name, absolutePath);
        
        // EDGE_CASE: 驗證路徑必須存在且為目錄
        validatePath(absolutePath);

        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setDescription(description);
        workspace.setAbsolutePath(absolutePath);
        workspace.setCreateAt(LocalDateTime.now());
        workspace.setUpdateAt(LocalDateTime.now());
        workspace.setActive(true);
        return workspaceRepository.save(workspace);
    }

    /**
     * 目的：更新 Workspace 資訊。
     * 輸入：
     * - id: long - Workspace ID
     * - name: String
     * - description: String
     * - absolutePath: String
     * - active: boolean
     * 輸出：無
     * 限制：ID 需存在，且若更改路徑，新路徑必須是有效目錄
     * 副作用：更新資料庫
     */
    @Transactional
    public void updateWorkspace(long id, String name, String description, String absolutePath, boolean active) {
        Workspace workspace = getWorkspace(id);
        
        // EDGE_CASE: 若路徑有變更，需重新驗證
        if (!workspace.getAbsolutePath().equals(absolutePath)) {
            validatePath(absolutePath);
        }

        workspace.setName(name);
        workspace.setDescription(description);
        workspace.setAbsolutePath(absolutePath);
        workspace.setActive(active);
        workspace.setUpdateAt(LocalDateTime.now());
        workspaceRepository.save(workspace);
        log.info("[WorkspaceService] 更新 Workspace: id={}, name={}, active={}", id, name, active);
    }

    /**
     * 目的：分頁列出所有 Workspace。
     * 輸入：
     * - page: int - 頁碼（0-based）
     * - size: int - 每頁筆數
     * 輸出：Page<Workspace>
     * 限制：page >= 0, size > 0
     * 副作用：無
     */
    public Page<Workspace> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        log.debug("[WorkspaceService] 列出 Workspace: page={}, size={}", page, size);
        return workspaceRepository.findAll(pageable);
    }

    /**
     * 目的：根據 ID 取得 Workspace，若不存在則拋出例外。
     * 輸入：
     * - workspaceId: long - 目標 ID
     * 輸出：Workspace
     * 限制：workspaceId 必須是有效的 Workspace ID，否則拋 NoSuchElementException
     * 副作用：無
     */
    public Workspace getWorkspace(long workspaceId) {
        // EDGE_CASE: ID 不存在時 orElseThrow 會拋出 NoSuchElementException
        return workspaceRepository.findById(workspaceId).orElseThrow();
    }

    /**
     * 目的：根據名稱查詢 Workspace。
     * 輸入：
     * - name: String - Workspace 名稱
     * 輸出：Optional<Workspace>
     * 限制：無
     * 副作用：無
     */
    public Optional<Workspace> getWorkspaceByName(String name) {
        log.debug("[WorkspaceService] 依名稱查詢 Workspace: name={}", name);
        return workspaceRepository.findByName(name);
    }

    /**
     * 目的：軟刪除 Workspace（將 active 設為 false）。
     * 輸入：
     * - workspaceId: long - 目標 ID
     * 輸出：無
     * 限制：workspaceId 必須是存在的 Workspace ID
     * 副作用：更新資料庫的 active 欄位為 false
     */
    @Transactional
    public void deleteWorkspace(long workspaceId) {
        // WHY: 使用軟刪除保留歷史紀錄的關聯完整性
        Workspace workspace = getWorkspace(workspaceId);
        workspace.setActive(false);
        workspace.setUpdateAt(LocalDateTime.now());
        workspaceRepository.save(workspace);
        log.info("[WorkspaceService] 軟刪除 Workspace: id={}, name={}", workspaceId, workspace.getName());
    }

    /**
     * 目的：驗證路徑是否存在且為目錄。
     * 輸入：
     * - absolutePath: String - 待驗證的絕對路徑
     * 輸出：無
     * 限制：路徑不存在或非目錄時拋出 IllegalArgumentException
     * 副作用：無
     */
    private void validatePath(String absolutePath) {
        File dir = new File(absolutePath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("無效的 Workspace 路徑或目錄不存在: " + absolutePath);
        }
    }

    /**
     * 目的：查詢所有啟用中的 Workspace。
     * 輸入：無
     * 輸出：List<Workspace>
     * 限制：無
     * 副作用：無
     */
    public List<Workspace> listAllActive() {
        return workspaceRepository.findByActiveTrue();
    }
}

/* ### Review Checklist ###
 * 1. 資料完整性：軟刪除保留歷史紀錄關聯？ ✓
 * 2. 邊界情況：getWorkspace 不存在時拋例外？ ✓
 * 3. 日誌：寫入操作有記錄？ ✓
 */
