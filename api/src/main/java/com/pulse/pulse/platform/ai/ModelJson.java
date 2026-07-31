package com.pulse.pulse.platform.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Parses JSON that came out of a model rather than off the wire.
 *
 * <p>Models are told to return bare JSON and mostly do, but two habits survive prompting: wrapping
 * the payload in a <code>```json</code> fence, and leaving a trailing comma before a closing bracket.
 * Strict Jackson rejects both, and the failure surfaces as a 500 at the call site — this is what
 * broke {@code /api/personal/suggestions/today} after the switch to Anthropic.
 *
 * <p>The leniency is deliberately kept off the injected Spring {@link ObjectMapper}: HTTP request
 * bodies come from clients, and they should keep being parsed strictly.
 */
public final class ModelJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private ModelJson() {
    }

    public static <T> T parse(String raw, TypeReference<T> type) throws JsonProcessingException {
        return MAPPER.readValue(stripFence(raw), type);
    }

    /**
     * Removes a surrounding markdown code fence, if there is one. Anything else is returned trimmed
     * and otherwise untouched, so well-behaved responses pass straight through.
     */
    static String stripFence(String raw) {
        String text = raw.strip();
        if (!text.startsWith("```")) {
            return text;
        }
        // Drop the opening fence line, which carries the optional language tag (```json).
        int endOfFenceLine = text.indexOf('\n');
        if (endOfFenceLine < 0) {
            return text;
        }
        String body = text.substring(endOfFenceLine + 1).strip();
        return body.endsWith("```") ? body.substring(0, body.length() - 3).strip() : body;
    }
}
