/**
 * 目的：Workspace 的 MCP 工具服務
 * 關鍵項目：
 * 1. 供 Agent 查詢工作區列表與執行歷史記錄
 * 2. 供 Agent 非同步在工作區執行指令 (寫入 AgentCommandTask)
 * 3. 供 Agent 設定/管理排程
 * 4. 供 Agent 標記心跳注入的任務已讀
 * 模組：workspace
 */
package com.github.nicholas23.majordomo.mcp;

import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.exec.GeminiCliJsonOutputParser;
import com.github.nicholas23.majordomo.history.History;
import com.github.nicholas23.majordomo.history.HistoryService;
import com.github.nicholas23.majordomo.history.ResultTextType;
import com.github.nicholas23.majordomo.schedule.Schedule;
import com.github.nicholas23.majordomo.schedule.ScheduleService;
import com.github.nicholas23.majordomo.schedule.ScheduleType;
import com.github.nicholas23.majordomo.workspace.AgentCommandTask;
import com.github.nicholas23.majordomo.workspace.AgentCommandTaskRepository;
import com.github.nicholas23.majordomo.workspace.Workspace;
import com.github.nicholas23.majordomo.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkspaceMcpService {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceMcpService.class);

    private final WorkspaceService workspaceService;
    private final HistoryService historyService;
    private final ScheduleService scheduleService;
    private final ExecuteService executeService;
    private final AgentCommandTaskRepository agentCommandTaskRepository;

    public WorkspaceMcpService(WorkspaceService workspaceService, HistoryService historyService,
                               ScheduleService scheduleService, ExecuteService executeService,
                               AgentCommandTaskRepository agentCommandTaskRepository) {
        this.workspaceService = workspaceService;
        this.historyService = historyService;
        this.scheduleService = scheduleService;
        this.executeService = executeService;
        this.agentCommandTaskRepository = agentCommandTaskRepository;
    }

    /**
     * 目的：列出目前系統中啟用中的工作區 (workspaces)。
     * 輸入：無
     * 輸出：String - 格式化後的工作區列表字串，包含 ID, 名稱, 與描述
     * 限制：無
     * 副作用：無
     */
    @Tool(description = "列出目前系統中啟用中的工作區 (workspaces)。此列表包含 ID, 名稱, 與描述。")
    public String listWorkspaces() {
        long startNs = System.nanoTime();
        List<Workspace> activeWorkspaces = workspaceService.listAllActive();

        if (activeWorkspaces.isEmpty()) {
            return "目前沒有啟用中的工作區。";
        }

        String result = activeWorkspaces.stream()
                .map(w -> String.format("ID: %d, 名稱: %s, 描述: %s", w.getId(), w.getName(), w.getDescription()))
                .collect(Collectors.joining("\n"));

        log.info("[WorkspaceMcpService] listWorkspaces 找到 {} 個, elapsed={}ms",
                activeWorkspaces.size(), (System.nanoTime() - startNs) / 1_000_000);
        return result;
    }

    /**
     * 目的：檢視特定工作區的執行歷史紀錄大綱。
     * 輸入：
     * - workspaceId: long - 目標工作區 ID
     * 輸出：String - 格式化後的歷史執行紀錄字串
     * 限制：最多回傳最近 5 次的紀錄
     * 副作用：無
     */
    @Tool(description = "檢視特定工作區的執行歷史紀錄大綱 (最近 5 次)。請提供 workspaceId。")
    public String getWorkspaceHistory(long workspaceId) {
        long startNs = System.nanoTime();
        List<History> histories = historyService.listHistory(workspaceId, PageRequest.of(0, 5)).getContent();

        if (histories.isEmpty()) {
            return "該工作區無歷史執行紀錄。";
        }

        // 取最近 5 筆
        String result = histories.stream()
                .limit(5)
                .map(h -> {
                    String outputStr = "[無結果]";
                    String raw = historyService.getResultContent(h.getId(), ResultTextType.STDOUT);
                    if (StringUtils.hasText(raw)) {
                        GeminiCliJsonOutputParser.GeminiCLiJsonResponse parsed = GeminiCliJsonOutputParser.parser(raw);
                        if (parsed != null && parsed.getResponse() != null) {
                            outputStr = parsed.getResponse();
                        } else {
                            outputStr = raw.length() > 500 ? raw.substring(0, 500) + "..." : raw;
                        }
                    }
                    return String.format("History ID: %d, 時間: %s, 狀態: %s\n指令: %s\n結果:\n%s",
                            h.getId(), h.getStartTime(), h.getStatus(), h.getCommand(), outputStr);
                })
                .collect(Collectors.joining("\n\n"));

        log.info("[WorkspaceMcpService] getWorkspaceHistory(workspaceId={}) 成功, elapsed={}ms",
                workspaceId, (System.nanoTime() - startNs) / 1_000_000);
        return result;
    }

    /**
     * 目的：在指定的工作區非同步執行 Gemini CLI 指令。
     * 輸入：
     * - workspaceId: long - 目標工作區 ID
     * - command: String - 欲執行的指令
     * 輸出：String - 指令排入背景執行的結果提示訊息
     * 限制：指定的 workspace 必須存在且為啟用狀態
     * 副作用：建立 AgentCommandTask，觸發背景背景執行 (ExecuteService.runAsyncFromAgent)
     */
    @Tool(description = "在指定的工作區執行指令，這個指令會由另一個AI Model在背景執行，所以指令要**簡潔扼要**。指令不會立即完成，執行完成後，系統會放在 Heartbeat 的待處理清單中把結果提供給你。請提供 workspaceId 與 command（指令) (例如: 計算..., 編輯...，等，在專案裡做的事)。")
    @Transactional
    public String executeCommandOnWorkspace(long workspaceId, String command) {
        long startNs = System.nanoTime();

        Workspace w = workspaceService.getWorkspace(workspaceId);
        if (w == null || !Boolean.TRUE.equals(w.getActive())) {
            return "錯誤: 找不到 ID 為 " + workspaceId + " 的工作區，或該工作區已停用。";
        }

        // 建立 Agent Task 紀錄
        AgentCommandTask task = new AgentCommandTask();
        task.setWorkspaceId(workspaceId);
        task.setCommand(command);
        task.setStatus("PENDING");
        task.setRead(false);
        task.setCreateAt(LocalDateTime.now());
        task.setUpdateAt(LocalDateTime.now());
        task = agentCommandTaskRepository.save(task);

        // 背景觸發執行 (由 ExecuteService 非同步處理)
        log.info("AgentCommandTask(taskId={}) 開始背景執行: workspace={}, command={}", task.getId(), w.getName(), command);
        executeService.runAsyncFromAgent(task.getId(), w, command);

        log.info("[WorkspaceMcpService] executeCommandOnWorkspace: taskId={}, workspaceId={}, elapsed={}ms",
                task.getId(), workspaceId, (System.nanoTime() - startNs) / 1_000_000);

        return String.format("指令已進入背景執行佇列 (Task ID: %d)。執行過程可能需要數分鐘，執行完成後會透過 Heartbeat 通知你。你可以進行其他事情。", task.getId());
    }

    /**
     * 目的：標記透過 Heartbeat 收到的非同步指令執行結果為『已讀』。
     * 輸入：
     * - taskId: long - AgentCommandTask 的 ID
     * 輸出：String - 標記成功或失敗的訊息
     * 限制：taskId 對應的紀錄必須存在
     * 副作用：更新 AgentCommandTask 的 isRead 狀態與更新時間
     */
    @Tool(description = "標記透過 Heartbeat 收到的非同步指令執行結果為『已讀』。這表示你已經看到了結果，系統將不會再重複推送該任務結果給你。請填寫你在 Heartbeat 訊息中看到的 Task ID。")
    @Transactional
    public String acknowledgeCommandResult(long taskId) {
        long startNs = System.nanoTime();
        Optional<AgentCommandTask> optTask = agentCommandTaskRepository.findById(taskId);

        if (optTask.isEmpty()) {
            return "錯誤: 找不到 Task ID: " + taskId;
        }

        AgentCommandTask task = optTask.get();
        if (task.isRead()) {
            return "Task ID " + taskId + " 已經標記為已讀了。";
        }

        task.setRead(true);
        task.setUpdateAt(LocalDateTime.now());
        agentCommandTaskRepository.save(task);

        log.info("[WorkspaceMcpService] acknowledgeCommandResult: taskId={}, elapsed={}ms",
                taskId, (System.nanoTime() - startNs) / 1_000_000);

        return "成功: Task ID " + taskId + " 已標記為已讀。";
    }

    /**
     * 目的：為特定工作區設定排程任務。
     * 輸入：
     * - workspaceId: long - 目標工作區 ID
     * - command: String - 欲執行的指令
     * - scheduleType: String - 排程種類 ('ONE_TIME' 或 'CRON_JOB')
     * - timeExpression: String - 觸發時間表達式 (ISO 時間或 Cron 表達式)
     * 輸出：String - 排程建立結果的提示訊息
     * 限制：指定的 workspace 必須存在且為啟用狀態，scheduleType 與 timeExpression 必須為有效格式
     * 副作用：儲存 Schedule 資料模型至資料庫並啟動排程 (scheduleService.saveAndActivate)
     */
    @Tool(description = "為特定工作區設定排程任務。scheduleType 必須是 'ONE_TIME' 或 'CRON_JOB'。如果是 ONE_TIME，請在 timeExpression 提供 ISO 時間 (例如 2024-12-31T23:59:00)。如果是 CRON_JOB，請在 timeExpression 提供 Cron Expression (例如 '0 0 * * * *')。")
    @Transactional
    public String scheduleCommandOnWorkspace(long workspaceId, String command, String scheduleType, String timeExpression) {
        long startNs = System.nanoTime();

        Workspace w = workspaceService.getWorkspace(workspaceId);
        if (w == null || !Boolean.TRUE.equals(w.getActive())) {
            return "錯誤: 找不到 ID 為 " + workspaceId + " 的工作區，或該工作區已停用。";
        }

        Schedule schedule = new Schedule();
        schedule.setWorkspaceId(workspaceId);
        schedule.setCommand(command);
        schedule.setEnabled(true);

        try {
            ScheduleType type = ScheduleType.valueOf(scheduleType.toUpperCase());
            schedule.setType(type);

            if (type == ScheduleType.ONE_TIME) {
                schedule.setStartTime(LocalDateTime.parse(timeExpression, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                schedule.setCron(timeExpression);
            }
        } catch (IllegalArgumentException e) {
            return "錯誤: 無效的 scheduleType。請使用 ONE_TIME 或 CRON_JOB。";
        } catch (DateTimeParseException e) {
            return "錯誤: ONE_TIME 的時間格式無效，請使用 ISO 格式如 '2024-12-31T23:59:00'。";
        } catch (Exception e) {
            return "錯誤: 建立排程失敗 - " + e.getMessage();
        }

        Schedule saved = scheduleService.saveAndActivate(schedule);

        log.info("[WorkspaceMcpService] scheduleCommandOnWorkspace: scheduleId={}, elapsed={}ms",
                saved.getId(), (System.nanoTime() - startNs) / 1_000_000);

        return String.format("成功建立排程！Schedule ID: %d, 類型: %s", saved.getId(), saved.getType());
    }

    /**
     * 目的：啟用或停用已存在的排程任務。
     * 輸入：
     * - scheduleId: long - 排程任務的 ID
     * - enabled: boolean - true 啟用, false 停用
     * 輸出：String - 狀態切換結果的提示訊息
     * 限制：指定的 scheduleId 必須存在
     * 副作用：更新 Schedule 狀態與重新觸發/取消排程服務 (scheduleService.toggleEnabled)
     */
    @Tool(description = "啟用或停用已存在的排程任務。提供 scheduleId 以及 enabled (true 啟用, false 停用)。")
    public String toggleWorkspaceSchedule(long scheduleId, boolean enabled) {
        long startNs = System.nanoTime();
        try {
            scheduleService.toggleEnabled(scheduleId, enabled);
            log.info("[WorkspaceMcpService] toggleWorkspaceSchedule: scheduleId={}, enabled={}, elapsed={}ms",
                    scheduleId, enabled, (System.nanoTime() - startNs) / 1_000_000);
            return "成功: Schedule ID " + scheduleId + " 狀態已更新為 " + (enabled ? "啟用" : "停用") + "。";
        } catch (Exception e) {
            return "錯誤: 切換排程狀態失敗 - " + e.getMessage();
        }
    }
}

/* ### Review Checklist ###
 * 1. 錯誤處理：輸入驗證（如 workspace 不存在、時間格式錯誤） ✓
 * 2. 異步支援：使用 runAsyncFromAgent 將負載拋到背景 ✓
 * 3. 狀態管理：清楚建立 PENDING 狀態等待執行 ✓
 * 4. Slf4j 紀錄：包含經過時間以便利效能追蹤 ✓
 */
