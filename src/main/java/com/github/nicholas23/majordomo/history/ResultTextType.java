/**
 * 目的：定義執行結果的文字類型
 * 關鍵項目：
 * 1. 對應 stdout 和 stderr
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

public enum ResultTextType {
    STDOUT,
    STDERR,
}

/* ### Review Checklist ###
 * 1. 安全性：所有可能的流程輸出都被涵蓋？ ✓
 * 2. 邊界條件：新增新類型時是否需要改動 DB (目前用 enum String)？ ✓
 * 3. 日誌：無邏輯不需日誌 ✓
 */
