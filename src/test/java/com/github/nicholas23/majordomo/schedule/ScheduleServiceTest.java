package com.github.nicholas23.majordomo.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import com.github.nicholas23.majordomo.agent.HeartBeat;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.workspace.Workspace;
import com.github.nicholas23.majordomo.workspace.WorkspaceService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 目的：測試 ScheduleService 的動態排程管理與資源回收機制。
 */
@ExtendWith(MockitoExtension.class)
public class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ExecuteService executeService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private ScheduledFuture<?> mockFuture;
    @Mock
    private HeartBeat heartBeat;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(taskScheduler, scheduleRepository, executeService, workspaceService, heartBeat);
    }

    @Test
    void testRegisterSchedule_WithCronJob_ShouldCallScheduler() {
        // Arrange
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setType(ScheduleType.CRON_JOB);
        schedule.setCron("0 0 * * * *");
        schedule.setEnabled(true);

        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn((ScheduledFuture) mockFuture);

        // Act
        scheduleService.registerSchedule(schedule);

        // Assert
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void testRegisterSchedule_WithExpiredOneTimeJob_ShouldNotCallScheduler() {
        // Arrange
        Schedule schedule = new Schedule();
        schedule.setId(2L);
        schedule.setType(ScheduleType.ONE_TIME);
        schedule.setStartTime(LocalDateTime.now().minusDays(1)); // Expired

        // Act
        scheduleService.registerSchedule(schedule);

        // Assert
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void testUnregisterSchedule_ShouldCancelFuture() {
        // Arrange
        long id = 3L;
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setType(ScheduleType.CRON_JOB);
        schedule.setCron("0 0 * * * *");
        
        doReturn(mockFuture).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        
        // 註冊以填充 activeFutures
        scheduleService.registerSchedule(schedule);

        // Act
        scheduleService.unregisterSchedule(id);

        // Assert
        verify(mockFuture).cancel(false);
    }

    @Test
    void testToggleEnabled_WhenTurningOff_ShouldCancelFutureInMap() {
        // Arrange
        long id = 10L;
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setType(ScheduleType.CRON_JOB);
        schedule.setCron("0 0 * * * *");
        schedule.setEnabled(true);

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(schedule));
        doReturn(mockFuture).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        // 先註冊
        scheduleService.registerSchedule(schedule);

        // Act
        scheduleService.toggleEnabled(id, false);

        // Assert
        assertFalse(schedule.isEnabled());
        verify(mockFuture).cancel(false);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void testOneTimeTask_Execution_ShouldCallExecuteServiceAndCleanup() {
        // Arrange
        long id = 5L;
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setType(ScheduleType.ONE_TIME);
        schedule.setStartTime(LocalDateTime.now().plusHours(1));
        schedule.setWorkspaceId(100L);
        schedule.setCommand("test command");

        Workspace ws = new Workspace();
        ws.setId(100L);
        when(workspaceService.getWorkspace(100L)).thenReturn(ws);

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(schedule));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(runnableCaptor.capture(), any(Instant.class))).thenReturn((ScheduledFuture) mockFuture);

        // Act 1: 註冊任務
        scheduleService.registerSchedule(schedule);
        
        // Act 2: 執行截獲的 Runnable (模擬 Scheduler 觸發)
        runnableCaptor.getValue().run();

        // Assert
        verify(executeService).run(ws, "test command");
        assertFalse(schedule.isEnabled());
        verify(scheduleRepository).save(schedule);

        // 雖然 activeFutures 是 private，但我們可以透過行為驗證後續能否解除註冊（若 entry 消失，unregister 將無效）
        scheduleService.unregisterSchedule(id);
        verifyNoMoreInteractions(mockFuture); // 因為 Map 中已無此 Future
    }

    @Test
    void testListByWorkspaceId_ShouldCallRepository() {
        // Arrange
        when(scheduleRepository.findByWorkspaceId(10L)).thenReturn(java.util.Collections.emptyList());

        // Act
        scheduleService.listByWorkspaceId(10L);

        // Assert
        verify(scheduleRepository).findByWorkspaceId(10L);
    }

    @Test
    void testGetSchedule_ShouldCallRepository() {
        // Arrange
        Schedule s = new Schedule();
        s.setId(10L);
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(s));

        // Act
        Optional<Schedule> result = scheduleService.getSchedule(10L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
    }
}
