/**
 * 目的：從 classpath 載入模板資源檔案
 * 關鍵項目：
 * 1. 讀取 resources/basicagent/ 下的 .md 模板檔案
 * 2. 回傳完整字串供 String.format() 填入佔位符
 * 模組：basicagent
 */
package com.github.nicholas23.majordomo.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// REASONING: 獨立工具類別，避免模板載入邏輯散落在 Initial 各方法中
public class TemplateLoader {
    private static final Logger log = LoggerFactory.getLogger(TemplateLoader.class);

    private TemplateLoader() {
        // WHY: 工具類別不需要實例化
    }

    /**
     * 目的：從 classpath 載入模板檔案並回傳其內容字串。
     * 輸入：
     * - resourcePath: String - classpath 下的資源路徑，如 "basicagent/identity.md"
     * 輸出：String - 模板完整內容
     * 限制：resourcePath 不能為 null 且必須指向存在於 classpath 的資源
     * 副作用：無
     * @throws IllegalStateException 若模板不存在或讀取失敗
     */
    public static String load(String resourcePath) {
        // WHY: 使用 ClassLoader.getResourceAsStream 確保在 JAR 和 IDE 環境下皆可讀取
        try (InputStream is = TemplateLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("[TemplateLoader] 找不到模板資源: {}", resourcePath);
                throw new IllegalStateException("Template not found: " + resourcePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("[TemplateLoader] 已載入模板: {}, length={}", resourcePath, content.length());
            return content;
        } catch (IOException e) {
            log.error("[TemplateLoader] 讀取模板失敗: {}", resourcePath, e);
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }
}

/* ### Review Checklist ###
 * 1. 資源管理：try-with-resources 確保 InputStream 關閉 ✓
 * 2. 邊界處理：資源不存在時拋出明確的 IllegalStateException ✓
 * 3. 編碼：使用 UTF-8 確保中文模板正確讀取 ✓
 * 4. 日誌記錄：載入成功/失敗皆有日誌 ✓
 */
