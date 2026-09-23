/**
 * 目的：執行歷史紀錄的業務邏輯層
 * 關鍵項目：
 * 1. 提供歷史紀錄的 CRUD 操作
 * 2. 封裝 HistoryRepository 及 HistoryResultRepository，對上層隱藏資料存取細節
 * 3. 承接原本在 Repository 中的物件組裝與兩步驟更新邏輯
 * 模組：history
 */
package com.github.nicholas23.majordomo.history;

import com.github.nicholas23.majordomo.exec.AgyCliJsonOutputParser;
import com.github.nicholas23.majordomo.exec.GeminiCliJsonOutputParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private final HistoryRepository historyRepository;
    private final ResultTextRepository resultTextRepository;

    public HistoryService(HistoryRepository historyRepository, ResultTextRepository resultTextRepository) {
        this.historyRepository = historyRepository;
        this.resultTextRepository = resultTextRepository;
    }

    /**
     * 目的：建立新的執行歷史紀錄。
     * 輸入：
     * - workspaceId: long
     * - command: String
     * 輸出：long - 新建紀錄的 ID
     * 限制：無
     * 副作用：寫入資料庫
     */
    @Transactional
    public History createHistory(long workspaceId, String command) {
        // Step 2: Create History
        History history = new History();
        history.setWorkspaceId(workspaceId);
        history.setCommand(command);
        history.setStartTime(LocalDateTime.now());
        history.setStatus(HistoryStatus.RUNNING);
        History saved = historyRepository.save(history);
        log.debug("[HistoryService] 建立歷史紀錄: id={}, workspaceId={}, command={}", saved.getId(), workspaceId, command);
        return saved;
    }

    /**
     * 目的：增量更新歷史紀錄的 output/error log。
     * 輸入：
     * - resultTextId: long
     * - text: String
     * 輸出：無
     * 限制：若 text 為空或 null 則略過更新
     * 副作用：更新 result_text
     */
    @Transactional
    public void appendHistoryLog(long historyId, ResultTextType type, String content) {
        if (content != null && !content.isEmpty()) {
            ResultText resultText = new ResultText();
            resultText.setHistoryId(historyId);
            resultText.setType(type);
            resultText.setCreateAt(LocalDateTime.now());
            resultText.setContent(content);
            resultTextRepository.save(resultText);
        }
    }

    /**
     * 目的：更新歷史紀錄的狀態與結束時間。
     * 輸入：
     * - historyId: long
     * - status: HistoryStatus
     * 輸出：無
     * 限制：無
     * 副作用：更新資料庫對應紀錄
     */
    @Transactional
    public void updateHistoryStatus(long historyId, HistoryStatus status) {
        historyRepository.findById(historyId).ifPresent(history -> {
            history.setEndTime(LocalDateTime.now());
            history.setStatus(status);
            historyRepository.save(history);
        });
        log.debug("[HistoryService] 更新歷史狀態: historyId={}, status={}", historyId, status);
    }

    /**
     * 目的：根據 ID 查詢單筆歷史紀錄。
     * 輸入：
     * - id: long
     * 輸出：Optional<History>
     * 限制：無
     * 副作用：無
     */
    public Optional<History> getHistory(long id) {
        return historyRepository.findById(id);
    }

    /**
     * 目的：根據 history id, ResultTextType type 取得所有執行結果內容文字（按時間排序），組合所有結果成為單一一個字串。
     * 輸入：
     * - resultId: long
     * 輸出：String - 結果內容，可能為 null
     * 限制：如果找不到對應紀錄會回傳 null
     * 副作用：無
     */
    public String getResultContent(long historyId, ResultTextType type) {
        return String.join("", resultTextRepository.findByHistoryIdAndTypeOrderByCreateAt(historyId, type));
    }

    /**
     * 目的：分頁列出指定 Workspace 的歷史紀錄。
     * 輸入：
     * - workspaceId: long
     * - pageable: Pageable
     * 輸出：Page<History>
     * 限制：無
     * 副作用：無
     */
    public Page<History> listHistory(long workspaceId, Pageable pageable) {
        log.debug("[HistoryService] 列出歷史紀錄: workspaceId={}, page={}", workspaceId, pageable.getPageNumber());
        return historyRepository.findByWorkspaceIdOrderByIdDesc(workspaceId, pageable);
    }

    /**
     * 目的：依據起始時間查詢所有 Workspace 的歷史紀錄，格式化後回傳字串列表（供 Heartbeat 注入 Agent）。
     * 輸入：
     * - since: LocalDateTime - 起始時間（null 表示不限時間）
     * 輸出：List<String> - 格式化後的歷史摘要列表
     * 限制：僅查詢 workspaceId > 0 的紀錄（排除 BasicAgent 自身的紀錄）
     * 副作用：無
     */
    public List<String> getWorkspaceHistories(LocalDateTime since) {
        List<History> histories;
        if (since == null) {
            histories = historyRepository.findByWorkspaceIdGreaterThan(0);
        } else {
            histories = historyRepository.findByWorkspaceIdGreaterThanAndEndTimeGreaterThanEqual(0, since);
        }
        return histories.stream()
                .map(h -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("工作區 ID: ").append(h.getWorkspaceId()).append("\n");
                    sb.append("記錄 ID: ").append(h.getId()).append("\n");
                    sb.append("指令: ").append(h.getCommand()).append("\n");
                    sb.append("狀態: ").append(h.getStatus().name()).append("\n");
                    String output = this.getResultContent(h.getId(), ResultTextType.STDOUT);
                    if (StringUtils.hasText(output)) {
                        String outputTrimmed = output.trim();
                        AgyCliJsonOutputParser.AgyOutput response = AgyCliJsonOutputParser.parseOutput(outputTrimmed);
                        if (response != null && StringUtils.hasText(response.getResponse())) {
                            sb.append("Output: ").append(response.getResponse());
                        } else if (response != null && StringUtils.hasText(response.getError())) {
                            sb.append("Output (Error): ").append(response.getError());
                        } else {
                            // Existing histories may have been created before the agy migration.
                            GeminiCliJsonOutputParser.GeminiCLiJsonResponse legacy = GeminiCliJsonOutputParser.parser(outputTrimmed);
                            if (legacy.getError() != null && StringUtils.hasText(legacy.getError().getMessage())
                                    && !legacy.getError().getMessage().startsWith("Failed to parse JSON:")) {
                                sb.append("Output (Error): ").append(legacy.getError().getMessage());
                            } else if (StringUtils.hasText(legacy.getResponse())) {
                                sb.append("Output: ").append(legacy.getResponse());
                            } else {
                                sb.append("Output: ").append(outputTrimmed.length() > 500 ? outputTrimmed.substring(0, 500) + "..." : outputTrimmed);
                            }
                        }
                    } else {
                        sb.append("Output: (empty)");
                    }
                    sb.append("\n");
                    return sb.toString();
                })
                .toList();
    }
}

/* ### Review Checklist ###
 * 1. 封裝性：上層不直接接觸 Repository？ ✓
 * 2. 日誌：寫入操作有記錄？ ✓
 * 3. Null 安全：getResultContent 可能回傳 null（呼叫端需注意）
 * 4. 資料一致性：updateHistory 的兩步驟操作非交易式（目前可接受）
 */
