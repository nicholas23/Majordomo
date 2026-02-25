/**
 * 目的：Workspace 管理頁面的 Web 控制器
 * 關鍵項目：
 * 1. 首頁顯示所有 Workspace 列表
 * 2. 提供單一 Workspace 詳情的 HTMX 片段
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.workspace.Workspace;
import com.github.nicholas23.majordomo.workspace.WorkspaceService;

@Controller
public class WorkspaceWebController {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceWebController.class);

    // Default Page Size
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final WorkspaceService workspaceService;
    private final com.github.nicholas23.majordomo.agent.Initial initialService;
    private final ExecuteService executeService;

    public WorkspaceWebController(WorkspaceService workspaceService,
            com.github.nicholas23.majordomo.agent.Initial initialService,
            ExecuteService executeService) {
        this.workspaceService = workspaceService;
        this.initialService = initialService;
        this.executeService = executeService;
    }

    /**
     * 目的：首頁，根據 BasicAgent 狀態決定頁面行為。
     * REASONING: 若已設定 BasicAgent，自動帶入其資訊頁；否則顯示工作區列表。
     * 輸入：
     * - model: Model - Spring MVC 模型
     * 輸出：String - 模板名稱或重導路徑
     * 限制：無
     * 副作用：無
     */
    @GetMapping("/")
    public String index(Model model) {
        log.debug("[WorkspaceWebController] 載入首頁");

        // WHY: 當 BasicAgent 已設定時，首頁直接導向 Agent 資訊頁
        if (initialService.isInitialized()) {
            model.addAttribute("agentName", initialService.getAgentName());
            model.addAttribute("userName", initialService.getUserName());
            model.addAttribute("timezone", initialService.getTimezone());
            model.addAttribute("hasBasicAgent", true);
        } else {
            model.addAttribute("hasBasicAgent", false);
        }

        Page<Workspace> workspaces = workspaceService.list(0, DEFAULT_PAGE_SIZE);
        // WHY: getContent() 回傳 UnmodifiableList，SpEL 在 Native Image 下無法解析其方法
        model.addAttribute("workspaces", new ArrayList<>(workspaces.getContent()));
        return "main";
    }

    /**
     * 目的：取得 Workspace 列表片段（供 Ajax 更新）。
     * 輸入：
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：無
     * 副作用：無
     */
    @GetMapping("/web/workspace/list")
    public String getWorkspaceList(Model model) {
        log.debug("[WorkspaceWebController] 載入 Workspace 列表片段");
        Page<Workspace> workspaces = workspaceService.list(0, DEFAULT_PAGE_SIZE);
        // WHY: getContent() 回傳 UnmodifiableList，SpEL 在 Native Image 下無法解析其方法
        model.addAttribute("workspaces", new ArrayList<>(workspaces.getContent()));
        return "fragments/workspace_list :: list";
    }

    /**
     * 目的：取得單一 Workspace 的詳情片段（供 HTMX 動態載入）。
     * 輸入：
     * - id: Long - Workspace ID
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：若 id 不存在將拋出例外
     * 副作用：無
     */
    @GetMapping("/web/workspace/{id}")
    public String getWorkspaceDetail(@PathVariable Long id, Model model) {
        log.debug("[WorkspaceWebController] 載入 Workspace 詳情: id={}", id);
        Workspace workspace = workspaceService.getWorkspace(id);
        model.addAttribute("workspace", workspace);
        return "fragments/workspace_detail";
    }

    /**
     * 目的：顯示建立新 Workspace 的表單片段。
     * 輸入：
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：無
     * 副作用：無
     */
    @GetMapping("/web/workspace/new")
    public String newWorkspaceForm(Model model) {
        return "fragments/workspace_new";
    }

    /**
     * 目的：處理建立新 Workspace 的請求。
     * 輸入：
     * - name: String - 工作區名稱
     * - description: String - 描述
     * - absolutePath: String - 絕對路徑
     * - model: Model
     * - response: HttpServletResponse
     * 輸出：String - 成功建立後的詳情片段
     * 限制：absolutePath 長度驗證由 Service 層處理
     * 副作用：寫入資料庫, 設定 HX-Trigger Header
     */
    @PostMapping("/web/workspace")
    public String createWorkspace(@RequestParam String name,
            @RequestParam String description,
            @RequestParam String absolutePath,
            Model model,
            HttpServletResponse response) {
        log.info("[WorkspaceWebController] 建立 Workspace: name={}, path={}", name, absolutePath);
        Workspace created = workspaceService.newWorkspace(name, description, absolutePath);

        // HTMX Trigger to refresh sidebar
        response.addHeader("HX-Trigger", "newWorkspaceCreated");

        model.addAttribute("workspace", created);
        return "fragments/workspace_detail";
    }

    /**
     * 目的：更新 Workspace 資訊。
     * 輸入：
     * - id: Long - 目標 ID
     * - name: String
     * - description: String
     * - absolutePath: String
     * - active: boolean
     * - activeToTelegram: boolean
     * - model: Model
     * 輸出：String - 更新後的詳情片段
     * 限制：若資料庫內查無此 ID 則拋出例外
     * 副作用：更新資料庫
     */
    @PostMapping("/web/workspace/{id}")
    public String updateWorkspace(@PathVariable Long id,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam String absolutePath,
            @RequestParam(defaultValue = "false") boolean active,
            @RequestParam(defaultValue = "false") boolean activeToTelegram,
            Model model) {

        log.info("[WorkspaceWebController] 更新 Workspace: id={}, name={}, active={}, telegram={}", id, name, active,
                activeToTelegram);

        // 需更新 Service 介面以支援 active 參數
        workspaceService.updateWorkspace(id, name, description, absolutePath, active, activeToTelegram);

        // 更新後重新載入詳情
        Workspace updated = workspaceService.getWorkspace(id);
        model.addAttribute("workspace", updated);
        return "fragments/workspace_detail";
    }

    /**
     * 目的：立即執行指定 Workspace 的指令。
     * WHY: 從 ScheduleWebController 遷移至此，讓 ScheduleService 只負責管理 DB Schedule。
     * 輸入：
     * - workspaceId: Long
     * - command: String - 執行指令
     * 輸出：ResponseEntity<Void> - 204 No Content
     * 限制：若 workspaceId 無效則拋出例外
     * 副作用：透過 ExecuteService 非同步執行指令
     */
    @PostMapping("/web/workspace/{workspaceId}/execute_now")
    public ResponseEntity<Void> executeNow(@PathVariable Long workspaceId, @RequestParam String command) {
        log.info("[WorkspaceWebController] 立即執行: workspaceId={}", workspaceId);
        // WHY: executeService.run() 內部已使用 workspaceCommandExecutor 非同步執行
        executeService.run(workspaceService.getWorkspace(workspaceId), command);
        return ResponseEntity.noContent().build();
    }
}

/*
 * ### Review Checklist ###
 * 1. 安全性：路徑參數 id 需驗證有效性（目前由 Service 層拋例外處理）
 * 2. 職責分離：executeNow 從 ScheduleWebController 遷移至此 ✓
 */
