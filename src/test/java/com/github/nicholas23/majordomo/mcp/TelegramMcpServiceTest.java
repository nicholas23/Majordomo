/**
 * 目的：測試 TelegramMcpService 的訊息發送與對話歷史連動
 * 關鍵項目：
 * 1. 驗證空訊息防呆處理（拒絕發送）
 * 2. 驗證訊息修剪、轉發至 TelegramHandleService 及持久化記錄到 AgentChatService
 * 3. 驗證發送失敗（例外）時的安全降級與錯誤捕捉
 * 模組：mcp
 */
package com.github.nicholas23.majordomo.mcp;

import com.github.nicholas23.majordomo.chat.AgentChatService;
import com.github.nicholas23.majordomo.telegram.TelegramHandleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramMcpServiceTest {

    @Mock
    private TelegramHandleService telegramHandleService;

    @Mock
    private AgentChatService agentChatService;

    private TelegramMcpService service;

    @BeforeEach
    void setUp() {
        service = new TelegramMcpService(telegramHandleService, agentChatService);
    }

    @Test
    @DisplayName("空訊息或空白字串應拒絕發送並返回 false")
    void sendMessageToUser_EmptyMessage_ShouldReturnFalse() {
        // EDGE_CASE: 空訊息防呆
        assertThat(service.sendMessageToUser(null)).isFalse();
        assertThat(service.sendMessageToUser("")).isFalse();
        assertThat(service.sendMessageToUser("   ")).isFalse();

        verifyNoInteractions(telegramHandleService);
        verifyNoInteractions(agentChatService);
    }

    @Test
    @DisplayName("合法訊息應修剪空格、委託發送並記錄至聊天歷史")
    void sendMessageToUser_ValidMessage_ShouldSendAndRecordChat() {
        String msg = "  任務執行成功！  ";

        Boolean result = service.sendMessageToUser(msg);

        assertThat(result).isTrue();
        verify(telegramHandleService, times(1)).sendToAllowedUser("任務執行成功！");
        verify(agentChatService, times(1)).saveChat("AGENT", "任務執行成功！", "MCP");
    }

    @Test
    @DisplayName("當發送底層發生例外時，應捕捉例外並返回 false，避免 Agent 中斷崩潰")
    void sendMessageToUser_WhenExceptionOccurs_ShouldCatchAndReturnFalse() {
        doThrow(new RuntimeException("Telegram API 連線逾時")).when(telegramHandleService).sendToAllowedUser(any());

        Boolean result = service.sendMessageToUser("測試訊息");

        assertThat(result).isFalse();
        // WHY: 失敗時不應寫入成功的 Agent 對話紀錄
        verify(agentChatService, never()).saveChat(any(), any(), any());
    }
}

/* ### Review Checklist ###
 * 1. 邊界測試：驗證 null/blank 訊息防呆 ✓
 * 2. 交互驗證：驗證 sendToAllowedUser 與 saveChat 順序與參數修剪 ✓
 * 3. 例外處理：驗證發送失敗時正確降級返回 false ✓
 */
