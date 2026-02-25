/**
 * 目的：Spring Boot 應用程式入口點
 * 關鍵項目：
 * 1. 啟動前檢查 Gemini CLI 是否可用
 * 2. 若 CLI 不存在則阻止應用程式啟動
 * 模組：Application
 */
package com.github.nicholas23.majordomo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@SpringBootApplication
public class MajordomoApplication {

    private static final Logger log = LoggerFactory.getLogger(MajordomoApplication.class);

    /**
     * 目的：應用程式主進入點，啟動前先驗證 Gemini CLI 環境。
     * 輸入：args (String[]) - 命令列參數
     * 輸出：無
     * 副作用：若 CLI 不可用則輸出錯誤訊息並結束程式
     */
    public static void main(String[] args) {
        if (geminiCliTest()) {
            log.info("[Application] Gemini CLI 驗證通過，啟動應用程式");
            SpringApplication.run(MajordomoApplication.class, args);
        } else {
            // WHY: Gemini CLI 是本系統核心依賴，若不存在則整個系統無法運作
            log.error("[Application] Gemini CLI 未找到或無法執行，應用程式終止啟動");
            System.err.println("The Gemini CLI is not found or cannot be run. Please ensure it is installed on your system.");
        }
    }

    /**
     * 目的：執行 `gemini --help` 來檢測 Gemini CLI 是否已安裝且可執行。
     * 輸入：無
     * 輸出：boolean - true 表示 CLI 可用
     * 副作用：會啟動一個子程序
     */
    private static boolean geminiCliTest() {
        int exitValue = -1;
        try {
            // REASONING: 使用 --help 參數作為 CLI 可用性的輕量檢測方式
            CompletableFuture<Process> future = Runtime.getRuntime().exec(new String[]{"gemini", "--help"}).onExit();
            Process process = future.get();
            exitValue = process.exitValue();
            log.debug("[Application] Gemini CLI 檢測結果: exitValue={}", exitValue);
        } catch (IOException | ExecutionException | InterruptedException e) {
            // EDGE_CASE: CLI 未安裝時 exec 會拋出 IOException
            log.error("[Application] Gemini CLI 檢測失敗: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return exitValue == 0;
    }
}

/* ### Review Checklist ###
 * 1. 環境依賴：啟動前驗證 Gemini CLI 存在？ ✓
 * 2. 錯誤處理：CLI 檢測失敗有記錄 log？ ✓
 * 3. 安全性：未暴露敏感資訊？ ✓
 */
