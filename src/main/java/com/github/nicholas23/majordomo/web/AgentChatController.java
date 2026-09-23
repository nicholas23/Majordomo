/**
 * 目的：提供 Agent Chat 相關 Web API 與視圖
 * 關鍵項目：
 * 1. 顯示 Agent Chat UI
 * 2. 獲取聊天歷史紀錄
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.nicholas23.majordomo.agent.AgentPromptBuilder;
import com.github.nicholas23.majordomo.agent.Initial;
import com.github.nicholas23.majordomo.exec.ExecuteService;
import com.github.nicholas23.majordomo.chat.AgentChatService;
import com.github.nicholas23.majordomo.memory.KeywordExtractor;
import com.github.nicholas23.majordomo.mcp.MemoryMcpService;

import java.util.List;

@Controller
@RequestMapping("/web/agent/chat")
public class AgentChatController {

    private final AgentChatService agentChatService;
    private final Initial initialService;
    private final ExecuteService executeService;
    private final MemoryMcpService memoryMcpService;

    public AgentChatController(AgentChatService agentChatService, Initial initialService, ExecuteService executeService, MemoryMcpService memoryMcpService) {
        this.agentChatService = agentChatService;
        this.initialService = initialService;
        this.executeService = executeService;
        this.memoryMcpService = memoryMcpService;
    }

    /**
     * 目的：顯示 Agent Chat 介面。
     * 輸入：
     * - model: Model
     * 輸出：String - fragment 模板名稱
     * 限制：若尚未設定 BasicAgent，將導向 Agent 設定頁面片段
     * 副作用：無
     */
    @GetMapping
    public String showAgentChat(Model model) {
        // WHY: 若尚未設定 BasicAgent，導向 Agent 設定頁
        if (!initialService.isInitialized()) {
            return "fragments/agent_setup";
        }
        var recentChats = agentChatService.getRecentChats();
        boolean waitingReply = !recentChats.isEmpty() && "USER".equals(recentChats.get(recentChats.size() - 1).getRole());

        model.addAttribute("agentName", initialService.getAgentName());
        model.addAttribute("userName", initialService.getUserName());
        model.addAttribute("recentChats", recentChats);
        model.addAttribute("waitingReply", waitingReply);
        return "fragments/agent_chat";
    }

    /**
     * 目的：處理使用者發送的聊天訊息，並透過 BasicAgent 回覆。
     * 輸入：
     * - message: String - 使用者輸入的訊息內容
     * - model: Model
     * 輸出：String - 更新後的 fragment 模板名稱
     * 限制：若 message 為空，則不處理並原樣返回
     * 副作用：紀錄聊天訊息、觸發 RAG 檢索、呼叫 Agent 執行
     */
    @PostMapping("/send")
    public String sendMessage(@RequestParam String message, Model model) {
        if (message == null || message.isBlank()) {
            var recentChats = agentChatService.getRecentChats();
            boolean waitingReply = !recentChats.isEmpty() && "USER".equals(recentChats.get(recentChats.size() - 1).getRole());
            model.addAttribute("recentChats", recentChats);
            model.addAttribute("waitingReply", waitingReply);
            return "fragments/agent_chat";
        }
        // 儲存使用者訊息
        agentChatService.saveChat("USER", message, "WEB");
        if (initialService.isInitialized()) {
            try {
                // RAG from memory
                List<String> keywords = KeywordExtractor.extract(message);
                List<String> memories = memoryMcpService.recallByKeywords(keywords, 5);
                String prompt = AgentPromptBuilder.build(initialService.getUserName(), message, memories);
                executeService.runWithBasicAgent(initialService.getBasePath().toFile(), prompt);
            } catch (Exception e) {
                agentChatService.saveChat("AGENT", "❌ 處理失敗: " + e.getMessage(), "WEB");
            }
        } else {
            agentChatService.saveChat("AGENT", "⚠️ 尚未設定 Basic Agent，無法處理訊息。", "WEB");
        }

        var recentChats = agentChatService.getRecentChats();
        boolean waitingReply = !recentChats.isEmpty() && "USER".equals(recentChats.get(recentChats.size() - 1).getRole());

        model.addAttribute("agentName", initialService.getAgentName());
        model.addAttribute("userName", initialService.getUserName());
        model.addAttribute("recentChats", recentChats);
        model.addAttribute("waitingReply", waitingReply);
        return "fragments/agent_chat";
    }
}

/* ### Review Checklist ###
 * 1. 邊界條件：未設定 BasicAgent 時是否能優雅應對？ ✓
 * 2. 錯誤處理：Agent 執行拋出 Exception 時是否有適當的錯誤訊息回覆？ ✓
 */
