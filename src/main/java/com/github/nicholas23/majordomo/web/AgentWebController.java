/**
 * 目的：Basic Agent 設定與資訊頁面的 Web 控制器
 * 關鍵項目：
 * 1. 提供 Basic Agent 初始化設定頁面（名字、使用者名稱、時區、回應方式）
 * 2. 委派 Initial 服務處理初始化，再透過 ExecuteService 執行初始化 prompt
 * 3. 提供 Basic Agent 資訊主頁面（不提供網頁對話）
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.nicholas23.majordomo.agent.HeartBeat;
import com.github.nicholas23.majordomo.agent.Initial;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.schedule.ScheduleService;

// REASONING: ExecuteService 的呼叫從 Initial 移至此處，
// 讓 Initial 專注於檔案/設定初始化，Controller 負責觸發 CLI 執行。
@Controller
public class AgentWebController {
    private static final Logger log = LoggerFactory.getLogger(AgentWebController.class);

    private final Initial initialService;
    private final ExecuteService executeService;
    private final ScheduleService scheduleService;

    public AgentWebController(Initial initialService,
                              ExecuteService executeService,
                              ScheduleService scheduleService) {
        this.initialService = initialService;
        this.executeService = executeService;
        this.scheduleService = scheduleService;
    }

    /**
     * 目的：顯示 Basic Agent 設定頁面。
     * 輸入：
     * - model: Model
     * 輸出：String - fragment 模板名稱或重導路徑
     * 限制：若已初始化，則會被重導至資訊頁
     * 副作用：無
     */
    @GetMapping("/web/agent/setup")
    public String setupPage(Model model) {
        if (initialService.isInitialized()) {
            return "redirect:/web/agent/info";
        }
        model.addAttribute("defaultPath", initialService.getBasePath().toString());
        model.addAttribute("defaultName", Initial.DEFAULT_NAME);
        model.addAttribute("defaultUserName", Initial.DEFAULT_USER_NAME);
        model.addAttribute("defaultTimezone", Initial.DEFAULT_TIMEZONE);
        model.addAttribute("defaultResponseStyle", Initial.DEFAULT_RESPONSE_STYLE);
        return "fragments/agent_setup";
    }

    /**
     * 目的：接收表單資料並建立/初始化 Basic Agent。
     * 輸入：
     * - agentName, userName, timezone, responseStyle, hobby, workDescription: String (均為選填)
     * - response: HttpServletResponse
     * 輸出：String - 重導路徑
     * 限制：無
     * 副作用：寫入預設設定檔 (.agent/initial.json 等)，觸發 CLI 執行，並註冊 Heartbeat 排程
     */
    @PostMapping("/web/agent/setup")
    public String createAgent(@RequestParam(required = false) String agentName,
                              @RequestParam(required = false) String userName,
                              @RequestParam(required = false) String timezone,
                              @RequestParam(required = false) String responseStyle,
                              @RequestParam(required = false) String hobby,
                              @RequestParam(required = false) String workDescription,
                              HttpServletResponse response) {
        log.info("[BasicAgentWebController] 初始化 Basic Agent: name={}", agentName);

        // WHY: init() 回傳初始化 prompt，由 Controller 負責觸發 CLI 執行
        String initPrompt = initialService.init(agentName, userName, timezone,
                responseStyle, hobby, workDescription);
        executeService.runWithBasicAgent(initialService.getBasePath().toFile(), initPrompt, true);

        scheduleService.registerHeartbeat(HeartBeat.DEFAULT_CRON);
        response.addHeader("HX-Redirect", "/web/agent/info");
        return "redirect:/web/agent/info";
    }

    /**
     * 目的：顯示 Basic Agent 資訊頁面。
     * 輸入：
     * - model: Model
     * 輸出：String - fragment 模板名稱或重導路徑
     * 限制：若尚未初始化，則會被重導至設定頁面
     * 副作用：無
     */
    @GetMapping("/web/agent/info")
    public String info(Model model) {
        if (!initialService.isInitialized()) {
            return "redirect:/web/agent/setup";
        }
        model.addAttribute("agentName", initialService.getAgentName());
        model.addAttribute("userName", initialService.getUserName());
        model.addAttribute("timezone", initialService.getTimezone());
        model.addAttribute("responseStyle", initialService.getResponseStyle());

        // WHY: Read HEARTBEAT.md and pass to frontend. Check if file is available first.
        String heartbeatContent = "查無 HEARTBEAT.md 檔案內容";
        java.nio.file.Path heartbeatFile = initialService.getBasePath().resolve("HEARTBEAT.md");
        if (java.nio.file.Files.exists(heartbeatFile)) {
            try {
                heartbeatContent = java.nio.file.Files.readString(heartbeatFile);
            } catch (java.io.IOException e) {
                log.error("[AgentWebController] 讀取 HEARTBEAT.md 失敗", e);
                heartbeatContent = "載入失敗，無法讀取檔案內容";
            }
        }
        model.addAttribute("heartbeatContent", heartbeatContent);

        return "fragments/agent_info";
    }
}

/* ### Review Checklist ###
 * 1. 職責分離：Controller 僅負責 HTTP 請求，將邏輯委派給 Initial / ExecuteService？ ✓
 * 2. HTMX 支援：建立成功後有正確設置 HX-Redirect Header？ ✓
 */
