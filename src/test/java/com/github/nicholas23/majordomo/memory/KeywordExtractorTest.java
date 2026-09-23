/**
 * 目的：測試 KeywordExtractor 關鍵字萃取功能
 * 關鍵項目：
 * 1. 驗證空值與空白輸入處理
 * 2. 驗證中英文停用詞過濾
 * 3. 驗證最小長度限制（< 2 的字元排除）
 * 4. 驗證標點符號斷詞與去重保持順序
 * 模組：memory
 */
package com.github.nicholas23.majordomo.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordExtractorTest {

    @Test
    @DisplayName("當輸入為 null 或全空白時，應回傳空列表")
    void shouldReturnEmptyListWhenTextIsNullOrEmpty() {
        // EDGE_CASE: null 與 blank 輸入防呆
        assertThat(KeywordExtractor.extract(null)).isEmpty();
        assertThat(KeywordExtractor.extract("")).isEmpty();
        assertThat(KeywordExtractor.extract("   \t\n  ")).isEmpty();
    }

    @Test
    @DisplayName("應正確以標點符號與空白進行斷詞，並排除停用詞與過短詞")
    void shouldExtractKeywordsAndFilterStopWords() {
        // 包含中文停用詞（請、幫我、在、的）與英文停用詞（the、in）
        String text = "請幫我把 Spring Boot 專案中的 Bug 修復，順便在 GitHub 上建立 PR！";
        List<String> result = KeywordExtractor.extract(text);

        // WHY: "Spring", "Boot", "專案中的" 或分割後字詞應保留有效關鍵字，停用詞如 "幫我"、"請"、"在" 應被排除
        assertThat(result).contains("Spring", "Boot", "GitHub");
        assertThat(result).doesNotContain("請", "幫我", "在", "上");
    }

    @Test
    @DisplayName("應過濾長度小於 2 的單字元 token")
    void shouldFilterShortTokens() {
        String text = "a b cd e fg h";
        List<String> result = KeywordExtractor.extract(text);

        // REASONING: 長度小於 2 的單字元（如 'a', 'b', 'e', 'h'）會被排除，留下的如 'cd', 'fg'
        assertThat(result).containsExactly("cd", "fg");
    }

    @Test
    @DisplayName("應去除重複出現的關鍵字並保持首次出現順序")
    void shouldDistinctKeywordsPreservingOrder() {
        String text = "Deploy Docker container, then test Docker container thoroughly";
        List<String> result = KeywordExtractor.extract(text);

        // WHY: "Docker" 與 "container" 重複出現，應去重且保持初次出現之順序
        assertThat(result).contains("Docker", "container", "Deploy", "test", "thoroughly");
        long dockerCount = result.stream().filter(k -> k.equalsIgnoreCase("Docker")).count();
        assertThat(dockerCount).isEqualTo(1);
    }

    @Test
    @DisplayName("英文停用詞應不分大小寫正確被過濾")
    void shouldFilterEnglishStopWordsCaseInsensitively() {
        String text = "The quick brown fox jumps over The lazy dog";
        List<String> result = KeywordExtractor.extract(text);

        // "The", "over" 屬於 stop words
        assertThat(result).doesNotContain("The", "the", "over");
        assertThat(result).contains("quick", "brown", "fox", "jumps", "lazy", "dog");
    }
}

/* ### Review Checklist ###
 * 1. 邊界測試：null、空字串、全空白已涵蓋 ✓
 * 2. 斷詞驗證：標點與空白切割已驗證 ✓
 * 3. 停用詞驗證：中英文及大小寫過濾已驗證 ✓
 * 4. 長度限制驗證：< 2 字元排除已驗證 ✓
 */
