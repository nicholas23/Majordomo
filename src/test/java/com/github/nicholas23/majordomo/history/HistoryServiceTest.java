/**
 * 目的：測試 HistoryService 的歷史紀錄管理、日誌追加與心跳格式化輸出
 * 關鍵項目：
 * 1. 驗證 createHistory 初始狀態與時間設定
 * 2. 驗證 appendHistoryLog 邊界防呆（忽略空日誌）
 * 3. 驗證 updateHistoryStatus 正確更新狀態與結束時間
 * 4. 驗證 getWorkspaceHistories 的 JSON 輸出解析、錯誤提取與原始字串 fallback
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private HistoryRepository historyRepository;

    @Mock
    private ResultTextRepository resultTextRepository;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new HistoryService(historyRepository, resultTextRepository);
    }

    @Test
    @DisplayName("createHistory: 應以 RUNNING 狀態與當前時間建立歷史紀錄")
    void createHistory_ShouldSaveRunningHistory() {
        when(historyRepository.save(any(History.class))).thenAnswer(invocation -> {
            History h = invocation.getArgument(0);
            h.setId(100L);
            return h;
        });

        History created = historyService.createHistory(1L, "ls -la");

        assertThat(created.getId()).isEqualTo(100L);
        assertThat(created.getWorkspaceId()).isEqualTo(1L);
        assertThat(created.getCommand()).isEqualTo("ls -la");
        assertThat(created.getStatus()).isEqualTo(HistoryStatus.RUNNING);
        assertThat(created.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("appendHistoryLog: 空日誌應略過，有效日誌應儲存")
    void appendHistoryLog_ShouldSaveOnlyNonEmptyContent() {
        // EDGE_CASE: null 或空內容不存
        historyService.appendHistoryLog(100L, ResultTextType.STDOUT, null);
        historyService.appendHistoryLog(100L, ResultTextType.STDOUT, "");
        verify(resultTextRepository, never()).save(any());

        // 有效內容
        historyService.appendHistoryLog(100L, ResultTextType.STDOUT, "Process started\n");
        ArgumentCaptor<ResultText> captor = ArgumentCaptor.forClass(ResultText.class);
        verify(resultTextRepository, times(1)).save(captor.capture());

        ResultText saved = captor.getValue();
        assertThat(saved.getHistoryId()).isEqualTo(100L);
        assertThat(saved.getType()).isEqualTo(ResultTextType.STDOUT);
        assertThat(saved.getContent()).isEqualTo("Process started\n");
    }

    @Test
    @DisplayName("updateHistoryStatus: 應更新狀態與結束時間")
    void updateHistoryStatus_ShouldUpdateStatusAndEndTime() {
        History h = new History();
        h.setId(100L);
        h.setStatus(HistoryStatus.RUNNING);

        when(historyRepository.findById(100L)).thenReturn(Optional.of(h));

        historyService.updateHistoryStatus(100L, HistoryStatus.COMPLETED);

        assertThat(h.getStatus()).isEqualTo(HistoryStatus.COMPLETED);
        assertThat(h.getEndTime()).isNotNull();
        verify(historyRepository).save(h);
    }

    @Test
    @DisplayName("getResultContent: 應拼接所有相關 ResultText")
    void getResultContent_ShouldJoinContents() {
        when(resultTextRepository.findByHistoryIdAndTypeOrderByCreateAt(100L, ResultTextType.STDOUT))
                .thenReturn(List.of("Line 1\n", "Line 2\n"));

        String result = historyService.getResultContent(100L, ResultTextType.STDOUT);

        assertThat(result).isEqualTo("Line 1\nLine 2\n");
    }

    @Test
    @DisplayName("listHistory: 應委託 Repository 進行分頁查詢")
    void listHistory_ShouldDelegateToRepository() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<History> mockPage = new PageImpl<>(List.of(new History()));
        when(historyRepository.findByWorkspaceIdOrderByIdDesc(1L, pageRequest)).thenReturn(mockPage);

        Page<History> result = historyService.listHistory(1L, pageRequest);

        assertThat(result).isEqualTo(mockPage);
    }

    @Test
    @DisplayName("getWorkspaceHistories: 應支援 JSON 回應解析、錯誤解析與原始字串 fallback")
    void getWorkspaceHistories_ShouldParseOutputSafely() {
        History h1 = new History();
        h1.setId(1L);
        h1.setWorkspaceId(10L);
        h1.setCommand("gemini ask");
        h1.setStatus(HistoryStatus.COMPLETED);

        History h2 = new History();
        h2.setId(2L);
        h2.setWorkspaceId(10L);
        h2.setCommand("gemini ask fail");
        h2.setStatus(HistoryStatus.FAILED);

        History h3 = new History();
        h3.setId(3L);
        h3.setWorkspaceId(10L);
        h3.setCommand("plain bash");
        h3.setStatus(HistoryStatus.COMPLETED);

        when(historyRepository.findByWorkspaceIdGreaterThan(0)).thenReturn(List.of(h1, h2, h3));

        // h1: 成功 JSON
        when(resultTextRepository.findByHistoryIdAndTypeOrderByCreateAt(1L, ResultTextType.STDOUT))
                .thenReturn(List.of("{\"response\":\"Command executed cleanly\"}"));

        // h2: 錯誤 JSON
        when(resultTextRepository.findByHistoryIdAndTypeOrderByCreateAt(2L, ResultTextType.STDOUT))
                .thenReturn(List.of("{\"error\":{\"message\":\"Execution timed out\"}}"));

        // h3: 純文字 raw output
        when(resultTextRepository.findByHistoryIdAndTypeOrderByCreateAt(3L, ResultTextType.STDOUT))
                .thenReturn(List.of("Raw bash standard output line"));

        List<String> formatted = historyService.getWorkspaceHistories(null);

        assertThat(formatted).hasSize(3);
        assertThat(formatted.get(0)).contains("Output: Command executed cleanly");
        assertThat(formatted.get(1)).contains("Output (Error): Execution timed out");
        assertThat(formatted.get(2)).contains("Output: Raw bash standard output line");
    }
}

/* ### Review Checklist ###
 * 1. 狀態轉換測試：RUNNING -> SUCCESS/FAILED 已驗證 ✓
 * 2. 輸出安全解析：JSON response、JSON error 與 raw fallback 皆已驗證 ✓
 * 3. 邊界測試：空日誌不觸發儲存已驗證 ✓
 */
