/**
 * 目的：從使用者輸入的自然語言中萃取出有意義的關鍵字
 * 關鍵項目：
 * 1. 按空白、標點符號進行基礎斷詞
 * 2. 過濾通用停用詞（中文與英文）以提高檢索精準度
 * 3. 過濾過短的 token（單字元中文/英文除非特殊）
 * 模組：memory
 */
package com.github.nicholas23.majordomo.memory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class KeywordExtractor {

    // WHY: 這些停用詞在檢索時只會增加雜訊（noise），排除後能有效提升 LIKE 查詢的命中精準度
    private static final Set<String> STOP_WORDS = Set.of(
            // 中文常見停用詞
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
            "都", "一", "一個", "上", "也", "很", "到", "說", "要", "去",
            "你", "會", "著", "沒有", "看", "好", "自己", "這", "他", "她",
            "它", "吧", "被", "比", "別", "呢", "那", "嗎", "嘛", "啊",
            "從", "把", "讓", "還", "可以", "什麼", "怎麼", "如何", "為什麼",
            "幫我", "請", "能", "能不能", "可不可以", "幫", "想",
            // 英文常見停用詞
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can", "shall",
            "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "as", "into", "about", "like", "through", "after", "over",
            "between", "out", "against", "during", "without", "before",
            "under", "around", "among", "and", "but", "or", "nor", "not",
            "so", "yet", "both", "either", "neither", "each", "every",
            "all", "any", "few", "more", "most", "other", "some", "such",
            "no", "only", "own", "same", "than", "too", "very",
            "i", "me", "my", "myself", "we", "our", "ours", "you", "your",
            "he", "him", "his", "she", "her", "it", "its", "they", "them",
            "this", "that", "these", "those", "what", "which", "who",
            "how", "when", "where", "why", "here", "there", "then", "now",
            "just", "also", "if", "up"
    );

    // REASONING: 設定最小 token 長度為 2，
    // 以排除無意義的單字元 token（如被斷詞後的標點殘留）
    private static final int MIN_TOKEN_LENGTH = 2;

    private KeywordExtractor() {
        // WHY: 工具類別不應被實例化
    }

    /**
     * 目的：從自然語言文字中萃取關鍵字列表。
     * REASONING: 使用正則表達式按空白與常見標點進行斷詞，
     * 適用於中英文混合的輸入場景。對於純中文長句（如「今天要把API文件寫完」），
     * 會盡可能按標點與空白切分，但不做深度 NLP 分詞。
     * 輸入：
     * - text: String - 使用者的原始輸入
     * 輸出：List<String> - 去除停用詞後的關鍵字列表（保持原始順序）
     * 限制：如果 text 為空或 null，將回傳空列表
     * 副作用：無
     */
    public static List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // NOTE: 以空白、逗號、句號、問號、驚嘆號、分號、冒號、括號等作為分隔符
        String[] tokens = text.trim().split("[\\s,，。？！；：、()（）\\[\\]{}\"']+");

        return Arrays.stream(tokens)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .filter(t -> !STOP_WORDS.contains(t.toLowerCase()))
                .distinct()
                .toList();
    }
}

/* ### Review Checklist ###
 * 1. 邊界處理：null/blank 輸入回傳空列表 ✓
 * 2. 停用詞覆蓋：中英文常見停用詞皆有收錄 ✓
 * 3. 大小寫：英文停用詞比對時統一 toLowerCase ✓
 * 4. 不可變性：回傳 toList() 為不可變列表 ✓
 */
