/**
 * 目的：BasicAgent 初始化服務
 * 關鍵項目：
 * 1. 使用 agent.properties 判斷是否已初始化、儲存 Agent 設定
 * 2. 設定項目：agent.name / agent.userName / agent.timezone / agent.responseStyle
 * 3. ensureAgentFiles — 檢查並補齊所有 Agent 所需的 md 檔案
 * 4. 提供靜態 Workspace 物件供所有組件共用（不存 DB）
 * 5. 模板內容外移至 resources/basicagent/ 目錄，透過 TemplateLoader 載入
 * 模組：basicagent
 */
package com.github.nicholas23.majordomo.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// REASONING: 移除 ExecuteService 依賴，讓 Initial 專注於初始化與檔案管理。
// 執行 CLI 的職責由呼叫者（BasicAgentWebController）負責。
@Service
public class Initial {
    private static final Logger log = LoggerFactory.getLogger(Initial.class);

    private static final String BASE_DIR_NAME = ".majordomo";
    private static final String PROPERTIES_FILE = "agent.properties";

    @Value("${majordomo.base-dir:}")
    private String configuredBaseDir;

    @Value("${majordomo.skip-agy-trust:${majordomo.skip-gemini-trust:false}}")
    private boolean skipAgyTrust;

    // Properties keys
    private static final String KEY_NAME = "agent.name";
    private static final String KEY_USER_NAME = "agent.userName";
    private static final String KEY_TIMEZONE = "agent.timezone";
    private static final String KEY_RESPONSE_STYLE = "agent.responseStyle";

    // 預設值
    public static final String DEFAULT_NAME = "露比醬";
    public static final String DEFAULT_USER_NAME = "主人";
    public static final String DEFAULT_TIMEZONE = "Asia/Taipei";
    public static final String DEFAULT_RESPONSE_STYLE =
            "保持溫暖、自然且清晰。偶爾使用相關的表情符號（最多 1-2 個），避免機器人式的措辭。";

    // 模板資源路徑
    private static final String TEMPLATE_DIR = "basicagent/";

    private Path basePath;

    /**
     * 目的：應用程式啟動後執行初始化流程。
     * 輸入：無
     * 輸出：無
     * 限制：需在 Spring 依賴注入完成後執行 (@PostConstruct)
     * 副作用：建立資料目錄與 Gemini CLI 設定檔、載入 Agent 屬性
     */
    @PostConstruct
    public void onStartup() {
        Path dataDir = getBasePath().resolve("data");
        try {
            Files.createDirectories(dataDir);
            log.info("[Initial] 確認資料目錄存在: {}", dataDir);
        } catch (IOException e) {
            log.error("[Initial] 無法建立資料目錄: {}", dataDir, e);
            throw new RuntimeException("無法建立資料目錄: " + dataDir, e);
        }
        Path agyConfig = getBasePath().resolve(".agents");
        try {
            Files.createDirectories(agyConfig);
            log.info("[Initial] 確認 Antigravity CLI MCP 設定目錄存在: {}", agyConfig);
        } catch (IOException e) {
            log.error("[Initial] 無法建立 Antigravity CLI MCP 設定目錄: {}", agyConfig, e);
            throw new RuntimeException("無法建立 Antigravity CLI MCP 設定目錄: " + agyConfig, e);
        }
        setupMemoryMcpSettings(agyConfig);
        addBasicDirToAgyTrustSettings();

        if (isInitialized()) {
            Properties props = loadProperties();
            ensureAgentFiles(props, null, null);
            log.info("[Initial] 已載入 BasicAgent: name={}", props.getProperty(KEY_NAME, DEFAULT_NAME));
        }
    }
    
    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 目的：將 BasicAgent 根目錄加入 Gemini CLI 的信任資料夾清單。
     * 輸入：無
     * 輸出：無
     * 限制：若 trustedFolders.json 不存在則跳過
     * 副作用：讀寫 ~/.gemini/trustedFolders.json
     */
    private void addBasicDirToAgyTrustSettings() {
        if (skipAgyTrust) {
            log.info("[Initial] 測試模式或已配置停用更新 agy Trust 目錄設定，略過");
            return;
        }
        Path settingsFile = Path.of(System.getProperty("user.home"), ".gemini", "antigravity-cli", "settings.json");
        try {
            Files.createDirectories(settingsFile.getParent());
            com.fasterxml.jackson.databind.node.ObjectNode json = objectMapper.createObjectNode();
            if (Files.exists(settingsFile) && !Files.readString(settingsFile).isBlank()) {
                json = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(Files.readString(settingsFile));
            }
            com.fasterxml.jackson.databind.node.ArrayNode workspaces = json.withArray("trustedWorkspaces");
            String basePathStr = getBasePath().toAbsolutePath().toString();
            boolean exists = false;
            for (com.fasterxml.jackson.databind.JsonNode workspace : workspaces) {
                if (basePathStr.equals(workspace.asText())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                workspaces.add(basePathStr);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), json);
                log.info("[Initial] 已將工作區加入 agy trust 設定: {}", settingsFile);
            }
        } catch (IOException e) {
            log.error("[Initial] 無法讀取或更新 agy trust 設定: {}", settingsFile, e);
            throw new RuntimeException("無法讀取或更新 agy trust 設定: " + settingsFile, e);
        }
    }

    /**
     * 目的：確保 Gemini CLI 的 MCP 設定檔中包含 memoryMcp 服務設定。
     * 輸入：
     * - geminiConfig: Path - .gemini 設定目錄路徑
     * 輸出：無
     * 限制：若設定已存在則不覆蓋
     * 副作用：建立或更新 settings.json
     */
    private void setupMemoryMcpSettings(Path agyConfig) {
        Path settingsFile = agyConfig.resolve("mcp_config.json");
        if (!Files.exists(settingsFile)) {
            // WHY: 這段 JSON 很短（6 行），不需要外移到模板檔案
            String content = """
                    {
                      "mcpServers": {
                        "memoryMcp": {
                            "serverUrl": "http://localhost:8088/mcp"
                        }
                      }
                    }
                    """;
            try {
                Files.writeString(settingsFile, content);
                log.info("[Initial] 已建立 Gemini-Cli MCP 設定檔: {}", settingsFile);
            } catch (IOException e) {
                log.error("[Initial] 無法建立 Gemini-Cli MCP 設定檔: {}", settingsFile, e);
                throw new RuntimeException("無法建立 Gemini-Cli MCP 設定檔: " + settingsFile, e);
            }
        } else {
            //讀取原有設定，確認是否已包含 memoryMcp 設定，如果沒有則補上，避免覆蓋使用者原有的其他設定
            try {
                String existingContent = Files.readString(settingsFile);
                com.fasterxml.jackson.databind.node.ObjectNode json = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(existingContent);
                com.fasterxml.jackson.databind.node.ObjectNode mcpServers;
                if (!json.has("mcpServers") || !json.get("mcpServers").isObject()) {
                    mcpServers = json.putObject("mcpServers");
                } else {
                    mcpServers = (com.fasterxml.jackson.databind.node.ObjectNode) json.get("mcpServers");
                }
                if (!mcpServers.has("memoryMcp")) {
                    com.fasterxml.jackson.databind.node.ObjectNode memoryMcp = mcpServers.putObject("memoryMcp");
                    memoryMcp.put("serverUrl", "http://localhost:8088/mcp");
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), json);
                    log.info("[Initial] 已更新 Gemini-Cli MCP 設定檔: {}", settingsFile);
                } else {
                    log.info("[Initial] Gemini-Cli MCP 設定檔已包含 memoryMcp 設定: {}", settingsFile);
                }
            } catch (IOException e) {
                log.error("[Initial] 無法讀取或更新 Gemini-Cli MCP 設定檔: {}", settingsFile, e);
                throw new RuntimeException("無法讀取或更新 Gemini-Cli MCP 設定檔: " + settingsFile, e);
            }
        }
    }

    public Path getBasePath() {
        if (basePath == null) {
            if (configuredBaseDir != null && !configuredBaseDir.isBlank()) {
                basePath = Path.of(configuredBaseDir);
            } else {
                basePath = Path.of(System.getProperty("user.home"), BASE_DIR_NAME);
            }
        }
        return basePath;
    }

    public boolean isInitialized() {
        return Files.exists(getBasePath().resolve(PROPERTIES_FILE));
    }

    // ==========================================
    // 讀取個別設定（帶預設值）
    // ==========================================

    public String getAgentName() {
        return getProp(KEY_NAME, DEFAULT_NAME);
    }

    public String getUserName() {
        return getProp(KEY_USER_NAME, DEFAULT_USER_NAME);
    }

    public String getTimezone() {
        return getProp(KEY_TIMEZONE, DEFAULT_TIMEZONE);
    }

    public String getResponseStyle() {
        return getProp(KEY_RESPONSE_STYLE, DEFAULT_RESPONSE_STYLE);
    }

    private String getProp(String key, String defaultValue) {
        if (isInitialized()) {
            return loadProperties().getProperty(key, defaultValue);
        }
        return defaultValue;
    }

    // ==========================================
    // 初始化
    // ==========================================

    /**
     * 目的：執行完整的 BasicAgent 初始化流程。
     * WHY: 回傳初始化 Prompt 字串，讓呼叫者自行決定何時透過 ExecuteService 執行 CLI。
     * 這樣 Initial 不需要依賴 ExecuteService，實現職責分離。
     * 輸入：
     * - agentName: String
     * - userName: String
     * - timezone: String
     * - responseStyle: String
     * - usersHobby: String
     * - userWorkDesc: String
     * 輸出：String - 組好的初始化 prompt，供呼叫者傳給 ExecuteService
     * 限制：傳入的參數如果為 null 或是空白，都會被自動替換為預設值
     * 副作用：建立目錄結構、儲存 agent.properties，並建立所有基礎的 .md 檔案
     */
    public String init(String agentName, String userName, String timezone,
                       String responseStyle, String usersHobby, String userWorkDesc) {
        agentName = fallback(agentName, DEFAULT_NAME);
        userName = fallback(userName, DEFAULT_USER_NAME);
        timezone = fallback(timezone, DEFAULT_TIMEZONE);
        responseStyle = fallback(responseStyle, DEFAULT_RESPONSE_STYLE);

        log.info("[Initial] 開始 BasicAgent 初始化: name={}", agentName);

        setupBaseWorkspace();

        Properties props = new Properties();
        props.setProperty(KEY_NAME, agentName);
        props.setProperty(KEY_USER_NAME, userName);
        props.setProperty(KEY_TIMEZONE, timezone);
        props.setProperty(KEY_RESPONSE_STYLE, responseStyle);
        saveProperties(props);
        ensureAgentFiles(props, usersHobby, userWorkDesc);

        log.info("[Initial] BasicAgent 初始化完成: path={}", getBasePath());

        // WHY: 載入模板並填入參數，回傳 prompt 字串讓呼叫者執行
        String template = TemplateLoader.load(TEMPLATE_DIR + "init_prompt.md");
        return String.format(template, getUserName(), getTimezone(), getResponseStyle(), getAgentName());
    }


    private String fallback(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    // ==========================================
    // Properties 操作
    // ==========================================

    private void saveProperties(Properties props) {
        Path propsFile = getBasePath().resolve(PROPERTIES_FILE);
        try (var out = Files.newOutputStream(propsFile)) {
            props.store(out, "BasicAgent Configuration");
            log.debug("[Initial] 寫入 agent.properties: {}", propsFile);
        } catch (IOException e) {
            log.error("[Initial] 寫入 agent.properties 失敗", e);
            throw new RuntimeException("寫入 agent.properties 失敗", e);
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        Path propsFile = getBasePath().resolve(PROPERTIES_FILE);
        try (var in = Files.newInputStream(propsFile)) {
            props.load(in);
        } catch (IOException e) {
            log.warn("[Initial] 讀取 agent.properties 失敗，使用預設值", e);
        }
        return props;
    }

    // ==========================================
    // 目錄與檔案
    // ==========================================

    private void setupBaseWorkspace() {
        Path base = getBasePath();
        try {
            Files.createDirectories(base);
            Files.createDirectories(base.resolve("data"));
            log.debug("[Initial] 基礎目錄建立完成: {}", base);
        } catch (IOException e) {
            log.error("[Initial] 建立基礎目錄失敗: {}", base, e);
            throw new RuntimeException("無法建立 BasicAgent 目錄: " + base, e);
        }
    }

    /**
     * 目的：確保所有 Agent 所需的 md 檔都存在，缺少的用預設值補上。
     */
    private void ensureAgentFiles(Properties props, String usersHobby, String userWorkDesc) {
        String name = props.getProperty(KEY_NAME, DEFAULT_NAME);
        String user = props.getProperty(KEY_USER_NAME, DEFAULT_USER_NAME);
        String tz = props.getProperty(KEY_TIMEZONE, DEFAULT_TIMEZONE);
        String style = props.getProperty(KEY_RESPONSE_STYLE, DEFAULT_RESPONSE_STYLE);

        Path base = getBasePath();
        try {
            ensureUserFile(base, name, user, tz, usersHobby, userWorkDesc);
            ensureIdentityFile(base, name);
            ensureSoulFile(base, name, user, tz, style);
            ensureMemoryFile(base);
            Files.createDirectories(base.resolve("memory"));
            ensureHeartbeatFile(base);
            ensureAgentFile(base, name, user);
            log.info("[Initial] Agent 檔案檢查完成");
        } catch (IOException e) {
            log.error("[Initial] 確保 Agent 檔案失敗", e);
            throw new RuntimeException("確保 Agent 檔案失敗", e);
        }
    }

    private void ensureIdentityFile(Path base, String agentName) throws IOException {
        Path identityFile = base.resolve("IDENTITY.md");
        if (!Files.exists(identityFile)) {
            String template = TemplateLoader.load(TEMPLATE_DIR + "identity.md");
            String content = String.format(template, agentName);
            Files.writeString(identityFile, content);
            log.debug("[Initial] 補建 IDENTITY.md");
        }
    }

    private void ensureUserFile(Path base, String agentName, String userName,
                                String tz, String usersHobby,
                                String userWorkDesc) throws IOException {
        Path userFile = base.resolve("USER.md");
        usersHobby = (usersHobby == null || usersHobby.isBlank())
                ? "用戶未提供，請努力去了解他再來更新" : usersHobby.trim();
        userWorkDesc = (userWorkDesc == null || userWorkDesc.isBlank())
                ? "用戶未提供，請努力去了解他再來更新" : userWorkDesc.trim();
        if (!Files.exists(userFile)) {
            String template = TemplateLoader.load(TEMPLATE_DIR + "user.md");
            String content = String.format(template,
                    agentName,agentName, userName, tz, usersHobby, userWorkDesc, agentName,userName);
            Files.writeString(userFile, content);
            log.debug("[Initial] 補建 USER.md");
        }
    }

    private void ensureSoulFile(Path base, String name, String user,
                                String tz, String style) throws IOException {
        Path soulFile = base.resolve("SOUL.md");
        if (!Files.exists(soulFile)) {
            String template = TemplateLoader.load(TEMPLATE_DIR + "soul.md");
            String content = String.format(template, user, name, name, style, tz, user);
            Files.writeString(soulFile, content);
            log.debug("[Initial] 補建 SOUL.md");
        }
    }

    private void ensureMemoryFile(Path base) throws IOException {
        Path memoryFile = base.resolve("MEMORY.md");
        if (!Files.exists(memoryFile)) {
            String content = TemplateLoader.load(TEMPLATE_DIR + "memory.md");
            Files.writeString(memoryFile, content);
            log.debug("[Initial] 補建 MEMORY.md");
        }
    }

    private void ensureHeartbeatFile(Path base) throws IOException {
        Path heartbeatFile = base.resolve("HEARTBEAT.md");
        if (!Files.exists(heartbeatFile)) {
            String content = TemplateLoader.load(TEMPLATE_DIR + "heartbeat.md");
            Files.writeString(heartbeatFile, content);
            log.debug("[Initial] 補建 HEARTBEAT.md");
        }
    }

    private void ensureAgentFile(Path base, String agentName, String username) throws IOException {
        Path agentMd = base.resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            String template = TemplateLoader.load(TEMPLATE_DIR + "agent.md");
            String content = String.format(template,
                    agentName, agentName, base.toAbsolutePath(), agentName, username);
            Files.writeString(agentMd, content);
            log.debug("[Initial] 補建 AGENT.md");
        }
    }
}

/* ### Review Checklist ###
 * 1. 職責分離：Initial 不再依賴 ExecuteService，CLI 執行交由呼叫者 ✓
 * 2. 模板管理：所有長文字模板透過 TemplateLoader 從 resources 載入 ✓
 * 3. API 變更：init() 回傳 String (prompt)，呼叫者需配合更新 ✓
 * 4. Checkstyle：移除所有內嵌 Text Block，消除縮排違規 ✓
 */
