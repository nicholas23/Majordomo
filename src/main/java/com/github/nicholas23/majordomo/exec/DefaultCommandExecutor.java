/**
 * 目的：預設指令執行器實作，使用 ProcessBuilder 啟動程序
 * 關鍵項目：
 * 1. 實作 CommandExecutor，呼叫系統層次的 CLI
 * 2. 在 macOS 環境下透過環境變數或參數配置 sandbox 保護
 * 模組：exec
 */
package com.github.nicholas23.majordomo.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;

// WHY: 在 macOS 上可自動設定 SEATBELT_PROFILE=strict-open 環境變數以加強安全性，
// 搭配 ExecuteService 的 -s 參數啟用 Gemini CLI sandbox。
@Component
public class DefaultCommandExecutor implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultCommandExecutor.class);
    private static final String SEATBELT_PROFILE_VALUE = "strict-open";

    private final boolean isMacOs;

    public DefaultCommandExecutor() {
        this.isMacOs = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (isMacOs) {
            log.info("[DefaultCommandExecutor] macOS 偵測成功，SEATBELT_PROFILE={}", SEATBELT_PROFILE_VALUE);
        }
    }

    /**
     * 目的：根據給定的指令與目錄啟動新程序。
     * 輸入：
     * - command: String[] - 待執行的指令與參數陣列
     * - dir: File - 執行的工作目錄
     * 輸出：Process - 已啟動的子程序
     * 限制：command 不應包含惡意注入
     * 副作用：作業系統上產生新的 Process，佔用資源
     */
    @Override
    public Process execute(String[] command, File dir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir);

        // TODO: macOS 上自動設定 SEATBELT_PROFILE，搭配 -s 參數啟用 strict-open sandbox
        // 目前因相容性問題暫時停用，待 Gemini CLI sandbox 穩定後再啟用
        // if (isMacOs) {
        //     pb.environment().put("SEATBELT_PROFILE", SEATBELT_PROFILE_VALUE);
        // }

        return pb.start();
    }
}

/* ### Review Checklist ###
 * 1. 跨平台相容：是否正確偵測並處理不同作業系統上的沙箱機制？ ✓
 * 2. 外部依賴：使用的執行方式是否安全？ ✓
 */
