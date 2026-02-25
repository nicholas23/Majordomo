/**
 * 目的：處理 Agent 的對話紀錄服務
 * 關鍵項目：
 * 1. 儲存來自 Web UI 或 Telegram 的使用者訊息與 Agent 訊息
 * 2. 檢索歷史對話
 * 模組：history
 */
package com.github.nicholas23.majordomo.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentChatService {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    private final AgentChatHistoryRepository repository;

    public AgentChatService(AgentChatHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 目的：儲存一筆對話紀錄
     * 輸入：
     * - role: String - 'USER' 或 'AGENT'
     * - message: String - 訊息內容
     * - source: String - 'TELEGRAM' 或 'WEB'
     * 輸出：無
     * 限制：role 與 source 須為有效識別字元，message 不可為 null 或空白
     * 副作用：將對話紀錄寫入資料庫
     */
    @Transactional
    public void saveChat(String role, String message, String source) {
        // EDGE_CASE: 使用者可能送出空訊息或只有空白的訊息
        if (message == null || message.isBlank()) {
            return;
        }
        
        AgentChatHistory chat = new AgentChatHistory();
        chat.setRole(role);
        chat.setMessage(message.trim());
        chat.setSource(source);
        chat.setCreateAt(LocalDateTime.now());
        
        repository.save(chat);
        log.debug("[AgentChatService] 已儲存對話紀錄: role={}, source={}, length={}", role, source, message.length());
    }

    /**
     * 目的：取得最近的 N 筆對話歷史，從舊到新排序 (符合聊天視窗順序)
     * 輸入：無
     * 輸出：List<AgentChatHistory> - 排序後的對話紀錄清單
     * 限制：最多回傳最近的 50 筆
     * 副作用：無
     */
    public List<AgentChatHistory> getRecentChats() {
        // WHY: findTop50ByOrderByCreateAtDesc 取出來是新到舊，這裡需要反轉成舊到新以符合 UI 呈現
        List<AgentChatHistory> recentChats = repository.findTop50ByOrderByCreateAtDesc();
        java.util.Collections.reverse(recentChats);
        return recentChats;
    }
}

/* ### Review Checklist ###
 * 1. 交易管理：寫入操作是否有 @Transactional 標註？ ✓
 * 2. 邊界處理：空訊息或是空白字串是否有防呆處理？ ✓
 * 3. 邏輯正確性：取得對話紀錄時是不是已經正確反轉為舊到新？ ✓
 */
