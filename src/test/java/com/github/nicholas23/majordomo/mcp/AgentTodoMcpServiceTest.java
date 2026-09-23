/**
 * 目的：測試 AgentTodoMcpService 的時間解析與待辦儲存邏輯
 * 關鍵項目：
 * 1. 驗證合法時間格式能成功儲存
 * 2. 驗證非法時間格式返回友善錯誤提示
 * 3. 驗證未預期例外時返回安全錯誤訊息
 * 模組：mcp
 */
package com.github.nicholas23.majordomo.mcp;

import com.github.nicholas23.majordomo.agent.AgentTodo;
import com.github.nicholas23.majordomo.agent.AgentTodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentTodoMcpServiceTest {

    @Mock
    private AgentTodoRepository agentTodoRepository;

    private AgentTodoMcpService service;

    @BeforeEach
    void setUp() {
        service = new AgentTodoMcpService(agentTodoRepository);
    }

    /**
     * 目的：驗證合法時間格式下，待辦事項能正確儲存並返回成功訊息。
     */
    @Test
    void addAgentTodo_ValidDateTime_ShouldSaveAndReturnSuccess() {
        // Arrange
        String description = "檢查發票狀態";
        String scheduledTime = "2026-09-06 15:30:00";

        AgentTodo saved = new AgentTodo();
        saved.setId(99L);
        saved.setDescription(description);
        saved.setScheduledTime(LocalDateTime.of(2026, 9, 6, 15, 30, 0));
        saved.setCreatedAt(LocalDateTime.now());

        when(agentTodoRepository.save(any(AgentTodo.class))).thenReturn(saved);

        // Act
        String result = service.addAgentTodo(description, scheduledTime);

        // Assert
        assertTrue(result.contains("已成功加入待辦事項"));
        assertTrue(result.contains("ID=99"));
        assertTrue(result.contains(scheduledTime));

        ArgumentCaptor<AgentTodo> captor = ArgumentCaptor.forClass(AgentTodo.class);
        verify(agentTodoRepository).save(captor.capture());
        AgentTodo captured = captor.getValue();
        assertEquals(description, captured.getDescription());
        assertEquals(LocalDateTime.of(2026, 9, 6, 15, 30, 0), captured.getScheduledTime());
    }

    /**
     * 目的：驗證非法時間格式輸入時，服務不應拋出例外，而是返回友善錯誤訊息。
     */
    @Test
    void addAgentTodo_InvalidDateTimeFormat_ShouldReturnErrorMessage() {
        // Arrange
        String description = "測試錯誤時間";
        String invalidTime = "2026/09/06 15:30"; // 格式不符 yyyy-MM-dd HH:mm:ss

        // Act
        String result = service.addAgentTodo(description, invalidTime);

        // Assert
        assertTrue(result.startsWith("錯誤: 時間格式無法解析"));
        verify(agentTodoRepository, never()).save(any());
    }

    /**
     * 目的：驗證資料庫儲存拋出異常時，能安全捕捉並回傳錯誤訊息。
     */
    @Test
    void addAgentTodo_RepositoryException_ShouldReturnSafeError() {
        // Arrange
        when(agentTodoRepository.save(any(AgentTodo.class))).thenThrow(new RuntimeException("DB Connection Timeout"));

        // Act
        String result = service.addAgentTodo("待辦內容", "2026-09-06 18:00:00");

        // Assert
        assertTrue(result.startsWith("錯誤: 未知錯誤 - DB Connection Timeout"));
    }
}

/* ### Review Checklist ###
 * 1. 測試邊界：合法時間與非法時間均涵蓋？ ✓
 * 2. 例外測試：資料庫異常時不會中斷呼叫端？ ✓
 * 3. 隔離性：使用 Mockito 隔絕資料庫依賴？ ✓
 */
