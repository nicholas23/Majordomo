/**
 * 目的：Gemini CLI 指令執行服務（一般化版本）
 * 關鍵項目：
 * 1. 統一的 execute() 方法：建 History → 啟動 CLI → 收集 stdout/stderr → 更新狀態 → 回傳 ExecutionResult
 * 2. executeAsync() 用 workspaceCommandExecutor 包裝非同步執行
 * 3. 高層方法 (run, runAsyncFromAgent, runWithBasicAgent) 只做情境前後處理
 * 4. workspaceCommandExecutor 統一管理所有非同步 CLI 執行
 * 模組：exec
 */
package com.github.nicholas23.majordomo.exec;

import com.github.nicholas23.majordomo.history.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.github.nicholas23.majordomo.workspace.Workspace;
import com.github.nicholas23.majordomo.workspace.AgentCommandTaskRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ExecuteService {
    private static final Logger log = LoggerFactory.getLogger(ExecuteService.class);

    // REASONING: Extract CLI constants to avoid magic strings and allow easy modification
    private static final String GEMINI_CLI_CMD = "gemini";
    // WHY: BasicAgent 不屬於任何 Workspace，使用 -1 作為識別碼
    public static final long BASIC_AGENT_WORKSPACE_ID = -1;

    // REASONING: Define configuration constants for execution behavior
    private static final long DEFAULT_FLUSH_INTERVAL_SECONDS = 60;
    private static final long STREAM_READER_TIMEOUT_SECONDS = 1;

    private final ExecutorService streamReaderExecutor;
    private final ExecutorService workspaceCommandExecutor;

    private final HistoryService historyService;
    private final CommandExecutor commandExecutor;
    private final AgentCommandTaskRepository agentCommandTaskRepository;


    /**
     * 追蹤正在執行中的進程，key 為 historyId
     */
    private final ConcurrentMap<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    /**
     * 記錄被主動中止的 historyId，避免最終狀態被覆寫
     */
    private final Set<Long> terminatedHistoryIds = ConcurrentHashMap.newKeySet();

    public ExecuteService(HistoryService historyService
            , @Qualifier("streamReaderExecutor") ExecutorService streamReaderExecutor
            , @Qualifier("workspaceCommandExecutor") ExecutorService workspaceCommandExecutor
            , CommandExecutor commandExecutor
            , AgentCommandTaskRepository agentCommandTaskRepository) {
        this.historyService = historyService;
        this.streamReaderExecutor = streamReaderExecutor;
        this.workspaceCommandExecutor = workspaceCommandExecutor;
        this.commandExecutor = commandExecutor;
        this.agentCommandTaskRepository = agentCommandTaskRepository;
    }

    // ==========================================
    // 統一執行結果
    // ==========================================

    /**
     * 目的：封裝 CLI 執行結果，讓呼叫端可根據結果做後續處理。
     * @param historyId 對應的歷史紀錄 ID
     * @param exitValue CLI 程序的結束碼
     * @param status 最終執行狀態
     */
    public record ExecutionResult(
            long historyId,
            int exitValue,
            HistoryStatus status
    ) { }

    // ==========================================
    // 核心方法：execute (同步) + executeAsync (非同步)
    // ==========================================

    /**
     * 目的：統一的底層同步執行方法。
     * WHY: 所有 CLI 執行都走同一條路：建 History → 跑 CLI → 收集 stdout/stderr → 更新狀態。
     * 輸入：
     * - workspaceId: long - 工作區 ID
     * - dir: File - 工作目錄
     * - prompt: String - 完整提示詞
     * 輸出：ExecutionResult - 包含 historyId、exitValue、ResultText ID、最終狀態
     * 限制：呼叫方需負責確認 dir 存在且 prompt 無惡意注入
     * 副作用：建立 History 紀錄、啟動子程序、寫入 stdout/stderr
     */
    ExecutionResult execute(long workspaceId, File dir, String prompt, String command, boolean init) {
        History history = historyService.createHistory(workspaceId, command);

        int exitValue = -1;
        HistoryStatus status;
        try {
            exitValue = runCli(history, dir, prompt, init);
            status = resolveFinalStatus(history.getId(), exitValue == 0 ? HistoryStatus.COMPLETED : HistoryStatus.FAILED);
        } catch (RuntimeException e) {
            log.error("[ExecuteService] CLI 執行異常: historyId={}", history.getId(), e);
            historyService.appendHistoryLog(history.getId(), ResultTextType.STDERR, "Execution failed: " + e.getMessage() + "\n");
            status = resolveFinalStatus(history.getId(), HistoryStatus.FAILED);
        }

        historyService.updateHistoryStatus(history.getId(), status);
        terminatedHistoryIds.remove(history.getId());
        log.info("[ExecuteService] 執行完成: historyId={}, exitValue={}, status={}", history.getId(), exitValue, status);
        debugPrintResult(history.getId());
        if (exitValue != 0) {
            logAllOutputAndError(history);
        }
        return new ExecutionResult(history.getId(), exitValue, status);
    }

    /**
     * 目的：記錄 CLI 執行失敗時的完整 stdout 與 stderr 日誌。
     * 輸入：
     * - history: History - 執行紀錄物件
     * 輸出：無
     * 限制：無
     * 副作用：寫入 ERROR 等級日誌
     */
    private void logAllOutputAndError(History history) {
        String output = historyService.getResultContent(history.getId(), ResultTextType.STDOUT);
        String error = historyService.getResultContent(history.getId(), ResultTextType.STDERR);
        log.error("[ExecuteService] CLI 執行失敗詳細資訊: \n stdout={} \n\n stderr={}", output, error);
    }

    /**
     * 目的：以 DEBUG 等級輸出 CLI 執行結果的統計資訊（Tool 呼叫次數、檔案變更）。
     * 輸入：
     * - historyId: long - 歷史紀錄 ID
     * 輸出：無
     * 限制：若結果無法解析則不輸出
     * 副作用：寫入 DEBUG 日誌
     */
    private void debugPrintResult(long historyId) {
        String outputPreview = historyService.getResultContent(historyId, ResultTextType.STDOUT);
        GeminiCliJsonOutputParser.GeminiCLiJsonResponse response = GeminiCliJsonOutputParser.parser(outputPreview);
        if (response != null && response.getStats() != null) {
            log.debug("tools call info={}", response.getStats().getTools());
            log.debug("file update info={}", response.getStats().getFiles());
        }
    }

    /**
     * 目的：非同步執行包裝 — 用 workspaceCommandExecutor 提交 execute()。
     * WHY: 統一管理非同步 CLI 執行，呼叫端不需要自己持有 executor。
     * 輸入：
     * - workspaceId: long
     * - dir: File
     * - prompt: String
     * - callback: java.util.function.Consumer<ExecutionResult> - (可選後處理)
     * 輸出：無
     * 限制：呼叫方需負責傳入合法的 callback，其不可拋出影響核心執行的異常
     * 副作用：提交非同步任務至 workspaceCommandExecutor
     */
    public void executeAsync(long workspaceId, File dir, String prompt, String command, boolean init, java.util.function.Consumer<ExecutionResult> callback) {
        workspaceCommandExecutor.execute(() -> {
            ExecutionResult result = execute(workspaceId, dir, prompt, command, init);
            if (callback != null) {
                try {
                    callback.accept(result);
                } catch (Exception e) {
                    log.error("[ExecuteService] executeAsync callback 執行失敗: historyId={}", result.historyId(), e);
                }
            }
        });
    }

    // ==========================================
    // 高層方法：各情境的前後處理
    // ==========================================

    /**
     * 目的：在指定 Workspace 下非同步執行 Gemini CLI 指令。
     * 輸入：
     * - workspace: Workspace
     * - command: String
     * 輸出：無
     * 限制：workspace 目錄需存在
     * 副作用：非同步建立 History + 執行 CLI
     */
    public void run(Workspace workspace, String command) {
        log.info("[ExecuteService] 開始執行指令: workspace={}, command={}", workspace.getName(), command);
        File dir = new File(workspace.getAbsolutePath());

        // EDGE_CASE: Workspace 目錄可能已被外部刪除、移動，或不是目錄
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("[ExecuteService] Workspace 路徑無效或不是目錄: {}", workspace.getAbsolutePath());
            return;
        }

        String prompt = PromptBuilder.build(workspace, command);
        executeAsync(workspace.getId(), dir, prompt,command, false, null);
    }

    /**
     * 目的：專為 AgentCommandTask 設計的非同步執行方法。
     * WHY: 綁定 Task → execute → callback 更新 Task 狀態
     * 輸入：
     * - taskId: long
     * - workspace: Workspace
     * - command: String
     * 輸出：無
     * 限制：taskId 必須對應資料庫中存在的非終止任務
     * 副作用：綁定 Task、非同步執行、更新 Task 狀態
     */
    public void runAsyncFromAgent(long taskId, Workspace workspace, String command) {
        File dir = new File(workspace.getAbsolutePath());

        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("[ExecuteService] AgentCommandTask Workspace 路徑無效或不是目錄: {}", workspace.getAbsolutePath());
            updateAgentCommandTaskStatus(taskId, "FAILED");
            return;
        }

        String prompt = PromptBuilder.build(workspace, command);

        executeAsync(workspace.getId(), dir, prompt, command, false, result -> {
            // WHY: execute 完成後，先綁定 historyId 再更新 Task 狀態
            agentCommandTaskRepository.findById(taskId).ifPresent(task -> {
                task.setHistoryId(result.historyId());
                task.setStatus(result.status().name());
                task.setUpdateAt(java.time.LocalDateTime.now());
                agentCommandTaskRepository.save(task);
            });
            log.info("[ExecuteService] AgentCommandTask(taskId={}) 執行完成: historyId={}, status={}",
                    taskId, result.historyId(), result.status());
        });

        // WHY: 綁定 Task 為 RUNNING，不等待結果（callback 會更新最終狀態）
        agentCommandTaskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus("RUNNING");
            task.setUpdateAt(java.time.LocalDateTime.now());
            agentCommandTaskRepository.save(task);
        });
    }

    /**
     * 目的：BasicAgent 專用的非同步執行方法。
     * WHY: BasicAgent 透過 MCP Tool 回覆使用者，不需要同步等待結果。
     *      與 runAsyncFromAgent 類似，但不需要 Workspace 和 TaskId。
     * 輸入：
     * - dir: File - BasicAgent 工作目錄
     * - prompt: String - 完整提示詞
     * 輸出：無
     * 限制：工作目錄必須存在
     * 副作用：非同步建立 History + 執行 CLI
     */
    public void runWithBasicAgent(File dir, String prompt) {
        runWithBasicAgent(dir, prompt, false);
    }
    /**
     * 目的：BasicAgent 專用的非同步執行方法（支援初始化模式）。
     * 輸入：
     * - dir: File - BasicAgent 工作目錄
     * - prompt: String - 完整提示詞
     * - init: boolean - 是否為初始化執行（不加 --resume 參數）
     * 輸出：無
     * 限制：工作目錄必須存在
     * 副作用：非同步建立 History + 執行 CLI
     */
    public void runWithBasicAgent(File dir, String prompt, boolean init) {
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("[ExecuteService] BasicAgent 路徑無效或不是目錄: {}", dir.getAbsolutePath());
            throw new RuntimeException("BasicAgent workspace path does not exist or is not a directory");
        }

        executeAsync(BASIC_AGENT_WORKSPACE_ID, dir, prompt, prompt, init, null);
    }

    /**
     * 目的：更新 AgentCommandTask 的狀態。
     * 輸入：
     * - taskId: long - 任務 ID
     * - statusStr: String - 狀態字串 (COMPLETED, FAILED, TERMINATED 等)
     * 輸出：無
     * 限制：若 taskId 對應紀錄不存在則無作用
     * 副作用：更新資料庫
     */
    private void updateAgentCommandTaskStatus(long taskId, String statusStr) {
        // WHY: 同狀態（COMPLETED, FAILED 等）同步回 AgentCommandTask，供 Heartbeat 使用
        agentCommandTaskRepository.findById(taskId).ifPresent(task -> {
            String finalStatus = "TERMINATED".equals(statusStr) ? "FAILED" : statusStr;
            task.setStatus(finalStatus);
            task.setUpdateAt(java.time.LocalDateTime.now());
            agentCommandTaskRepository.save(task);
        });
    }

    // ==========================================
    // 進程控制
    // ==========================================

    /**
     * 目的：主動終止正在執行中的指令。
     * 輸入：
     * - historyId: long
     * 輸出：無
     * 限制：不可逆地強行中斷執行緒
     * 副作用：終止子程序並標記狀態為 TERMINATED
     */
    public void stopProcess(long historyId) {
        Process process = runningProcesses.get(historyId);
        if (process != null && process.isAlive()) {
            log.info("[ExecuteService] 正在終止指令: historyId={}", historyId);
            terminatedHistoryIds.add(historyId);
            process.destroy();
            historyService.updateHistoryStatus(historyId, HistoryStatus.TERMINATED);
            runningProcesses.remove(historyId);
        } else {
            log.warn("[ExecuteService] 找不到正在執行的指令或指令已結束: historyId={}", historyId);
        }
    }

    private HistoryStatus resolveFinalStatus(long historyId, HistoryStatus fallbackStatus) {
        if (terminatedHistoryIds.contains(historyId)) {
            return HistoryStatus.TERMINATED;
        }
        return historyService.getHistory(historyId)
                .map(History::getStatus)
                .filter(current -> current == HistoryStatus.TERMINATED)
                .orElse(fallbackStatus);
    }

    // ==========================================
    // CLI 子程序管理
    // ==========================================

    /**
     * 目的：實際呼叫 Gemini CLI 子程序並收集輸出。
     * WHY: 統一的底層 CLI 呼叫，所有路徑（Workspace / BasicAgent）共用。
     */
    private int runCli(History history, File dir, String prompt , boolean init) {
        String[] cmd = buildCommand(prompt, init);
        int exitValue = -1;
        try {
            Process process = commandExecutor.execute(cmd, dir);
            log.info("[ExecuteService] 啟動 Gemini CLI 子程序: dir={}, pid={}", dir.getAbsolutePath(), process.pid());
            Future<Void> stdout = readStreamAsync(history.getId(), ResultTextType.STDOUT, process.getInputStream());
            Future<Void> stderr = readStreamAsync(history.getId(), ResultTextType.STDERR, process.getErrorStream());
            // 註冊進程，以便後續中止
            runningProcesses.put(history.getId(), process);
            //等待 process 執行結束
            exitValue = process.waitFor();
            log.info("[ExecuteService] Gemini CLI 子程序結束: dir={}, pid={}, exitValue={}", dir.getAbsolutePath(), process.pid(), exitValue);
            stdout.get(STREAM_READER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            stderr.get(STREAM_READER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IOException e) {
            log.error("[ExecuteService] CLI 啟動失敗: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start CLI process", e);
        } catch (InterruptedException e) {
            log.error("[ExecuteService] CLI 執行被中斷: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("CLI execution interrupted", e);
        } catch (ExecutionException e) {
            log.error("[ExecuteService] 流讀取執行失敗: {}", e.getMessage(), e);
            if (!(e.getCause() instanceof NullPointerException)) {
                throw new RuntimeException("Stream reading failed", e);
            }
        } catch (TimeoutException e) {
            log.error("[ExecuteService] 流讀取等待超時: {}", e.getMessage(), e);
            throw new RuntimeException("Stream reading timeout", e);
        } finally {
            runningProcesses.remove(history.getId());
        }

        return exitValue;
    }

    /**
     * 目的：非同步讀取 InputStream 到 DB, 並用時間控制寫入的頻率
     * 輸入：
     * - resultTextId: long
     * - inputStream: InputStream
     * - streamName: String
     * 輸出：Future<Void> - 非同步工作票據
     * 限制：inputStream 需保持開啟直到任務結束
     * 副作用：長時間佔用 streamReaderExecutor，並定期寫入資料庫
     */
    private Future<Void> readStreamAsync(
            long historyId,
            ResultTextType type,
            InputStream inputStream) {
        return streamReaderExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            StringBuilder buffer = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                    long now = System.currentTimeMillis();
                    if (now - start >= DEFAULT_FLUSH_INTERVAL_SECONDS * 1000) {
                        if (!buffer.isEmpty()) {
                            historyService.appendHistoryLog(historyId,type, buffer.toString());
                            buffer.setLength(0);
                        }
                        start = now;
                    }
                }
                if (!buffer.isEmpty()) {
                    historyService.appendHistoryLog(historyId,type, buffer.toString());
                    buffer.setLength(0);
                }
                log.debug("[ExecuteService] {} 讀取完成", type.name());
            } catch (IOException e) {
                log.error("[ExecuteService] 讀取 {} 時發生錯誤", type.name(), e);
            }
            return null;
        });
    }

    /**
     * 目的：組裝 Gemini CLI 指令列。
     *
     * WHY: 將 CLI 指令組裝邏輯集中管理，方便未來修改參數或增加選項。
     * CLI 參數說明：
     * - --approval-mode=yolo: 允許 CLI 自行決定是否需要求使用者批准，適合自動化場景
     * - --resume: 允許 CLI 從上次中斷的地方繼續執行。保留之前的輸出結果。
     * - --sandbox: 啟用沙箱模式，限制 CLI 的系統訪問權限
     * - --output-format json: 讓 CLI 以 JSON 格式輸出結果，方便後續解析。
     * - --prompt: 後面接完整提示詞，作為 CLI 執行的輸入。
     * 輸入：
     * - prompt: String - 完整提示詞
     * 輸出：String[] - 完整的 CLI 指令陣列
     * 限制：無
     * 副作用：無
     */
    String[] buildCommand(String prompt, boolean init) {
        List<String> cmd = new ArrayList<>();
        cmd.add(GEMINI_CLI_CMD);
        cmd.add("--approval-mode=yolo");
        if (! init) {
            cmd.add("--resume");
        }
        cmd.add("--sandbox");
        cmd.add("--output-format");
        cmd.add("json");
        cmd.add("--prompt");
        cmd.add(prompt);
        return cmd.toArray(new String[0]);
    }
}

/* ### Review Checklist ###
 * 1. 一般化：統一 execute() 處理所有 CLI 執行 ✓
 * 2. 非同步管理：workspaceCommandExecutor 內建於 ExecuteService ✓
 * 3. 回傳結構：ExecutionResult record 讓呼叫端可做後處理 ✓
 * 4. 進程追蹤：runningProcesses + terminatedHistoryIds 確保中止功能 ✓
 * 5. 職責分離：各高層方法只做情境前後處理 ✓
 */
