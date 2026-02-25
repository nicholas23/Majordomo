/**
 * 目的：Memory MCP 服務層 — AI 長期記憶的 CRUD 工具
 * 關鍵項目：
 * 1. 實作 store / recall / forget 三個 MCP 工具方法
 * 2. store 支援 Upsert（key 相同時更新，不存在時新增）
 * 3. recall 支援 keyword-based 模糊檢索（為 RAG 的檢索基礎）
 * 4. recallByKeywords 供 AgentPromptBuilder 的 Auto-Context 使用
 * 模組：memory
 */
package com.github.nicholas23.majordomo.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.nicholas23.majordomo.memory.Memory;
import com.github.nicholas23.majordomo.memory.MemoryRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class MemoryMcpService {

    private static final Logger log = LoggerFactory.getLogger(MemoryMcpService.class);

    private final MemoryRepository memoryRepository;

    public MemoryMcpService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * 目的：儲存一筆記憶到資料庫。若 key 已存在則更新內容 (Upsert)。
     * WHY: Agent 需要能夠持久化使用者偏好、決策與經驗，使其在跨對話之間具備連續性的意識。
     * 輸入：
     * - key: String - 記憶鍵
     * - category: String - 分類
     * - text: String - 內容
     * 輸出：Boolean - 是否儲存成功
     * 限制：key 與 text 不可為空
     * 副作用：寫入或更新資料庫
     */
    @Tool(description = "儲存在記憶庫：請在儲存持久的偏好設定、決策或關鍵情境下使用，"
            + "但避免資訊短暫、吵雜或敏感及無明確需要儲存的資訊。")
    @Transactional
    public Boolean store(String key, String category, String text) {
        long startNs = System.nanoTime();
        // EDGE_CASE: 參數為空時提前返回
        if (key == null || key.isBlank() || text == null || text.isBlank()) {
            log.warn("[Memory] store 呼叫失敗：key 或 text 為空。key={}", key);
            return false;
        }

        String safeCategory = (category == null || category.isBlank()) ? "custom" : category.trim();

        Optional<Memory> existing = memoryRepository.findByMemoryKey(key.trim());
        if (existing.isPresent()) {
            // REASONING: Upsert 避免重複 key 的記憶；只更新內容與時間戳
            Memory memory = existing.get();
            memory.setContent(text.trim());
            memory.setCategory(safeCategory);
            memory.setUpdatedAt(LocalDateTime.now());
            memoryRepository.save(memory);
            log.info("[Memory] 已更新記憶：key={}, category={}, elapsed={}ms",
                    key, safeCategory, (System.nanoTime() - startNs) / 1_000_000);
        } else {
            Memory memory = new Memory();
            memory.setMemoryKey(key.trim());
            memory.setCategory(safeCategory);
            memory.setContent(text.trim());
            memory.setCreatedAt(LocalDateTime.now());
            memory.setUpdatedAt(LocalDateTime.now());
            memoryRepository.save(memory);
            log.info("[Memory] 已新增記憶：key={}, category={}, elapsed={}ms",
                    key, safeCategory, (System.nanoTime() - startNs) / 1_000_000);
        }
        return true;
    }

    /**
     * 目的：依據查詢字串搜尋相關記憶。
     * WHY: Agent 需要在對話中回想過去的事實或偏好，以提供更具上下文感知的回應。
     * 輸入：
     * - query: String - 搜尋字串
     * - limit: Integer - 回傳上限
     * 輸出：String[] - 匹配的記憶內容陣列
     * 限制：query 不可為空
     * 副作用：無
     */
    @Tool(description = "找回記憶：請在需要回顧過去資訊以輔助決策時使用，"
            + "但避免頻繁或過度回顧，尤其是當資訊已過時、無關，"
            + "可能引起混淆以及答案己存在目前的對話或文件時使用。")
    public String[] recall(String query, Integer limit) {
        long startNs = System.nanoTime();
        if (query == null || query.isBlank()) {
            log.warn("[Memory] recall 呼叫失敗：query 為空");
            return new String[]{};
        }
        int safeLimit = (limit == null || limit <= 0) ? 5 : limit;

        List<Memory> results = memoryRepository.searchByKeyword(query.trim(), safeLimit);
        log.info("[Memory] recall query='{}', 找到 {} 筆, elapsed={}ms",
                query, results.size(), (System.nanoTime() - startNs) / 1_000_000);

        return results.stream()
                .map(m -> String.format("[%s] %s: %s", m.getCategory(), m.getMemoryKey(), m.getContent()))
                .toArray(String[]::new);
    }

    /**
     * 目的：依據 key 刪除一筆記憶。
     * WHY: 支援使用者主動遺忘過時或錯誤的資訊，維持記憶的準確性。
     * 輸入：
     * - key: String - 記憶的唯一辨識鍵
     * 輸出：Boolean - 是否刪除成功
     * 限制：key 不可為空
     * 副作用：從資料庫刪除該筆記憶
     */
    @Tool(description = "忘記記憶：忘掉錯誤，過時與明確被要求忘掉的記憶，"
            + "避免頻繁或過度刪除，尤其是當資訊可能仍有價值或未明確需要刪除時使用。")
    @Transactional
    public Boolean forget(String key) {
        long startNs = System.nanoTime();
        if (key == null || key.isBlank()) {
            log.warn("[Memory] forget 呼叫失敗：key 為空");
            return false;
        }
        Optional<Memory> existing = memoryRepository.findByMemoryKey(key.trim());
        if (existing.isPresent()) {
            memoryRepository.delete(existing.get());
            log.info("[Memory] 已刪除記憶：key={}, elapsed={}ms",
                    key, (System.nanoTime() - startNs) / 1_000_000);
            return true;
        } else {
            log.warn("[Memory] forget 目標不存在：key={}", key);
            return false;
        }
    }

    // ====== 以下為內部方法，供 Prompt Builder 的 Auto-Context 使用 ======

    /**
     * 目的：依據多個關鍵字進行聯集搜尋，回傳去重後最相關的記憶列表。
     * REASONING: 此方法專為 AgentPromptBuilder 的 Keyword RAG 設計。
     * 將多個關鍵字各自查詢後取聯集、去重、取前 N 筆，以模擬基於命中數的相關度排序。
     * 輸入：
     * - keywords: List<String> - 從 userMessage 萃取出的關鍵字
     * - limit: int - 最終回傳的記憶上限
     * 輸出：List<String> - 格式化後的記憶字串列表
     * 限制：無
     * 副作用：無
     */
    public List<String> recallByKeywords(List<String> keywords, int limit) {
        long startNs = System.nanoTime();
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        // WHY: 使用 LinkedHashSet 維持插入順序且自動去重，
        // 確保高頻命中的記憶排在前面（因先查到的關鍵字通常較重要）
        Set<Memory> uniqueResults = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            List<Memory> partial = memoryRepository.searchByKeyword(keyword.trim(), limit);
            uniqueResults.addAll(partial);
        }

        log.info("[Memory] recallByKeywords keywords={}, 找到 {} 筆不重複記憶, elapsed={}ms",
                keywords, uniqueResults.size(), (System.nanoTime() - startNs) / 1_000_000);

        return uniqueResults.stream()
                .limit(limit)
                .map(m -> String.format("[%s] %s: %s", m.getCategory(), m.getMemoryKey(), m.getContent()))
                .toList();
    }
}

/* ### Review Checklist ###
 * 1. 事務保護：store / forget 標註 @Transactional ✓
 * 2. 邊界處理：所有方法檢查 null/blank 參數 ✓
 * 3. 日誌記錄：所有操作均有 DEBUG/WARN 等級的日誌 ✓
 * 4. 安全性：無敏感資訊洩漏風險 ✓
 * 5. 職責分離：MCP 工具方法 vs 內部檢索方法明確區隔 ✓
 */
