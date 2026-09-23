package com.github.nicholas23.majordomo.exec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgyCliJsonOutputParserTest {

    @Test
    void parsesHeadlessAgyOutput() {
        AgyCliJsonOutputParser.AgyOutput result = AgyCliJsonOutputParser.parseOutput("""
                {"conversation_id":"conv-1","status":"SUCCESS","response":"done",
                 "duration_seconds":1.5,"num_turns":2,"usage":{"total_tokens":42}}
                """);

        assertThat(result).isNotNull();
        assertThat(result.getConversationId()).isEqualTo("conv-1");
        assertThat(result.getResponse()).isEqualTo("done");
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(42);
    }

    @Test
    void parsesStructuredErrorFromStderr() {
        AgyCliJsonOutputParser.AgyError result = AgyCliJsonOutputParser.parseError(
                "warning\\nAGY_ERROR: {\"status\":\"ERROR\",\"error_id\":\"err-1\",\"retryable\":true,\"message\":\"rate limited\"}");

        assertThat(result).isNotNull();
        assertThat(result.getErrorId()).isEqualTo("err-1");
        assertThat(result.isRetryable()).isTrue();
    }
}
