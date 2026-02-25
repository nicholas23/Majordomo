/**
 * 目的：Telegram 設定的加密持久化服務
 * 關鍵項目：
 * 1. 將 botToken 與 allowedUser 加密儲存至 ~/.majordomo/telegram.properties
 * 2. 使用 AES-256/GCM 加密，金鑰取自 user.home 的 SHA-256 雜湊
 * 3. 提供遮罩顯示方法供 Web UI 使用
 * 模組：telegram
 */
package com.github.nicholas23.majordomo.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.github.nicholas23.majordomo.agent.Initial;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Telegram 設定服務：加密讀寫 botToken / allowedUser。
 */
@Service
public class TelegramSettingsService {

    private static final Logger log = LoggerFactory.getLogger(TelegramSettingsService.class);

    private static final String PROPS_FILE = "telegram.properties";
    private static final String KEY_BOT_TOKEN = "botToken";
    private static final String KEY_ALLOWED_USER = "allowedUser";

    // AES-256/GCM 參數
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MASK_VISIBLE_CHARS = 4;

    private final Initial initialService;

    public TelegramSettingsService(Initial initialService) {
        this.initialService = initialService;
    }

    /**
     * 目的：檢查 telegram.properties 是否已存在且包含設定。
     * 輸入：無
     * 輸出：boolean - true 表示已設定
     * 限制：無
     * 副作用：無
     */
    public boolean isConfigured() {
        Path propsFile = initialService.getBasePath().resolve(PROPS_FILE);
        if (!Files.exists(propsFile)) {
            return false;
        }
        Properties props = loadRawProperties();
        return props.containsKey(KEY_BOT_TOKEN) && props.containsKey(KEY_ALLOWED_USER);
    }

    /**
     * 目的：儲存加密後的 Telegram 設定。
     * 輸入：
     * - botToken: String - 明文 Bot Token
     * - allowedUser: String - 明文 allowedUser ID
     * 輸出：無
     * 限制：無
     * 副作用：讀寫 ~/.majordomo/telegram.properties
     */
    public void save(String botToken, String allowedUser) {
        Properties props = new Properties();
        props.setProperty(KEY_BOT_TOKEN, encrypt(botToken));
        props.setProperty(KEY_ALLOWED_USER, encrypt(allowedUser));

        Path propsFile = initialService.getBasePath().resolve(PROPS_FILE);
        try (var out = Files.newOutputStream(propsFile)) {
            props.store(out, "Telegram Settings (encrypted)");
            log.info("[TelegramSettingsService] 已儲存加密的 Telegram 設定: {}", propsFile);
        } catch (IOException e) {
            log.error("[TelegramSettingsService] 寫入設定檔失敗: {}", propsFile, e);
            throw new RuntimeException("寫入 telegram.properties 失敗", e);
        }
    }

    /**
     * 目的：取得解密後的 botToken。
     * 輸入：無
     * 輸出：String - 明文 botToken，若未設定回傳空字串
     * 限制：無
     * 副作用：無
     */
    public String getBotToken() {
        return getDecryptedValue(KEY_BOT_TOKEN);
    }

    /**
     * 目的：取得解密後的 allowedUser。
     * 輸入：無
     * 輸出：String - 明文 allowedUser，若未設定回傳空字串
     * 限制：無
     * 副作用：無
     */
    public String getAllowedUser() {
        return getDecryptedValue(KEY_ALLOWED_USER);
    }

    /**
     * 目的：取得遮罩版本的 botToken，供 UI 安全顯示。
     * WHY: 避免在前端暴露完整 Token，僅顯示前 N 碼 + 遮罩。
     * 輸入：無
     * 輸出：String - 如 "8111****"，未設定回傳 "(未設定)"
     * 限制：無
     * 副作用：無
     */
    public String getMaskedBotToken() {
        String token = getBotToken();
        if (token.isEmpty()) {
            return "(未設定)";
        }
        if (token.length() <= MASK_VISIBLE_CHARS) {
            return "****";
        }
        return token.substring(0, MASK_VISIBLE_CHARS) + "****";
    }

    // ==========================================
    // 加密 / 解密
    // ==========================================

    /**
     * 目的：使用 AES-256/GCM 加密明文。
     * WHY: GCM 模式同時提供加密與完整性驗證，不需額外 HMAC。IV 隨機產生，與密文一起 Base64 編碼儲存。
     * 輸入：
     * - plainText: String - 待加密字串
     * 輸出：String - 包含 IV 與密文的 Base64 字串
     * 限制：若金鑰衍生或加密失敗會拋出 RuntimeException
     * 副作用：無
     */
    private String encrypt(String plainText) {
        try {
            SecretKeySpec keySpec = deriveKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // WHY: 將 IV 與密文合併後 Base64 編碼，解密時可分離
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            log.error("[TelegramSettingsService] 加密失敗", e);
            throw new RuntimeException("加密失敗", e);
        }
    }

    /**
     * 目的：解密 AES-256/GCM 密文。
     * 輸入：
     * - cipherText: String - 包含 IV 與密文的 Base64 字串
     * 輸出：String - 解密後的明文字串
     * 限制：若解密失敗（例如金鑰不符或資料篡改）會拋出 RuntimeException
     * 副作用：無
     */
    private String decrypt(String cipherText) {
        try {
            SecretKeySpec keySpec = deriveKey();
            byte[] combined = Base64.getDecoder().decode(cipherText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            log.error("[TelegramSettingsService] 解密失敗", e);
            throw new RuntimeException("解密失敗", e);
        }
    }

    /**
     * 目的：從 user.home 產生 AES-256 金鑰。
     * WHY: 不額外儲存金鑰，每次啟動由固定來源衍生，同一台機器同一帳號結果一致。
     * 輸入：無
     * 輸出：SecretKeySpec - AES 金鑰規格
     * 限制：無
     * 副作用：無
     */
    private SecretKeySpec deriveKey() {
        try {
            String seed = System.getProperty("user.home");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("金鑰衍生失敗", e);
        }
    }

    // ==========================================
    // Properties 操作
    // ==========================================

    /**
     * 目的：從屬性檔中讀出並解密指定鍵值的值。
     * 輸入：
     * - key: String - 設定項目鍵名
     * 輸出：String - 明文設定值，若無設定則為空字串
     * 限制：無
     * 副作用：無
     */
    private String getDecryptedValue(String key) {
        Properties props = loadRawProperties();
        String encrypted = props.getProperty(key);
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        return decrypt(encrypted);
    }

    /**
     * 目的：從 ~/.majordomo/telegram.properties 載入原始 Properties 物件。
     * 輸入：無
     * 輸出：Properties - 包含設定檔鍵值的物件
     * 限制：若讀取失敗不會中斷，回傳空的 Properties
     * 副作用：從硬碟讀出設定檔
     */
    private Properties loadRawProperties() {
        Properties props = new Properties();
        Path propsFile = initialService.getBasePath().resolve(PROPS_FILE);
        if (Files.exists(propsFile)) {
            try (var in = Files.newInputStream(propsFile)) {
                props.load(in);
            } catch (IOException e) {
                log.warn("[TelegramSettingsService] 讀取 telegram.properties 失敗", e);
            }
        }
        return props;
    }
}

/* ### Review Checklist ###
 * 1. 安全性：AES-256/GCM 加密，IV 隨機產生？ ✓
 * 2. 金鑰管理：從 user.home SHA-256 衍生，不額外儲存？ ✓
 * 3. 邊界處理：未設定時回傳空字串？ ✓
 * 4. 遮罩顯示：避免前端暴露完整 Token？ ✓
 * 5. 日誌記錄：不記錄明文敏感值？ ✓
 */
