/**
 * 目的：Telegram MCP 服務層 — 提供 Agent 與使用者溝通的 MCP 工具
 * 關鍵項目：
 * 1. sendMessageToUser：Agent 透過此工具向使用者發送訊息
 * 2. getChatHistory：Agent 取得近期聊天記錄以保持對話連續性
 * 3. 所有透過 MCP 發送的訊息自動記錄至 AgentChatHistory
 * 模組：telegram
 */
package com.github.nicholas23.majordomo.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import com.github.nicholas23.majordomo.chat.AgentChatService;
import com.github.nicholas23.majordomo.telegram.TelegramHandleService;

@Service
public class TelegramMcpService {

    private static final Logger log = LoggerFactory.getLogger(TelegramMcpService.class);

    private final TelegramHandleService telegramHandleService;
    private final AgentChatService agentChatService;

    public TelegramMcpService(TelegramHandleService telegramHandleService,
                              AgentChatService agentChatService) {
        this.telegramHandleService = telegramHandleService;
        this.agentChatService = agentChatService;
    }

    /**
     * 目的：Agent 透過此 MCP 工具向使用者發送訊息。
     * WHY: 讓 Agent 主動控制回覆時機與內容，取代過去系統自動轉發的機制。
     * 輸入：
     * - message: String - 要發送的訊息內容
     * 輸出：Boolean - 是否發送成功
     * 限制：message 不可為空
     * 副作用：透過 Telegram 發送訊息、寫入 AgentChatHistory
     */
    @Tool(description = "傳送訊息給你的服務對象：當你需要回覆使用者、通知使用者任何資訊時，必須使用此工具。"
            + "這是你與使用者溝通的唯一管道。")
    public Boolean sendMessageToUser(String message) {
        long startNs = System.nanoTime();

        // EDGE_CASE: 空訊息不發送
        if (message == null || message.isBlank()) {
            log.warn("[TelegramMcpService] sendMessageToUser 呼叫失敗：message 為空");
            return false;
        }

        try {
            // WHY: 透過 TelegramHandleService 的底層 sendMessage 方法發送
            telegramHandleService.sendToAllowedUser(message.trim());
            // 記錄 Agent 回覆到聊天歷史
            agentChatService.saveChat("AGENT", message.trim(), "MCP");

            log.info("[TelegramMcpService] 已發送訊息給使用者, length={}, elapsed={}ms",
                    message.length(), (System.nanoTime() - startNs) / 1_000_000);
            return true;
        } catch (Exception e) {
            log.error("[TelegramMcpService] 發送訊息失敗: {}", e.getMessage(), e);
            return false;
        }
    }
}

/* ### Review Checklist ###
 * 1. 安全性：訊息長度未做上限限制（依賴 Telegram API 自身限制 4096 字元）
 * 2. 事務保護：saveChat 已在 AgentChatService 層標註 @Transactional ✓
 * 3. 邊界處理：空訊息拒絕發送 ✓
 * 4. 日誌記錄：所有操作均有記錄 ✓
 * 5. 職責分離：MCP 工具方法 vs 底層發送方法明確區隔 ✓
 */
