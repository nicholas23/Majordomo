/**
 * Tests for ExecuteService.
 * Purpose: Verify the execution logic and status updates.
 * Key Items:
 * 1. Test successful execution via unified execute().
 * 2. Test process termination.
 * 3. Test failed execution.
 * Module: exec
 */
package com.github.nicholas23.majordomo.exec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.github.nicholas23.majordomo.history.History;
import com.github.nicholas23.majordomo.history.HistoryService;
import com.github.nicholas23.majordomo.history.HistoryStatus;
import com.github.nicholas23.majordomo.history.ResultTextType;
import com.github.nicholas23.majordomo.workspace.AgentCommandTaskRepository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 目的：測試 ExecuteService 的核心邏輯。
 * WHY: 測試統一的 execute() 方法，確保 History 建立、狀態更新、結果收集正確。
 */
@ExtendWith(MockitoExtension.class)
public class ExecuteServiceTest {

    @Mock
    private HistoryService historyService;
    @Mock
    private CommandExecutor commandExecutor;
    @Mock
    private AgentCommandTaskRepository agentCommandTaskRepository;

    private ExecutorService streamReaderExecutor;
    private ExecutorService workspaceCommandExecutor;
    private ExecuteService executeService;

    @BeforeEach
    void setUp() {
        streamReaderExecutor = Executors.newVirtualThreadPerTaskExecutor();
        workspaceCommandExecutor = Executors.newVirtualThreadPerTaskExecutor();
        executeService = new ExecuteService(historyService, streamReaderExecutor, workspaceCommandExecutor, commandExecutor, agentCommandTaskRepository);
    }



    @Test
    void testExecute_WhenCommandSucceeds_ReturnsCompleted() throws Exception {
        History history = createTestHistory(10L);

        when(historyService.createHistory(anyLong(), anyString())).thenReturn(history);

        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)));
        when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        when(mockProcess.waitFor()).thenReturn(0);

        when(commandExecutor.execute(any(), any())).thenReturn(mockProcess);

        // WHY: 直接測試同步的 execute() 方法，不受 executeAsync 影響
        ExecuteService.ExecutionResult result = executeService.execute(1L, new File("/tmp"), "test prompt","test command",true);

        assertEquals(10L, result.historyId());
        assertEquals(0, result.exitValue());
        assertEquals(HistoryStatus.COMPLETED, result.status());
        verify(historyService).updateHistoryStatus(eq(10L), eq(HistoryStatus.COMPLETED));
        verify(historyService).appendHistoryLog(eq(10L), eq(ResultTextType.STDOUT), eq("Hello\n"));
    }

    @Test
    void buildCommand_UsesAgyHeadlessJsonMode() {
        ReflectionTestUtils.setField(executeService, "includeDirs", "/tmp/one, /tmp/two");

        assertArrayEquals(new String[]{
                        "agy", "--agent", "majordomo", "--output-format", "json", "--continue",
                        "--dangerously-skip-permissions", "--add-dir", "/tmp/one", "--add-dir", "/tmp/two",
                        "--prompt", "hello"},
                executeService.buildCommand("hello", false));
    }

    @Test
    void testExecute_WhenExitValueNotZero_ReturnsFailed() throws Exception {
        History history = createTestHistory(50L);

        when(historyService.createHistory(anyLong(), anyString())).thenReturn(history);

        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)));
        when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream("ERR".getBytes(StandardCharsets.UTF_8)));
        when(mockProcess.waitFor()).thenReturn(1);
        when(commandExecutor.execute(any(), any())).thenReturn(mockProcess);

        ExecuteService.ExecutionResult result = executeService.execute(1L, new File("/tmp"), "test prompt","test command",true);

        assertEquals(50L, result.historyId());
        assertEquals(1, result.exitValue());
        assertEquals(HistoryStatus.FAILED, result.status());
        verify(historyService).updateHistoryStatus(eq(50L), eq(HistoryStatus.FAILED));
    }

    @Test
    void testExecute_WhenAgyReportsJsonError_ReturnsFailedEvenWithZeroExitCode() throws Exception {
        History history = createTestHistory(51L);
        when(historyService.createHistory(anyLong(), anyString())).thenReturn(history);
        when(historyService.getResultContent(51L, ResultTextType.STDOUT))
                .thenReturn("{\"status\":\"ERROR\",\"error\":\"rate limited\"}");

        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockProcess.waitFor()).thenReturn(0);
        when(commandExecutor.execute(any(), any())).thenReturn(mockProcess);

        ExecuteService.ExecutionResult result = executeService.execute(1L, new File("/tmp"), "test", "test", true);

        assertEquals(HistoryStatus.FAILED, result.status());
        verify(historyService).updateHistoryStatus(51L, HistoryStatus.FAILED);
    }

    @Test
    void testStopProcess_SuccessfullyTerminatesProcess() throws Exception {
        long historyId = 20L;
        History history = createTestHistory(historyId);
        Process mockProcess = mock(Process.class);

        CountDownLatch waitForStartedLatch = new CountDownLatch(1);
        CountDownLatch stopCalledLatch = new CountDownLatch(1);

        when(historyService.createHistory(anyLong(), anyString())).thenReturn(history);
        when(commandExecutor.execute(any(), any())).thenReturn(mockProcess);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        when(mockProcess.isAlive()).thenReturn(true);

        // WHY: 模擬 waitFor 會被阻塞，直到我們明確放行（代表 stopProcess 已呼叫）
        when(mockProcess.waitFor()).thenAnswer(invocation -> {
            waitForStartedLatch.countDown();
            stopCalledLatch.await(5, TimeUnit.SECONDS);
            return 143;
        });

        // Act: 在另一個執行緒呼叫 stopProcess
        CompletableFuture.runAsync(() -> {
            try {
                waitForStartedLatch.await(5, TimeUnit.SECONDS);
                executeService.stopProcess(historyId);
                stopCalledLatch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // WHY: 直接呼叫同步 execute() 以可靠地測試中止邏輯
        ExecuteService.ExecutionResult result = executeService.execute(1L, new File("/tmp"), "long-run","test command",true);

        // Assert
        verify(mockProcess).destroy();
        assertEquals(HistoryStatus.TERMINATED, result.status());
        verify(historyService, atLeastOnce()).updateHistoryStatus(eq(historyId), eq(HistoryStatus.TERMINATED));
    }

    private History createTestHistory(long id) {
        History h = new History();
        h.setId(id);
        h.setWorkspaceId(1L);
        h.setStatus(HistoryStatus.RUNNING);
        return h;
    }
}
/* ### Review Checklist ###
 * 1. Coverage: 核心 execute() 的 success/fail/terminate 均已覆蓋 ✓
 * 2. Isolation: 使用 Mock 隔離 HistoryService 和 CommandExecutor ✓
 * 3. 同步測試：直接測試同步 execute()，不受 executeAsync 影響 ✓
 */
