/**
 * 目的：Telegram Bot 訊息處理服務
 * 關鍵項目：
 * 1. 初始化 Telegram Bot 並監聽訊息
 * 2. 所有訊息直接轉發給 BasicAgent 處理
 * 3. Agent 透過 MCP Tool (sendMessageToUser) 回覆使用者
 * 4. 僅允許白名單內的使用者操作
 * 5. 改為從 TelegramSettingsService 取得加密設定
 * 模組：telegram
 */
package com.github.nicholas23.majordomo.telegram;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.github.nicholas23.majordomo.agent.AgentPromptBuilder;
import com.github.nicholas23.majordomo.agent.Initial;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.chat.AgentChatService;
import com.github.nicholas23.majordomo.memory.KeywordExtractor;
import com.github.nicholas23.majordomo.mcp.MemoryMcpService;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Telegram Bot 訊息處理：監聽、分派、回覆。
 */
@Service
public class TelegramHandleService {

    private static final Logger log = LoggerFactory.getLogger(TelegramHandleService.class);

    private final ExecuteService executeService;
    private final Initial initialService;
    private final MemoryMcpService memoryMcpService;
    private final AgentChatService agentChatService;
    private final TelegramSettingsService settingsService;

    private TelegramClient bot;
    private TelegramBotsLongPollingApplication botsApplication;

    public TelegramHandleService(ExecuteService executeService, Initial initialService,
                                 MemoryMcpService memoryMcpService, AgentChatService agentChatService,
                                 TelegramSettingsService settingsService) {
        this.executeService = executeService;
        this.initialService = initialService;
        this.memoryMcpService = memoryMcpService;
        this.agentChatService = agentChatService;
        this.settingsService = settingsService;
    }

    /**
     * 目的：初始化 Telegram Bot，設定訊息監聽器。
     * 輸入：無
     * 輸出：無
     * 限制：需在依賴注入完成後執行 (@PostConstruct)
     * 副作用：建立 TelegramBot 實例並開始監聽更新
     */
    @PostConstruct
    public void init() {
        initializeBot();
    }

    /**
     * 目的：重新初始化 Telegram Bot 連線。
     * WHY: 使用者透過 Web Settings 更新設定後，需要重建 Bot 連線。
     * 輸入：無
     * 輸出：無
     * 限制：無
     * 副作用：關閉舊 Bot、建立新 Bot 實例
     */
    public void reinitialize() {
        // REASONING: 先關閉舊連線避免資源洩漏
        if (bot != null) {
            bot = null;
            log.info("[TelegramHandleService] 已關閉舊的 Telegram Bot 連線");
        }
        if (botsApplication != null) {
            try {
                botsApplication.close();
                log.info("[TelegramHandleService] 已關閉舊的 Telegram Long Polling 連線");
            } catch (Exception e) {
                log.error("[TelegramHandleService] 關閉舊的 Telegram Long Polling 連線時發生錯誤", e);
            }
            botsApplication = null;
        }
        initializeBot();
    }

    /**
     * 目的：實際建立 Bot 連線的內部方法。
     * 輸入：無
     * 輸出：無
     * 限制：若未設定 Bot Token，則不啟用服務
     * 副作用：建立 TelegramBot 實例並設定監聽器
     */
    private void initializeBot() {
        if (!settingsService.isConfigured()) {
            log.warn("[TelegramHandleService] Telegram 設定未完成, 服務將不啟用");
            return;
        }

        String botToken = settingsService.getBotToken();
        if (botToken.isEmpty()) {
            log.warn("[TelegramHandleService] Bot Token 為空, Telegram 服務將不啟用");
            return;
        }
        bot = new OkHttpTelegramClient(botToken);
        
        try {
            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, (LongPollingSingleThreadUpdateConsumer) this::processUpdate);
            log.info("[TelegramHandleService] Telegram Bot 已成功註冊並開始監聽");
        } catch (Exception e) {
            log.error("[TelegramHandleService] Telegram Bot 註冊失敗", e);
        }
    }

    /**
     * 目的：處理單一 Telegram 更新訊息。
     * WHY: 移除所有互動式選單與指令處理，所有訊息一律轉發給 BasicAgent。
     * 輸入：
     * - update: Update - Telegram 更新物件
     * 輸出：無
     * 限制：非文字訊息或非白名單用戶的訊息將被忽略
     * 副作用：紀錄聊天訊息、觸發 Agent 執行及訊息回覆
     */
    private void processUpdate(Update update) {
        Message message = update.getMessage();
        // EDGE_CASE: 非文字訊息（圖片、貼圖、CallbackQuery 等）直接忽略
        if (message == null || message.getText() == null) {
            return;
        }

        Long userId = message.getFrom().getId();
        String allowedUser = settingsService.getAllowedUser();
        // WHY: 安全性考量，僅白名單用戶可操作系統
        if (allowedUser.isEmpty() || !allowedUser.equals(String.valueOf(userId))) {
            log.warn("[TelegramHandleService] 收到未授權用戶訊息, User ID: {}", userId);
            return;
        }

        Long chatId = message.getChatId();
        String text = message.getText();

        log.info("[TelegramHandleService] 收到訊息: {}, User ID: {}", text, userId);

        // 記錄使用者訊息
        agentChatService.saveChat("USER", text, "TELEGRAM");

        // WHY: 所有訊息預設轉發給 BasicAgent 處理，提供類似 AI 助理的對話體驗
        handleBasicAgentMessage(chatId, text);
    }

    /**
     * 目的：將訊息轉發給 BasicAgent 處理。
     * REASONING: 讓 Telegram 成為使用者與 AI Agent 的主要對話介面。
     * 輸入：
     * - chatId: Long - 目標聊天 ID
     * - userMessage: String - 使用者訊息內容
     * 輸出：無
     * 限制：若系統尚未初始化，將回覆提示訊息
     * 副作用：查詢 BasicAgent Workspace 並觸發 ExecuteService
     */
    private void handleBasicAgentMessage(Long chatId, String userMessage) {
        if (!initialService.isInitialized()) {
            sendMessage(chatId, """
                    ⚠️ 尚未設定 Basic Agent。
                    請先至 Web UI 完成 Basic Agent 初始化。
                    """);
            return;
        }
        String agentName = initialService.getAgentName();
        log.info("[TelegramHandleService] 轉發訊息至 BasicAgent: workspace={}, message={}", agentName, userMessage);

        // WHY: 使用 Keyword RAG 從記憶庫中檢索相關上下文，注入 Agent Prompt
        List<String> keywords = KeywordExtractor.extract(userMessage);
        List<String> memories = memoryMcpService.recallByKeywords(keywords, 5);
        log.debug("[TelegramHandleService] RAG 檢索結果: keywords={}, memories={}", keywords, memories.size());
        String prompt = AgentPromptBuilder.build(initialService.getUserName(), userMessage, memories);
        try {
            // WHY: Agent 會透過 MCP Tool (sendMessageToUser) 自己回覆使用者，不需要處理回傳值
            executeService.runWithBasicAgent(initialService.getBasePath().toFile(), prompt);
        } catch (RuntimeException e) {
            log.error("[TelegramHandleService] BasicAgent 執行失敗: workspace={}", agentName, e);
            sendMessage(chatId, "❌ %s 執行失敗，請稍後重試。".formatted(agentName));
        }
    }

    /**
     * 目的：透過 Telegram Bot API 發送訊息。
     * 輸入：
     * - chatId: long - 目標聊天 ID
     * - text: String - 訊息內容
     * 輸出：無
     * 限制：若 Bot 未初始化則無法發送
     * 副作用：透過網路發送 Telegram 訊息
     */
    public void sendMessage(long chatId, String text) {
        // EDGE_CASE: Bot 可能未初始化（Token 未設定時）
        if (bot == null) {
            log.warn("[TelegramHandleService] Bot 未初始化,無法發送訊息");
            return;
        }

        try {
            SendMessage request = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build();
            bot.execute(request);
            log.debug("[TelegramHandleService] 訊息已發送,Chat ID: {}", chatId);
        } catch (Exception e) {
            log.error("[TelegramHandleService] 發送訊息失敗: chatId={}", chatId, e);
        }
    }

    /**
     * 目的：向 allowedUser 發送訊息（供 MCP Tool 使用）。
     * WHY: 封裝 allowedUser 的解析與發送邏輯，讓 TelegramMcpService 不需知道 chatId。
     * 輸入：
     * - text: String - 訊息內容
     * 輸出：無
     * 限制：若 allowedUser 未正確設定則不發送
     * 副作用：透過 Telegram 發送訊息
     */
    public void sendToAllowedUser(String text) {
        String allowedUser = settingsService.getAllowedUser();
        if (allowedUser.isEmpty() || bot == null) {
            log.warn("[TelegramHandleService] 無法發送訊息：Bot 或 allowedUser 未設定");
            return;
        }

        long chatId;
        try {
            chatId = Long.parseLong(allowedUser);
        } catch (NumberFormatException e) {
            log.warn("[TelegramHandleService] allowedUser 格式錯誤: {}", allowedUser);
            return;
        }
        sendMessage(chatId, text);
    }

    /**
     * 目的：在應用程式關閉前，優雅地關閉 Telegram Bot 連線。
     */
    @PreDestroy
    public void destroy() {
        if (botsApplication != null) {
            try {
                botsApplication.close();
                log.info("[TelegramHandleService] 應用程式關閉，已關閉 Telegram Long Polling 連線");
            } catch (Exception e) {
                log.error("[TelegramHandleService] 關閉 Telegram Long Polling 連線時發生錯誤", e);
            }
        }
    }
}

/* ### Review Checklist ###
 * 1. 安全性：白名單驗證防止未授權存取？ ✓
 * 2. 邊界情況：Bot Token 為空時優雅降級？ ✓
 * 3. 錯誤處理：Agent 執行 catch Exception 有記錄 log？ ✓
 * 4. 職責分離：Agent 回覆透過 MCP Tool，不再直接轉發 ✓
 * 5. 設定來源：改用 TelegramSettingsService 取代 @Value ✓
 * 6. 重新連線：reinitialize() 可安全重建 Bot ✓
 */
