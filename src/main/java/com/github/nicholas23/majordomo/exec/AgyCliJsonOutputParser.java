package com.github.nicholas23.majordomo.exec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses the structured output emitted by {@code agy --output-format json}. */
public final class AgyCliJsonOutputParser {
    private static final Logger log = LoggerFactory.getLogger(AgyCliJsonOutputParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgyCliJsonOutputParser() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgyOutput {
        @JsonProperty("conversation_id")
        private String conversationId;
        private String status;
        private String response;
        private String error;
        @JsonProperty("duration_seconds")
        private double durationSeconds;
        @JsonProperty("num_turns")
        private int numTurns;
        private Usage usage;

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }
        public int getNumTurns() { return numTurns; }
        public void setNumTurns(int numTurns) { this.numTurns = numTurns; }
        public Usage getUsage() { return usage; }
        public void setUsage(Usage usage) { this.usage = usage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private long inputTokens;
        @JsonProperty("output_tokens")
        private long outputTokens;
        @JsonProperty("thinking_tokens")
        private long thinkingTokens;
        @JsonProperty("cache_read_tokens")
        private long cacheReadTokens;
        @JsonProperty("total_tokens")
        private long totalTokens;

        public long getInputTokens() { return inputTokens; }
        public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
        public long getThinkingTokens() { return thinkingTokens; }
        public void setThinkingTokens(long thinkingTokens) { this.thinkingTokens = thinkingTokens; }
        public long getCacheReadTokens() { return cacheReadTokens; }
        public void setCacheReadTokens(long cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }
        public long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgyError {
        private String status;
        private Object code;
        private String message;
        private boolean retryable;
        @JsonProperty("error_id")
        private String errorId;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Object getCode() { return code; }
        public void setCode(Object code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public boolean isRetryable() { return retryable; }
        public void setRetryable(boolean retryable) { this.retryable = retryable; }
        public String getErrorId() { return errorId; }
        public void setErrorId(String errorId) { this.errorId = errorId; }
    }

    public static AgyOutput parseOutput(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, AgyOutput.class);
        } catch (JsonProcessingException e) {
            log.debug("[AgyCliJsonOutputParser] 無法解析 agy JSON 輸出: {}", e.getOriginalMessage());
            return null;
        }
    }

    /** Extracts the structured error line emitted by agy 1.2.6+ on stderr. */
    public static AgyError parseError(String text) {
        if (text == null) {
            return null;
        }
        int marker = text.indexOf("AGY_ERROR:");
        if (marker < 0) {
            return null;
        }
        String json = text.substring(marker + "AGY_ERROR:".length()).trim();
        int lineEnd = json.indexOf('\n');
        if (lineEnd >= 0) {
            json = json.substring(0, lineEnd);
        }
        try {
            return OBJECT_MAPPER.readValue(json, AgyError.class);
        } catch (JsonProcessingException e) {
            log.debug("[AgyCliJsonOutputParser] 無法解析 AGY_ERROR: {}", e.getOriginalMessage());
            return null;
        }
    }
}
