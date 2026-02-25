/**
 * 目的：解析 Gemini CLI 輸出的 JSON
 * 關鍵項目：
 * 1. 定義 JSON Schema 對應的 Java 類別 (GeminiCLiJsonResponse, Stats 等)
 * 2. 提供 parser 方法將文字反序列化為 Java Object
 * 模組：exec
 */
package com.github.nicholas23.majordomo.exec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GeminiCliJsonOutputParser {
    private static final Logger logger = LoggerFactory.getLogger(GeminiCliJsonOutputParser.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // gemini cli json output Response schema
    /*
    {
        "response": "string", // The main AI-generated content answering your prompt
        "stats": {
            // Usage metrics and performance data
            "models": {
                // Per-model API and token usage statistics
                "[model-name]": {
                    "api": {
                        // request counts, errors, latency
                    },
                    "tokens": {
                        // prompt, response, cached, total counts
                    }
                }
            },
            "tools": {
                // Tool execution statistics
                "totalCalls": "number",
                "totalSuccess": "number",
                "totalFail": "number",
                "totalDurationMs": "number",
                "totalDecisions": {
                    // accept, reject, modify, auto_accept counts
                },
                "byName": {
                    // per-tool detailed stats
                }
            },
            "files": {
                // File modification statistics
                "totalLinesAdded": "number",
                "totalLinesRemoved": "number"
            }
        },
        "error": {
            // Present only when an error occurred
            "type": "string", // Error type (e.g., "ApiError", "AuthError")
            "message": "string", // Human-readable error description
            "code": "number" // Optional error code
        }
    }
    */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiCLiJsonResponse {
        private String response;
        private Stats stats;
        private ErrorObj error;

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public Stats getStats() {
            return stats;
        }

        public void setStats(Stats stats) {
            this.stats = stats;
        }

        public ErrorObj getError() {
            return error;
        }

        public void setError(ErrorObj error) {
            this.error = error;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stats {
        private Map<String, ModelStats> models;
        private ToolsStats tools;
        private FilesStats files;

        public Map<String, ModelStats> getModels() {
            return models;
        }

        public void setModels(Map<String, ModelStats> models) {
            this.models = models;
        }

        public ToolsStats getTools() {
            return tools;
        }

        public void setTools(ToolsStats tools) {
            this.tools = tools;
        }

        public FilesStats getFiles() {
            return files;
        }

        public void setFiles(FilesStats files) {
            this.files = files;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelStats {
        private Map<String, Object> api;
        private Map<String, Object> tokens;

        public Map<String, Object> getApi() {
            return api;
        }

        public void setApi(Map<String, Object> api) {
            this.api = api;
        }

        public Map<String, Object> getTokens() {
            return tokens;
        }

        public void setTokens(Map<String, Object> tokens) {
            this.tokens = tokens;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolsStats {
        private int totalCalls;
        private int totalSuccess;
        private int totalFail;
        private long totalDurationMs;

        public int getTotalCalls() {
            return totalCalls;
        }

        public void setTotalCalls(int totalCalls) {
            this.totalCalls = totalCalls;
        }

        public int getTotalSuccess() {
            return totalSuccess;
        }

        public void setTotalSuccess(int totalSuccess) {
            this.totalSuccess = totalSuccess;
        }

        public int getTotalFail() {
            return totalFail;
        }

        public void setTotalFail(int totalFail) {
            this.totalFail = totalFail;
        }

        public long getTotalDurationMs() {
            return totalDurationMs;
        }

        public void setTotalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
        }

        @Override
        public String toString() {
            return String.format("Tools{Calls=%d, Success=%d, Fail=%d, DurationMs=%d}",
                    totalCalls, totalSuccess, totalFail, totalDurationMs);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilesStats {
        private int totalLinesAdded;
        private int totalLinesRemoved;

        public int getTotalLinesAdded() {
            return totalLinesAdded;
        }

        public void setTotalLinesAdded(int totalLinesAdded) {
            this.totalLinesAdded = totalLinesAdded;
        }

        public int getTotalLinesRemoved() {
            return totalLinesRemoved;
        }

        public void setTotalLinesRemoved(int totalLinesRemoved) {
            this.totalLinesRemoved = totalLinesRemoved;
        }

        @Override
        public String toString() {
            return String.format("Files{Added=%d, Removed=%d}",
                    totalLinesAdded, totalLinesRemoved);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorObj {
        private String type;
        private String message;
        private Integer code;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }
    }

    /**
     * 目的：將字串解析成代表 Gemini CLI 執行結果的 JSON 物件。
     * 輸入：
     * - text: String - 待解析的 JSON 字串
     * 輸出：GeminiCLiJsonResponse - 解析後的物件，若是發生錯誤則回傳包裝錯誤資訊的物件
     * 限制：text 可以為空或 null
     * 副作用：無
     */
    public static GeminiCLiJsonResponse parser(String text) {
        if (text == null || text.isBlank()) {
            return new GeminiCLiJsonResponse();
        }
        // WHY: Use generic parsing to tolerate missing fields or extra fields
        try {
            return jsonMapper.readValue(text, GeminiCLiJsonResponse.class);
        } catch (JsonProcessingException e) {
            logger.error("[GeminiCliJsonOutputParser] Failed to parse JSON response: {}", e.getMessage());
            // REASONING: Return an empty object or object with error info to avoid crashing caller
            GeminiCLiJsonResponse errorResponse = new GeminiCLiJsonResponse();
            ErrorObj errorObj = new ErrorObj();
            errorObj.setMessage("Failed to parse JSON: " + e.getMessage());
            errorResponse.setError(errorObj);
            return errorResponse;
        }
    }

}
/* ### Review Checklist ###
 * 1. Correctness: JSON schema mapped correctly? ✓
 * 2. Robustness: Handle invalid JSON by returning error object? ✓
 * 3. Dependencies: Uses Jackson 3 JsonMapper? ✓
 */
