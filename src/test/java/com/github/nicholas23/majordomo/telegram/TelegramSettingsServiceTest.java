/**
 * 目的：TelegramSettingsService 單元測試
 * 關鍵項目：
 * 1. 驗證加密 → 解密 round-trip 正確性
 * 2. 驗證 isConfigured 狀態判斷
 * 3. 驗證遮罩顯示邏輯
 * 模組：telegram
 */
package com.github.nicholas23.majordomo.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.nicholas23.majordomo.agent.Initial;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TelegramSettingsService 的單元測試。
 */
class TelegramSettingsServiceTest {

    @TempDir
    Path tempDir;

    private TelegramSettingsService service;

    @BeforeEach
    void setUp() {
        Initial mockInitial = mock(Initial.class);
        when(mockInitial.getBasePath()).thenReturn(tempDir);
        service = new TelegramSettingsService(mockInitial);
    }

    @Test
    void isConfiguredReturnsFalseWhenNoFile() {
        // REASONING: 初始狀態無檔案，應回傳 false
        assertFalse(service.isConfigured());
    }

    @Test
    void saveAndReadBackRoundTrip() {
        // REASONING: 加密儲存後應能正確解密還原
        String token = "1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ_0123456";
        String user = "123456789";

        service.save(token, user);

        assertTrue(service.isConfigured());
        assertEquals(token, service.getBotToken());
        assertEquals(user, service.getAllowedUser());
    }

    @Test
    void getMaskedBotTokenWhenNotConfigured() {
        assertEquals("(未設定)", service.getMaskedBotToken());
    }

    @Test
    void getMaskedBotTokenShowsPartialValue() {
        service.save("1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ_0123456", "123456789");

        String masked = service.getMaskedBotToken();
        assertTrue(masked.startsWith("1234"));
        assertTrue(masked.endsWith("****"));
        // WHY: 不應暴露完整 Token
        assertFalse(masked.contains("ABCde"));
    }

    @Test
    void getBotTokenReturnsEmptyWhenNotConfigured() {
        assertEquals("", service.getBotToken());
    }

    @Test
    void getAllowedUserReturnsEmptyWhenNotConfigured() {
        assertEquals("", service.getAllowedUser());
    }

    @Test
    void overwriteExistingSettings() {
        // REASONING: 重新儲存應覆蓋舊值
        service.save("oldToken", "oldUser");
        service.save("newToken", "newUser");

        assertEquals("newToken", service.getBotToken());
        assertEquals("newUser", service.getAllowedUser());
    }
}
