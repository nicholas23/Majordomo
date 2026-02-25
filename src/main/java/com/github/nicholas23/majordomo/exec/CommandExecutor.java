/**
 * 目的：定義 CLI 指令執行器介面
 * 關鍵項目：
 * 1. 將實際的執行緒啟動邏輯抽象化，以便於單元測試 Mocking。
 * 模組：exec
 */
package com.github.nicholas23.majordomo.exec;

import java.io.IOException;

public interface CommandExecutor {
    /**
     * 目的：執行系統指令
     * 輸入：
     * - command: String[] - 指令與參數列表
     * - dir: java.io.File - 執行目錄
     * 輸出：Process - 啟動的程序物件
     * 限制：command 不可空，dir 必須是存在的工作目錄
     * 副作用：啟動作業系統層級的新程序
     * @throws IOException 啟動失敗
     */
    Process execute(String[] command, java.io.File dir) throws IOException;
}

/* ### Review Checklist ###
 * 1. 職責分離：執行指令介面化是否可以方便替換底層的執行方式？ ✓
 */
