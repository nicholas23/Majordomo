/**
 * 目的：MCP 服務的 Spring 配置類別
 * 關鍵項目：
 * 1. 將標註 @Tool 的服務方法自動註冊為 MCP 工具
 * 2. 註冊 MemoryMcpService 的 store / recall / forget 工具
 * 3. 註冊 WorkspaceMcpService 的工作區管理與排程工具
 * 4. 註冊 TelegramMcpService 的使用者通訊工具
 * 5. 註冊 AgentTodoMcpService 的待辦提醒工具 (addAgentTodo)
 * 模組：config
 */
package com.github.nicholas23.majordomo.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.nicholas23.majordomo.mcp.MemoryMcpService;
import com.github.nicholas23.majordomo.mcp.WorkspaceMcpService;
import com.github.nicholas23.majordomo.mcp.TelegramMcpService;
import com.github.nicholas23.majordomo.mcp.AgentTodoMcpService;

// REASONING: 使用 MethodToolCallbackProvider 自動掃描方式，
// 比手動逐一註冊更易維護且不易遺漏新增的 @Tool 方法
@Configuration
public class McpServiceConfigure {
    /**
     * 目的：註冊 Memory MCP 工具
     * 輸入：memoryMcpService: MemoryMcpService
     * 輸出：ToolCallbackProvider - MCP 工具提供者
     * 限制：無
     * 副作用：將記憶工具發佈至 MCP Server
     */
    @Bean
    public ToolCallbackProvider memoryToolCallbackProvider(MemoryMcpService memoryMcpService) {
        // WHY: 這行程式碼會自動掃描 service 裡面所有標註 @Tool 的方法，並註冊到 MCP 中
        return MethodToolCallbackProvider.builder()
                .toolObjects(memoryMcpService)
                .build();
    }

    /**
     * 目的：註冊 Workspace MCP 工具
     * 輸入：workspaceMcpService: WorkspaceMcpService
     * 輸出：ToolCallbackProvider - MCP 工具提供者
     * 限制：無
     * 副作用：將工作區管理工具發佈至 MCP Server
     */
    @Bean
    public ToolCallbackProvider workspaceToolCallbackProvider(WorkspaceMcpService workspaceMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(workspaceMcpService)
                .build();
    }

    /**
     * 目的：註冊 Telegram MCP 工具
     * 輸入：telegramMcpService: TelegramMcpService
     * 輸出：ToolCallbackProvider - MCP 工具提供者
     * 限制：無
     * 副作用：將 Telegram 推送工具發佈至 MCP Server
     */
    @Bean
    public ToolCallbackProvider telegramToolCallbackProvider(TelegramMcpService telegramMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(telegramMcpService)
                .build();
    }

    /**
     * 目的：註冊 AgentTodo MCP 工具
     * 輸入：agentTodoMcpService: AgentTodoMcpService
     * 輸出：ToolCallbackProvider - MCP 工具提供者
     * 限制：無
     * 副作用：將待辦事項管理工具發佈至 MCP Server
     */
    @Bean
    public ToolCallbackProvider agentTodoToolCallbackProvider(AgentTodoMcpService agentTodoMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(agentTodoMcpService)
                .build();
    }
}

/* ### Review Checklist ###
 * 1. Bean 命名：各 ToolCallbackProvider 語義明確 ✓
 * 2. 依賴注入：各 McpService 由 Spring 管理 ✓
 * 3. 完整性：涵蓋 Memory、Workspace、Telegram、AgentTodo 四大 MCP 服務 ✓
 */
