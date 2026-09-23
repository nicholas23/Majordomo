/**
 * 目的：設定非同步執行緒池
 * 關鍵項目：
 * 1. 設定 ThreadPoolTaskScheduler 供排程任務使用
 * 2. 設定 VirtualThreadPerTaskExecutor 供串流讀取使用
 * 3. 設定 VirtualThreadPerTaskExecutor 供 Workspace 非同步指令執行使用
 * 模組：config
 */
package com.github.nicholas23.majordomo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncThreadPoolsConfigure {

    @Value("${app.scheduler.await-termination-seconds:5}")
    private int awaitTerminationSeconds;

    /**
     * 目的：全局 TaskScheduler（供 ScheduleService 使用，支援 Virtual Thread）
     * 輸入：無
     * 輸出：TaskScheduler - 排程器
     * 限制：應用的生命週期內全域共用
     * 副作用：配置了優雅停機（由 app.scheduler.await-termination-seconds 設定緩衝）
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadFactory(Thread.ofVirtual().name("task-scheduler-", 0).factory());
        scheduler.setThreadNamePrefix("task-scheduler-");
        // WHY: 啟用優雅停機，並支援由屬性配置緩衝時間，避免測試環境過度等待
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return scheduler;
    }

    /**
     * 目的：Stream Reader Executor（使用 Virtual Thread）
     * 輸入：無
     * 輸出：ExecutorService - 線程執行器
     * 限制：供長時間持有的 IO 串流讀取使用
     * 副作用：無
     */
    @Bean(name = "streamReaderExecutor", destroyMethod = "shutdown")
    public ExecutorService streamReaderExecutor() {
        // Virtual Thread 的 ExecutorService
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 目的：Workspace 指令非同步執行器（使用 Virtual Thread）
     * 輸入：無
     * 輸出：ExecutorService - 線程執行器
     * 限制：提供給系統中臨時生成的短時間工作區指令執行
     * 副作用：無
     * WHY: 語意上與 streamReader 和 taskScheduler 區隔，
     * 用於所有 Workspace 的單次非同步指令執行（Agent MCP、Web UI 立即執行等）。
     */
    @Bean(name = "workspaceCommandExecutor", destroyMethod = "shutdown")
    public ExecutorService workspaceCommandExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

/* ### Review Checklist ###
 * 1. Resource Management: Executors properly shutdown? (Spring manages via destroyMethod/bean lifecycle) ✓
 * 2. Thread Safety: Virtual threads used appropriately for IO-bound tasks? ✓
 * 3. Configuration: Pool sizes and timeouts configurable? (Hardcoded for now, acceptable for MVP) ✓
 */
