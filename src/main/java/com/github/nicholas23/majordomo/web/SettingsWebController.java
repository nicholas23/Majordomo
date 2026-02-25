/**
 * 目的：系統設定頁面的 Web 控制器
 * 關鍵項目：
 * 1. 提供 Telegram 設定的顯示頁面（botToken 遮罩顯示）
 * 2. 接收使用者輸入並委派 TelegramSettingsService 加密儲存
 * 3. 儲存後自動重新初始化 Telegram Bot 連線
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.nicholas23.majordomo.telegram.TelegramHandleService;
import com.github.nicholas23.majordomo.telegram.TelegramSettingsService;

/**
 * Settings 頁面控制器：管理 Telegram 等系統設定。
 */
@Controller
public class SettingsWebController {

    private static final Logger log = LoggerFactory.getLogger(SettingsWebController.class);

    private final TelegramSettingsService telegramSettingsService;
    private final TelegramHandleService telegramHandleService;

    public SettingsWebController(TelegramSettingsService telegramSettingsService,
                                 TelegramHandleService telegramHandleService) {
        this.telegramSettingsService = telegramSettingsService;
        this.telegramHandleService = telegramHandleService;
    }

    /**
     * 目的：顯示系統設定頁面。
     * 輸入：
     * - model: Model
     * 輸出：String - fragment 名稱
     * 限制：無
     * 副作用：無
     */
    @GetMapping("/web/settings")
    public String settingsPage(Model model) {
        model.addAttribute("telegramConfigured", telegramSettingsService.isConfigured());
        model.addAttribute("maskedBotToken", telegramSettingsService.getMaskedBotToken());
        model.addAttribute("allowedUser",
                telegramSettingsService.isConfigured() ? telegramSettingsService.getAllowedUser() : "");
        model.addAttribute("saved", false);
        return "fragments/settings";
    }

    /**
     * 目的：儲存 Telegram 設定並重建 Bot 連線。
     * WHY: 儲存後立即呼叫 reinitialize() 讓新設定生效，無需重啟。
     * 輸入：
     * - botToken: String
     * - allowedUser: String
     * - model: Model
     * 輸出：String - fragment 名稱
     * 限制：無
     * 副作用：加密寫入檔案、重建 Telegram Bot 連線
     */
    @PostMapping("/web/settings/telegram")
    public String saveTelegramSettings(@RequestParam String botToken,
                                       @RequestParam String allowedUser,
                                       Model model) {
        log.info("[SettingsWebController] 儲存 Telegram 設定");

        // EDGE_CASE: 若使用者未修改 botToken，表單可能送出遮罩值
        // 此時應保留原有 Token
        if (botToken.contains("****") && telegramSettingsService.isConfigured()) {
            botToken = telegramSettingsService.getBotToken();
        }

        telegramSettingsService.save(botToken, allowedUser);
        telegramHandleService.reinitialize();

        model.addAttribute("telegramConfigured", true);
        model.addAttribute("maskedBotToken", telegramSettingsService.getMaskedBotToken());
        model.addAttribute("allowedUser", allowedUser);
        model.addAttribute("saved", true);

        log.info("[SettingsWebController] Telegram 設定已更新並重新連線");
        return "fragments/settings";
    }
}

/* ### Review Checklist ###
 * 1. 安全性：botToken 不記錄明文到 log？ ✓
 * 2. 邊界處理：遮罩值送出時保留原 Token？ ✓
 * 3. 職責分離：Controller 不直接操作加密，委派 Service？ ✓
 * 4. 即時生效：儲存後呼叫 reinitialize()？ ✓
 */
