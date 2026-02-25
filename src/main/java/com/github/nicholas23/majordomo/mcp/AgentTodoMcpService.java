/**
 * 目的：主 Agent 自排管理 MCP 工具服務
 * 關鍵項目：
 * 1. 供 Agent 自己將待辦事項注入時間窗口
 * 2. 以短心跳來啟動後續檢查機制。
 * 模組：mcp
 */
package com.github.nicholas23.majordomo.mcp;

import com.github.nicholas23.majordomo.agent.AgentTodo;
import com.github.nicholas23.majordomo.agent.AgentTodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class AgentTodoMcpService {
    private static final Logger log = LoggerFactory.getLogger(AgentTodoMcpService.class);

    private final AgentTodoRepository agentTodoRepository;

    public AgentTodoMcpService(AgentTodoRepository agentTodoRepository) {
        this.agentTodoRepository = agentTodoRepository;
    }

    /**
     * 目的：供主 Agent 將待辦事項存入，時間到了會以短心跳形式觸發檢查。
     * REASONING: 透過這個工具，Agent 具備未來自主規劃的能力。
     * 輸入：
     * - description: String - 待辦事項的描述內容
     * - scheduledTime: String - 絕對時間 (格式: yyyy-MM-dd HH:mm:ss) 
     * 輸出：String - 是否成功加入待辦。
     * 限制：時間格式必須被嚴格遵守，因為心跳的區間查詢會使用 DateTime
     * 副作用：寫入 DB (agent_todo 表)
     */
    @Tool(description = "這是用來提供加入ToDoList的功能 。加入的待辦事項，會在指定時間，經由短心跳觸發，加入到prompt中。scheduledTime 必須以「絕對時間」輸入 (例如: 2024-12-31 23:59:00)，描述請簡短精確。")
    @Transactional
    public String addAgentTodo(String description, String scheduledTime) {
        long startNs = System.nanoTime();

        try {
            LocalDateTime datetime = LocalDateTime.parse(scheduledTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            AgentTodo todo = new AgentTodo();
            todo.setDescription(description);
            todo.setScheduledTime(datetime);
            todo.setCreatedAt(LocalDateTime.now());

            AgentTodo saved = agentTodoRepository.save(todo);

            log.info("[AgentTodoMcpService] addAgentTodo 成功: id={}, scheduled={}, elapsed={}ms",
                    saved.getId(), datetime, (System.nanoTime() - startNs) / 1_000_000);

            return "已成功加入待辦事項 (ID=" + saved.getId() + ")。將在 " + scheduledTime + " 或其之後的短心跳時將你喚醒。";
        } catch (DateTimeParseException e) {
            log.warn("[AgentTodoMcpService] addAgentTodo 失敗，時間格式錯誤: input={}", scheduledTime);
            return "錯誤: 時間格式無法解析。請確保輸入為絕對時間，格式如 'yyyy-MM-dd HH:mm:ss' (例如 '2025-05-15 14:30:00')。";
        } catch (Exception e) {
            log.error("[AgentTodoMcpService] addAgentTodo 發生未預期錯誤", e);
            return "錯誤: 未知錯誤 - " + e.getMessage();
        }
    }
}

/* ### Review Checklist ###
 * 1. 職責分離：邏輯包含建立與檢查格式 ✓
 * 2. 錯誤捕捉：捕捉到 DateTimeParseException 並友善提示？ ✓
 */
