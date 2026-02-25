/**
 * 目的：排程管理頁面的 Web 控制器
 * 關鍵項目：
 * 1. 提供 Workspace 的排程列表 HTMX 片段
 * 2. 提供排程編輯器 HTMX 片段（新增/編輯）
 * 3. 儲存排程（新增/更新）
 * 4. 切換排程啟用/停用狀態
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.nicholas23.majordomo.schedule.Schedule;
import com.github.nicholas23.majordomo.schedule.ScheduleRepository;
import com.github.nicholas23.majordomo.schedule.ScheduleService;
import com.github.nicholas23.majordomo.schedule.ScheduleType;

@Controller
public class ScheduleWebController {
    private static final Logger log = LoggerFactory.getLogger(ScheduleWebController.class);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;

    public ScheduleWebController(ScheduleRepository scheduleRepository, ScheduleService scheduleService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleService = scheduleService;
    }

    /**
     * 目的：取得指定 Workspace 的排程列表片段。
     * 輸入：
     * - id: Long - Workspace ID
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：無
     * 副作用：無
     */
    @GetMapping("/web/workspace/{id}/schedule")
    public String getScheduleTab(@PathVariable Long id, Model model) {
        log.debug("[ScheduleWebController] 載入排程列表: workspaceId={}", id);
        model.addAttribute("workspaceId", id);
        model.addAttribute("schedules", scheduleRepository.findByWorkspaceId(id));
        return "fragments/schedule_tab";
    }

    /**
     * 目的：取得排程編輯器片段（新增或編輯）。
     * 輸入：
     * - scheduleId: Long - 排程 ID（0 表示新增）
     * - workspaceId: Long (選填)
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：若 scheduleId 找不到對應排程則回傳包含空 schedule 的模板
     * 副作用：無
     */
    @GetMapping("/web/schedule/{scheduleId}/edit")
    public String getScheduleEditor(@PathVariable Long scheduleId,
                                    @RequestParam(required = false) Long workspaceId,
                                    Model model) {
        log.debug("[ScheduleWebController] 載入排程編輯器: scheduleId={}", scheduleId);
        // REASONING: scheduleId == 0 表示新增排程，使用空白 Schedule 物件
        if (scheduleId == 0) {
            Schedule newSchedule = new Schedule();
            if (workspaceId != null) {
                newSchedule.setWorkspaceId(workspaceId);
            }
            newSchedule.setEnabled(true);
            model.addAttribute("schedule", newSchedule);
        } else {
            scheduleRepository.findById(scheduleId).ifPresent(s -> model.addAttribute("schedule", s));
        }
        model.addAttribute("types", ScheduleType.values());
        return "fragments/schedule_editor";
    }

    /**
     * 目的：儲存排程（新增或更新）。
     * 輸入：
     * - workspaceId: Long - 關聯的工作區 ID
     * - schedule: Schedule - 表單提交的排程資料
     * - model: Model
     * 輸出：String - 重導到排程列表片段
     * 限制：需確保傳入的資料通過基本驗證
     * 副作用：寫入資料庫，可能註冊排程到 TaskScheduler
     */
    @PostMapping("/web/workspace/{workspaceId}/schedule")
    public String saveSchedule(@PathVariable Long workspaceId, Schedule schedule, Model model) {
        schedule.setWorkspaceId(workspaceId);
        log.info("[ScheduleWebController] 儲存排程: workspaceId={}, type={}", workspaceId, schedule.getType());
        scheduleService.saveAndActivate(schedule);

        // WHY: 儲存後回傳更新的排程列表，供 HTMX 替換
        model.addAttribute("workspaceId", workspaceId);
        model.addAttribute("schedules", scheduleRepository.findByWorkspaceId(workspaceId));
        return "fragments/schedule_tab";
    }



    /**
     * 目的：切換排程的啟用/停用狀態。
     * 輸入：
     * - id: Long - 排程 ID
     * - enabled: boolean - 新狀態
     * - workspaceId: Long
     * - model: Model
     * 輸出：String - 重導到排程列表片段
     * 限制：若 id 不存在則無作用
     * 副作用：更新資料庫，註冊或取消排程
     */
    @PutMapping("/web/schedule/{id}/toggle")
    public String toggleSchedule(@PathVariable Long id,
                                 @RequestParam boolean enabled,
                                 @RequestParam Long workspaceId,
                                 Model model) {
        log.info("[ScheduleWebController] 切換排程狀態: id={}, enabled={}", id, enabled);
        scheduleService.toggleEnabled(id, enabled);

        model.addAttribute("workspaceId", workspaceId);
        model.addAttribute("schedules", scheduleRepository.findByWorkspaceId(workspaceId));
        return "fragments/schedule_tab";
    }
}

/* ### Review Checklist ###
 * 1. 架構：Controller 透過 ScheduleService 操作？ ✓
 * 2. 邊界情況：scheduleId 不存在時回傳空的 editor？ ✓（ifPresent）
 * 3. 安全性：路徑參數未做權限驗證
 */
