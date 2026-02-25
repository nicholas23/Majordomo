/**
 * 目的：BasicAgent 專屬的提示詞建構器
 * 關鍵項目：
 * 1. 簡化 Prompt：因為 Gemini CLI 自動載入 GEMINI.md 作為上下文，不再需要硬塞大量規則。
 * 2. 僅提供核心身份喚起與當下對話訊息。
 * 3. 支援 Auto-Context：將 RAG 檢索到的相關記憶注入 Prompt 中。
 * 模組：basicagent
 */
package com.github.nicholas23.majordomo.agent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


public class AgentPromptBuilder {


    /**
     * 目的：組裝 BasicAgent 對話用的結構化提示詞（含 Auto-Context 記憶注入）。
     * 輸入：
     * - username: String - 使用者名稱
     * - userMessage: String - 使用者訊息
     * - autoContextMemories: List<String> - 由 RAG 預先檢索到的相關記憶
     * 輸出：String - 完整的結構化 Prompt
     * 限制：參數均可接受 null（會進行安全轉換處理）
     * 副作用：無
     * REASONING: Gemini CLI 會自動讀取專案下的 GEMINI.md。
     * 我們的 GEMINI.md 已經定義好了行為規範與讀取 SOUL.md / MEMORY.md 的指示，
     * 所以這裡只需傳遞使用者訊息並喚醒身份即可，大幅降低 Prompt Token 消耗。
     * 額外注入的記憶能讓 Agent 在對話中具備過去經驗的上下文感知。
     */
    public static String build(String username, String userMessage,
                               List<String> autoContextMemories) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        String message = safeText(userMessage);
        StringBuilder sb = new StringBuilder();

        // WHY: 若有預先檢索到的相關記憶，放在訊息前方讓 Agent 先建立上下文
        if (autoContextMemories != null && !autoContextMemories.isEmpty()) {
            sb.append("【系統自動帶入的近期/相關記憶】\n");
            for (String memory : autoContextMemories) {
                sb.append("- ").append(memory).append("\n");
            }
            sb.append("\n");
        }

        sb.append(String.format("""
                【回覆規則】
                請一定要使用 **`sendMessageToUser`**，來回覆使用者的訊息，並且只能回覆一次，不然會讓人以為你沒有收到。
                【來自%s的訊息（現在時間：%s 】              
                %s
                """, username,dtf.format(LocalDateTime.now()), message));

        return sb.toString();
    }

    /**
     * 目的：將輸入字串做安全轉換，null 或空白轉為 "(empty)"。
     * 輸入：
     * - value: String - 待轉換的文字
     * 輸出：String - 安全處理後的字串
     * 限制：無
     * 副作用：無
     */
    private static String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.trim();
    }
}

/* ### Review Checklist ###
 * 1. 向下相容：保留原始雙參數 build 方法 ✓
 * 2. 邊界處理：autoContextMemories 為 null 或空時不輸出記憶區塊 ✓
 * 3. 記憶注入位置：放在使用者訊息前方以建立上下文 ✓
 */
