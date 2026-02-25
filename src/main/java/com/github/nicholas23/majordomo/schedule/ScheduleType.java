/**
 * 目的：排程類型的列舉定義
 * 關鍵項目：
 * 1. CRON_JOB - 週期性排程（使用 cron 表達式）
 * 2. ONE_TIME - 一次性排程（使用指定時間點）
 * 模組：schedule
 */
package com.github.nicholas23.majordomo.schedule;

public enum ScheduleType {
    CRON_JOB,
    ONE_TIME,
}

/* ### Review Checklist ###
 * 1. 狀態涵蓋：狀態是否滿足所有的情境？ ✓
 */
