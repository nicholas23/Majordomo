/**
 * 目的：排程任務的業務邏輯層
 * 關鍵項目：
 * 1. 應用程式啟動時初始化所有排程（一次性 + Cron）
 * 2. 使用 Spring TaskScheduler 動態註冊/取消排程任務
 * 3. 排程觸發後呼叫 ExecuteService 執行 Gemini CLI 指令
 * 4. Heartbeat 使用獨立的 registerHeartbeat() 方法，與排程系統分離
 * 5. 透過 ConcurrentHashMap 追蹤 ScheduledFuture 以支援動態取消
 * 模組：schedule
 */
package com.github.nicholas23.majordomo.schedule;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.nicholas23.majordomo.agent.HeartBeat;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.workspace.WorkspaceService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class ScheduleService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    // WHY: 追蹤已註冊的 ScheduledFuture，以便動態取消排程
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeFutures = new ConcurrentHashMap<>();

    // WHY: Heartbeat 使用固定 key，與一般排程的 ID 空間隔離
    private static final Long HEARTBEAT_FUTURE_KEY = -1L;

    private final TaskScheduler taskScheduler;
    private final ScheduleRepository scheduleRepository;
    private final ExecuteService executeService;
    private final WorkspaceService workspaceService;
    private final HeartBeat heartBeat;
    private final ZoneId systemZoneId = ZoneId.systemDefault();

    public ScheduleService(TaskScheduler taskScheduler, ScheduleRepository scheduleRepository,
                           ExecuteService executeService, WorkspaceService workspaceService,
                           HeartBeat heartBeat) {
        this.taskScheduler = taskScheduler;
        this.scheduleRepository = scheduleRepository;
        this.executeService = executeService;
        this.workspaceService = workspaceService;
        this.heartBeat = heartBeat;
    }

    /**
     * 目的：初始化所有排程任務（一次性 + Cron）。
     * REASONING: 只處理一般排程，Heartbeat 由外部呼叫 registerHeartbeat() 獨立啟動。
     * 輸入：無
     * 輸出：無
     * 限制：需在依賴注入完成後執行 (@PostConstruct)
     * 副作用：向 TaskScheduler 註冊排程任務
     */
    @PostConstruct
    public void initSchedules() {
        log.info("[ScheduleService] 開始初始化排程任務");
        registerHeartbeat(HeartBeat.DEFAULT_CRON);
        List<Schedule> enabledSchedules = scheduleRepository.findByEnabledTrue();
        for (Schedule schedule : enabledSchedules) {
            registerSchedule(schedule);
        }
        log.info("[ScheduleService] 排程任務初始化完成，共註冊 {} 筆", enabledSchedules.size());
    }

    // ==========================================
    // Heartbeat（獨立機制，不走 Schedule 表）
    // ==========================================

    /**
     * 目的：獨立註冊 Heartbeat 到 TaskScheduler。
     * REASONING: Heartbeat 不是 Schedule 表中的紀錄，它的生命週期由 BasicAgent 初始化後直接啟動，與一般排程完全分離。
     * 輸入：
     * - cron: String - 心跳的 Cron 表達式
     * 輸出：無
     * 限制：cron 不能為 null 或 blank，否則跳過註冊
     * 副作用：向 TaskScheduler 註冊心跳排程，並儲存 reference
     */
    public void registerHeartbeat(String cron) {
        unregisterHeartbeat();

        if (cron == null || cron.isBlank()) {
            log.warn("[ScheduleService] Heartbeat 缺少 Cron 表達式，跳過註冊");
            return;
        }

        Runnable task = () -> {
            try {
                log.info("[ScheduleService] 觸發 Heartbeat");
                heartBeat.process();
            } catch (Exception e) {
                log.error("[ScheduleService] Heartbeat 執行期間發生錯誤", e);
            }
        };

        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cron));
        activeFutures.put(HEARTBEAT_FUTURE_KEY, future);
        log.info("[ScheduleService] 已註冊 Heartbeat 排程: cron={}", cron);
    }

    /**
     * 目的：取消 Heartbeat 排程。
     * 輸入：無
     * 輸出：無
     * 限制：如果沒有活躍的 Heartbeat 則無作用
     * 副作用：取消 ScheduledFuture，從紀錄中移除
     */
    public void unregisterHeartbeat() {
        ScheduledFuture<?> future = activeFutures.remove(HEARTBEAT_FUTURE_KEY);
        if (future != null) {
            future.cancel(false);
            log.info("[ScheduleService] 已取消 Heartbeat 排程");
        }
    }

    // ==========================================
    // 一般排程（CRON_JOB / ONE_TIME）
    // ==========================================

    /**
     * 目的：將排程註冊到 TaskScheduler（僅處理 CRON_JOB 和 ONE_TIME）。
     * 輸入：
     * - schedule: Schedule - 排程資料
     * 輸出：無
     * 限制：排程類型必須合法，若為一次性則需為未來時間
     * 副作用：向 TaskScheduler 註冊任務，儲存 ScheduledFuture 到 activeFutures
     */
    public void registerSchedule(Schedule schedule) {
        // EDGE_CASE: 若排程已註冊，先取消再重新註冊
        unregisterSchedule(schedule.getId());

        Runnable task = () -> {
            try {
                log.info("[ScheduleService] 觸發排程任務: id={}, workspaceId={}", schedule.getId(), schedule.getWorkspaceId());
                executeService.run(workspaceService.getWorkspace(schedule.getWorkspaceId()), schedule.getCommand());
            } catch (Exception e) {
                log.error("[ScheduleService] 排程任務執行期間發生未預期錯誤: id={}", schedule.getId(), e);
            } finally {
                // WHY: 對於一次性任務，執行後主動移除 entry 並在 DB 中將 enabled 設為 false
                if (schedule.getType() == ScheduleType.ONE_TIME) {
                    activeFutures.remove(schedule.getId());
                    scheduleRepository.findById(schedule.getId()).ifPresent(s -> {
                        s.setEnabled(false);
                        scheduleRepository.save(s);
                        log.info("[ScheduleService] 一次性排程已執行完畢，更新資料庫狀態為停用: id={}", s.getId());
                    });
                    log.debug("[ScheduleService] 已自動移除一次性排程狀態: id={}", schedule.getId());
                }
            }
        };

        ScheduledFuture<?> future;
        if (schedule.getType() == ScheduleType.ONE_TIME) {
            if (schedule.getStartTime() != null && schedule.getStartTime().isAfter(LocalDateTime.now())) {
                Instant instant = schedule.getStartTime().atZone(systemZoneId).toInstant();
                future = taskScheduler.schedule(task, instant);
                log.debug("[ScheduleService] 註冊一次性排程: id={}, startTime={}", schedule.getId(), schedule.getStartTime());
            } else {
                log.debug("[ScheduleService] 跳過已過期的一次性排程: id={}", schedule.getId());
                return;
            }
        } else {
            // CRON_JOB
            if (schedule.getCron() == null || schedule.getCron().isBlank()) {
                log.warn("[ScheduleService] Cron 排程缺少 Cron 表達式: id={}", schedule.getId());
                return;
            }
            future = taskScheduler.schedule(task, new CronTrigger(schedule.getCron()));
            log.debug("[ScheduleService] 註冊 Cron 排程: id={}, cron={}", schedule.getId(), schedule.getCron());
        }

        activeFutures.put(schedule.getId(), future);
    }

    /**
     * 目的：從 TaskScheduler 取消已註冊的排程。
     * 輸入：
     * - scheduleId: long - 排程 ID
     * 輸出：無
     * 限制：若 scheduleId 找不到對應的排程，無作用
     * 副作用：取消 ScheduledFuture 並從 activeFutures 移除
     */
    public void unregisterSchedule(long scheduleId) {
        ScheduledFuture<?> future = activeFutures.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
            log.debug("[ScheduleService] 取消排程: id={}", scheduleId);
        }
    }

    /**
     * 目的：儲存排程到資料庫，若啟用則同時註冊到 TaskScheduler。
     * 輸入：
     * - schedule: Schedule - 排程資料
     * 輸出：Schedule - 已儲存的排程（含自動產生的 ID）
     * 限制：需在交易環境下執行
     * 副作用：寫入資料庫，可能註冊排程
     */
    @Transactional
    public Schedule saveAndActivate(Schedule schedule) {
        Schedule saved = scheduleRepository.save(schedule);
        log.info("[ScheduleService] 儲存排程: id={}, workspaceId={}, type={}", saved.getId(), saved.getWorkspaceId(), saved.getType());

        if (saved.isEnabled()) {
            registerSchedule(saved);
        } else {
            unregisterSchedule(saved.getId());
        }
        return saved;
    }

    /**
     * 目的：切換排程的啟用/停用狀態。
     * 輸入：
     * - id: long - 排程 ID
     * - enabled: boolean - 新狀態
     * 輸出：無
     * 限制：若資料庫內查無此 ID 則無作用。需在交易環境下執行
     * 副作用：更新資料庫，註冊或取消排程
     */
    @Transactional
    public void toggleEnabled(long id, boolean enabled) {
        scheduleRepository.findById(id).ifPresent(schedule -> {
            schedule.setEnabled(enabled);
            scheduleRepository.save(schedule);
            log.info("[ScheduleService] 切換排程狀態: id={}, enabled={}", id, enabled);

            if (enabled) {
                registerSchedule(schedule);
            } else {
                unregisterSchedule(id);
            }
        });
    }

    /**
     * 目的：查詢指定 Workspace 的所有排程任務。
     * 輸入：workspaceId: long
     * 輸出：List<Schedule>
     * 限制：無
     * 副作用：無
     */
    public List<Schedule> listByWorkspaceId(long workspaceId) {
        return scheduleRepository.findByWorkspaceId(workspaceId);
    }

    /**
     * 目的：根據 ID 查詢單一排程。
     * 輸入：id: long
     * 輸出：Optional<Schedule>
     * 限制：無
     * 副作用：無
     */
    public java.util.Optional<Schedule> getSchedule(long id) {
        return scheduleRepository.findById(id);
    }
}

/* ### Review Checklist ###
 * 1. 執行緒安全：ConcurrentHashMap 確保多執行緒安全？ ✓
 * 2. 職責分離：Heartbeat 與一般排程完全分離？ ✓
 * 3. Heartbeat key：使用 -1L 作為固定 key，不會與 DB 自增 ID 衝突？ ✓
 * 4. 資源管理：使用 Virtual Threads 避免執行緒耗盡？ ✓
 * 5. 狀態一致：toggleEnabled 同步更新 DB 及 TaskScheduler？ ✓
 * 6. 職責純粹：只管理 DB Schedule + TaskScheduler，不負責即時執行 ✓
 */
