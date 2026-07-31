package com.pulse.pulse.platform.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelJsonTest {

    private static final TypeReference<List<Map<String, Object>>> TASKS = new TypeReference<>() {
    };

    @Test
    void parsesBareJson() throws JsonProcessingException {
        assertThat(ModelJson.parse("[{\"title\":\"a\"}]", TASKS))
                .containsExactly(Map.of("title", "a"));
    }

    /** The exact shape that broke /api/personal/suggestions/today: fenced *and* trailing comma. */
    @Test
    void parsesFencedJsonWithTrailingComma() throws JsonProcessingException {
        String raw = """
                ```json
                [
                  {
                    "title": "Master Big O Notation",
                    "priority": 0,
                  }
                ]
                ```""";

        assertThat(ModelJson.parse(raw, TASKS))
                .containsExactly(Map.of("title", "Master Big O Notation", "priority", 0));
    }

    @Test
    void stripsFenceWithoutLanguageTag() {
        assertThat(ModelJson.stripFence("```\n[1,2]\n```")).isEqualTo("[1,2]");
    }

    @Test
    void leavesUnfencedTextAlone() {
        assertThat(ModelJson.stripFence("  [1,2]  ")).isEqualTo("[1,2]");
    }

    /** A fence is only stripped when it wraps the payload — backticks inside a string stay put. */
    @Test
    void leavesInnerBackticksAlone() {
        assertThat(ModelJson.stripFence("[\"use ```json here\"]")).isEqualTo("[\"use ```json here\"]");
    }

    @Test
    void stillRejectsGenuinelyBrokenJson() {
        assertThatThrownBy(() -> ModelJson.parse("```json\nnot json at all\n```", TASKS))
                .isInstanceOf(JsonProcessingException.class);
    }
}
