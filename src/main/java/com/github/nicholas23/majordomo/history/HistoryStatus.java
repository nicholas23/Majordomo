/**
 * 目的：History 執行狀態的列舉
 * 關鍵項目：定義歷史紀錄的所有可能狀態
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

// REASONING: 使用明確的列舉取代以 endTime 是否為 null 推斷狀態的做法，
// 同時支援更多狀態如 FAILED 和 TERMINATED
public enum HistoryStatus {
    /** 排程已建立但尚未開始執行 */
    PENDING,
    /** 正在執行中 */
    RUNNING,
    /** 執行成功完成 */
    COMPLETED,
    /** 執行失敗 */
    FAILED,
    /** 被使用者終止 */
    TERMINATED
}

/* ### Review Checklist ###
 * 1. 狀態涵蓋：狀態是否滿足所有的情境？ ✓
 */
