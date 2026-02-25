/**
 * 目的：執行歷史紀錄頁面的 Web 控制器
 * 關鍵項目：
 * 1. 提供 Workspace 的歷史紀錄列表 HTMX 片段
 * 2. 提供單筆歷史紀錄詳情（含執行結果內容）HTMX 片段
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import com.github.nicholas23.majordomo.history.ResultTextType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.github.nicholas23.majordomo.history.HistoryService;

@Controller
public class HistoryWebController {
    private static final Logger log = LoggerFactory.getLogger(HistoryWebController.class);

    private final HistoryService historyService;

    public HistoryWebController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * 目的：取得指定 Workspace 的歷史紀錄列表片段。
     * 輸入：
     * - id: Long - Workspace ID
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：預設只抓取最新 20 筆
     * 副作用：無
     */
    @GetMapping("/web/workspace/{id}/history")
    public String getHistoryTab(@PathVariable Long id, Model model) {
        log.debug("[HistoryWebController] 載入歷史紀錄: workspaceId={}", id);
        model.addAttribute("workspaceId", id);
        model.addAttribute("histories", historyService.listHistory(id, PageRequest.of(0, 20)).getContent());
        return "fragments/history_tab";
    }

    /**
     * 目的：取得單筆歷史紀錄的詳情片段（包含執行結果內容）。
     * 輸入：
     * - historyId: Long - 歷史紀錄 ID
     * - model: Model
     * 輸出：String - 片段模板名稱
     * 限制：若資料庫中查無此 historyId，仍會回傳模板，但內容可能為空
     * 副作用：無
     */
    @GetMapping("/web/history/{historyId}/detail")
    public String getHistoryDetail(@PathVariable Long historyId, Model model) {
        log.debug("[HistoryWebController] 載入歷史詳情: historyId={}", historyId);
        historyService.getHistory(historyId).ifPresent(history -> {
            model.addAttribute("history", history);
            model.addAttribute("outputContent", historyService.getResultContent(historyId, ResultTextType.STDOUT));
            model.addAttribute("errorContent", historyService.getResultContent(historyId, ResultTextType.STDERR));
        });
        return "fragments/history_detail";
    }
}

/* ### Review Checklist ###
 * 1. 安全性：historyId 路徑參數未做額外權限驗證（可能跨 Workspace 存取）
 * 2. 邊界情況：history 不存在時回傳空的 fragment？ ✓（ifPresent）
 */
