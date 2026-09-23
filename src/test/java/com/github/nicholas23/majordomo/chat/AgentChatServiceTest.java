/**
 * 目的：測試 AgentChatService 的對話紀錄儲存與檢索功能
 * 關鍵項目：
 * 1. 驗證合法對話紀錄的儲存與修剪空白
 * 2. 驗證空訊息或空白字串略過儲存（防呆邊界）
 * 3. 驗證取得最近對話紀錄時依時間正序（由舊到新）排列
 * 模組：chat
 */
package com.github.nicholas23.majordomo.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceTest {

    @Mock
    private AgentChatHistoryRepository repository;

    private AgentChatService service;

    @BeforeEach
    void setUp() {
        service = new AgentChatService(repository);
    }

    @Test
    @DisplayName("正常儲存對話：應修剪空白並儲存至資料庫")
    void saveChat_ValidMessage_ShouldSaveTrimmedMessage() {
        // Arrange
        String role = "USER";
        String message = "  你好，請幫我檢查伺服器狀態  ";
        String source = "WEB";

        // Act
        service.saveChat(role, message, source);

        // Assert
        ArgumentCaptor<AgentChatHistory> captor = ArgumentCaptor.forClass(AgentChatHistory.class);
        verify(repository, times(1)).save(captor.capture());

        AgentChatHistory saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.getMessage()).isEqualTo("你好，請幫我檢查伺服器狀態");
        assertThat(saved.getSource()).isEqualTo("WEB");
        assertThat(saved.getCreateAt()).isNotNull();
    }

    @Test
    @DisplayName("空訊息或全空白訊息：應略過不進行持久化")
    void saveChat_NullOrBlankMessage_ShouldIgnore() {
        // EDGE_CASE: 空訊息防呆處理
        service.saveChat("USER", null, "WEB");
        service.saveChat("USER", "", "WEB");
        service.saveChat("USER", "   ", "WEB");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("取得最近對話：應將新到舊的查詢結果反轉為舊到新")
    void getRecentChats_ShouldReverseOrderToChronological() {
        // Arrange
        AgentChatHistory chat1 = new AgentChatHistory();
        chat1.setId(1L);
        chat1.setMessage("較舊的訊息");
        chat1.setCreateAt(LocalDateTime.now().minusMinutes(5));

        AgentChatHistory chat2 = new AgentChatHistory();
        chat2.setId(2L);
        chat2.setMessage("較新的訊息");
        chat2.setCreateAt(LocalDateTime.now().minusMinutes(1));

        // Repository 回傳 Desc（新到舊）
        List<AgentChatHistory> descList = new ArrayList<>(List.of(chat2, chat1));
        when(repository.findTop50ByOrderByCreateAtDesc()).thenReturn(descList);

        // Act
        List<AgentChatHistory> result = service.getRecentChats();

        // Assert
        // WHY: 聊天視窗需要由舊到新排序
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }
}

/* ### Review Checklist ###
 * 1. 邊界測試：驗證 null 和空白字串不觸發資料庫寫入 ✓
 * 2. 順序邏輯：驗證查詢結果正確反轉為舊到新 ✓
 * 3. Mock 隔離：使用 Mockito 確保單元測試不受外部環境干擾 ✓
 */
