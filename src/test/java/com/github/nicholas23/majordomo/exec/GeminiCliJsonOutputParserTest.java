/**
 * Tests for GeminiCliJsonOutputParser.
 * Purpose: Verify the JSON parsing logic.
 * Key Items:
 * 1. Test parsing valid JSON.
 * 2. Test parsing malformed JSON.
 * 3. Test parsing empty/null input.
 * Module: exec
 */
package com.github.nicholas23.majordomo.exec;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class GeminiCliJsonOutputParserTest {

    @Test
    void testParser_ValidJson_ReturnsObject() {
        String json = """
            {
                "response": "Hello World",
                "stats": {
                    "tools": {
                        "totalCalls": 5
                    }
                }
            }
            """;
        
        GeminiCliJsonOutputParser.GeminiCLiJsonResponse result = GeminiCliJsonOutputParser.parser(json);
        
        assertThat(result).isNotNull();
        assertThat(result.getResponse()).isEqualTo("Hello World");
        assertThat(result.getStats()).isNotNull();
        assertThat(result.getStats().getTools().getTotalCalls()).isEqualTo(5);
    }

    @Test
    void testParser_MalformedJson_ReturnsErrorObject() {
        String json = "{ invalid json }";
        
        GeminiCliJsonOutputParser.GeminiCLiJsonResponse result = GeminiCliJsonOutputParser.parser(json);
        
        assertThat(result).isNotNull();
        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("Failed to parse JSON");
    }

    @Test
    void testParser_EmptyInput_ReturnsEmptyObject() {
        GeminiCliJsonOutputParser.GeminiCLiJsonResponse result = GeminiCliJsonOutputParser.parser("");
        
        assertThat(result).isNotNull();
        assertThat(result.getResponse()).isNull();
    }
}
/* ### Review Checklist ###
 * 1. Coverage: Valid, invalid, empty inputs covered? ✓
 * 2. Assertions: Verified key fields? ✓
 */
