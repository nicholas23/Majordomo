/**
 * 目的：Basic Agent 心跳處理服務
 * 關鍵項目：
 * 1. 封裝心跳任務的完整執行邏輯
 * 2. 透過 HeartbeatPromptBuilder 產生專屬提示詞
 * 3. 委派 ExecuteService.runWithPrompt 執行 Gemini CLI
 * 模組：agent
 */
package com.github.nicholas23.majordomo.agent;

import com.github.nicholas23.majordomo.history.ResultTextType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.exec.AgyCliJsonOutputParser;
import com.github.nicholas23.majordomo.history.HistoryService;
import com.github.nicholas23.majordomo.workspace.AgentCommandTask;
import com.github.nicholas23.majordomo.workspace.AgentCommandTaskRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class HeartBeat {
    private static final Logger log = LoggerFactory.getLogger(HeartBeat.class);

    // WHY: 預設每 10 分鐘執行一次心跳（ZeroClaw 最小值為 5 分鐘，我們選擇較保守的間隔以節省 API 成本）
    public static final String DEFAULT_CRON = "0 */15 8-22 * * *";

    private final Initial initialService;
    private final ExecuteService executeService;
    private final AgentCommandTaskRepository agentCommandTaskRepository;
    private final AgentTodoRepository agentTodoRepository;
    private final HistoryService historyService;

    private LocalDateTime lastHeartBeatTime;

    public HeartBeat(Initial initialService, ExecuteService executeService,
                     AgentCommandTaskRepository agentCommandTaskRepository,
                     AgentTodoRepository agentTodoRepository,
                     HistoryService historyService) {
        this.initialService = initialService;
        this.executeService = executeService;
        this.agentCommandTaskRepository = agentCommandTaskRepository;
        this.agentTodoRepository = agentTodoRepository;
        this.historyService = historyService;
        lastHeartBeatTime = LocalDateTime.now().minusMinutes(10); // 初始化為 10 分鐘前，確保第一次執行能抓到最近的歷史紀錄
    }

    /**
     * 目的：執行心跳任務。
     * REASONING: ScheduleService 只需呼叫此方法，不需了解心跳的內部細節。
     * 從 Initial 的靜態 Workspace 物件取得 Agent 資訊（不查 DB）。
     * 輸入：無
     * 輸出：無
     * 限制：必須在 Initial 初始化完成後執行
     * 副作用：觸發 Gemini CLI 執行心跳任務
     */
    public void process() {

        if (!initialService.isInitialized()) {
            log.warn("[HeartBeat] 尚未設定 BasicAgent，跳過心跳任務");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        // 用時間判斷：如果是整點 0 分 (容許誤差5分鐘) 且是偶數小時，視為長心跳
        boolean isLongHeartbeat = (now.getMinute() < 5) && (now.getHour() % 2 == 0);

        List<AgentCommandTask> unreadTasks = agentCommandTaskRepository.findUnreadCompletedTasks();
        List<AgentTodo> pendingTodos = agentTodoRepository.findPendingTodosToTrigger(now);

        // 如果是短心跳，且沒有未讀任務也沒有到期的待辦事項，則跳過（節省 Token）
        if (!isLongHeartbeat && unreadTasks.isEmpty() && pendingTodos.isEmpty()) {
            log.info("[HeartBeat] 短心跳閘門：無待處理任務，跳過本回合執行。");
            lastHeartBeatTime = now;
            return;
        }

        log.info("[HeartBeat] 開始執行心跳任務 (模式: {})", isLongHeartbeat ? "長心跳" : "短心跳");

        if (!unreadTasks.isEmpty()) {
            log.info("[HeartBeat] 找到 {} 筆未讀指令結果，將注入給 Agent", unreadTasks.size());
        }
        if (!pendingTodos.isEmpty()) {
            log.info("[HeartBeat] 找到 {} 筆到期的待辦事項，將注入給 Agent", pendingTodos.size());
            // WHY: 注入前將狀態更新為 TRIGGERED，確保不會因滑動視窗偏差重複觸發或遺失
            for (AgentTodo todo : pendingTodos) {
                todo.setStatus("TRIGGERED");
                agentTodoRepository.save(todo);
            }
        }

        List<String> recentHistory = historyService.getWorkspaceHistories(lastHeartBeatTime);

        lastHeartBeatTime = now;
        String prompt = build(initialService.getAgentName(), unreadTasks, recentHistory, pendingTodos, isLongHeartbeat);
        executeService.runWithBasicAgent(initialService.getBasePath().toFile(), prompt);
    }

    /**
     * 目的：組裝 Heartbeat 任務的結構化提示詞。
     * 輸入：
     * - agentName: String - Agent 名稱
     * 輸出：String - 完整的結構化 Prompt
     * 限制：agentName 不能為 null
     * 副作用：無
     * REASONING: 依賴 GEMINI.md 定義行為規範，此處僅作為心跳觸發器。
     * Heartbeat 缺乏使用者的明確查詢上下文，因此不預先注入記憶，
     * 而是提示 Agent 善用 MCP 工具自主判斷是否需要回想過去資訊。
     */
    public String build(String agentName, boolean isLongHeartbeat) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String heartbeatType = isLongHeartbeat ? "【長心跳】(全面檢查)" : "【短心跳】(事件處理/待辦)";
        String extraInstruction = isLongHeartbeat ? 
            "因為這是長心跳，請務必讀取 HEARTBEAT.md 並執行其中的例行任務，全面檢查專案狀態。" : 
            "因為這是短心跳，請專注處理下面提供的事件或待辦事項，不需要全面檢查。如果沒事直接忽略即可。";

        return String.format("""
                %s。這是系統自動排程自動提醒的"重要的工作或事情"，當前心跳類型為：%s。(現在時間：%s)

                【指令】
                %s
                你可以隨時使用 MCP 記憶體工具（例如 recall）來回想背景資訊或過去的決策。
                
                【任務規劃與排程】
                如果你在檢查後認為需要安排後續的短期檢查或是未來任務，"強烈建議"你透過 `addAgentTodo` MCP 工具設定待辦事項，待心跳執行。
                注意：若未使用addAgentTodo`，會在兩小時後的長心跳才會執行。

                【回覆規則】
                如果處理完畢或判斷沒事，就不用特別通知了，直接簡短回應完成即可。
                """, agentName, heartbeatType, dtf.format(LocalDateTime.now()), extraInstruction);
    }

    /**
     * 目的：組裝帶有未讀指令結果的 Heartbeat 提示詞。
     * 輸入：
     * - agentName: String - Agent 名稱
     * - unreadTasks: List<AgentCommandTask> - 未讀的任務清單
     * - scheduledJobHistory: List<String> - 最近排程任務的歷史紀錄摘要列表
     * 輸出：String - 完整的結構化 Prompt
     * 限制：unreadTasks 若為 null 會自動轉換為空邏輯
     * 副作用：無
     */
    public String build(String agentName, List<AgentCommandTask> unreadTasks, List<String> scheduledJobHistory, List<AgentTodo> pendingTodos, boolean isLongHeartbeat) {
        StringBuilder prompt = new StringBuilder(build(agentName, isLongHeartbeat));

        if (unreadTasks != null && !unreadTasks.isEmpty()) {
            prompt.append("\n\n【未讀的非同步執行結果】\n");
            prompt.append("以下是你先前派發的指令執行結果。請根據這些結果決定後續動作，並 **務必** 使用 `acknowledgeCommandResult` MCP 工具標記這些 Task ID 為已讀。\n\n");

            for (AgentCommandTask task : unreadTasks) {
                prompt.append(String.format("--- 任務 ID: %d ---\n", task.getId()));
                prompt.append(String.format("工作區 ID: %d\n", task.getWorkspaceId()));
                prompt.append(String.format("發送指令: %s\n", safeText(task.getCommand())));
                prompt.append(String.format("執行狀態: %s\n", safeText(task.getStatus())));

                if (task.getHistoryId() != null) {
                    historyService.getHistory(task.getHistoryId()).ifPresent(history -> {
                        String output = historyService.getResultContent(history.getId(), ResultTextType.STDOUT);
                        if (output != null && !output.isBlank()) {
                            AgyCliJsonOutputParser.AgyOutput parsed = AgyCliJsonOutputParser.parseOutput(output);
                            if (parsed != null) {
                                if (parsed.getResponse() != null && !parsed.getResponse().isBlank()) {
                                    prompt.append("執行結果 (Response):\n").append(parsed.getResponse()).append("\n");
                                }
                                if (parsed.getError() != null && !parsed.getError().isBlank()) {
                                    prompt.append("執行失敗 (Failed):\n").append(parsed.getError()).append("\n");
                                }
                            }
                        }
                    });
                }
                prompt.append("-------------------\n\n");
            }
        }

        if (!scheduledJobHistory.isEmpty()){
            prompt.append("\n\n【最近的排程任務的歷史】\n");
            prompt.append("以下是最近的排程任務執行歷史紀錄，都還沒回覆給服務對象，可以整理一下內容，再回覆給服務對象：\n\n");
            for (String record : scheduledJobHistory) {
                prompt.append(record).append("\n----\n");
            }
            prompt.append("-------------------\n\n");
        }

        if (pendingTodos != null && !pendingTodos.isEmpty()) {
            prompt.append("\n\n【主 Agent 待辦事項 (AgentTodo)】\n");
            prompt.append("以下是你先前設定，到達或超過預定時間的待辦事項。請執行或檢查相應內容：\n\n");
            for (AgentTodo todo : pendingTodos) {
                prompt.append(String.format("- 預定時間: %s, 內容: %s\n", 
                    todo.getScheduledTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                    safeText(todo.getDescription())));
            }
            prompt.append("\n(這些待辦事項已作為喚醒你的條件，不需手動刪除，系統已將其標記為已觸發)\n-------------------\n\n");
        }

        return prompt.toString();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.trim();
    }
}

/* ### Review Checklist ###
 * 1. 邊界處理：初始化沒完成時是否有略過心跳？ ✓
 * 2. 結構：將外部 API 依賴隔絕在 executeService？ ✓
 * 3. 日誌：完整記錄開始與未讀任務？ ✓
 */
