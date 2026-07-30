package com.pulse.pulse.platform.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private TestObservationRegistry observationRegistry;
    private AnthropicClient client;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();
        client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void generateDigestRecordsSuccessMetricsAndObservation() {
        Message message = messageWithText("done");
        when(client.messages().create(any(MessageCreateParams.class))).thenReturn(message);

        AnthropicService service = new AnthropicService(
                client,
                new PulseObservability(observationRegistry, meterRegistry)
        );

        String response = service.generateDigest("abc").block();

        assertEquals("done", response);
        assertEquals(1.0, meterRegistry.get("pulse.ai.calls")
                .tag("operation", "digest")
                .tag("model", "claude-haiku-4-5")
                .tag("result", "success")
                .counter()
                .count());
        assertEquals(1L, meterRegistry.get("pulse.ai.call.duration").timer().count());
        assertThat(observationRegistry).hasObservationWithNameEqualTo("pulse.ai.chat");
        assertThat(observationValues()).doesNotContain("abc", "done");
    }

    @Test
    void generateDigestRecordsErrorMetricsWhenCallFails() {
        when(client.messages().create(any(MessageCreateParams.class))).thenThrow(new RuntimeException("boom"));

        AnthropicService service = new AnthropicService(
                client,
                new PulseObservability(observationRegistry, meterRegistry)
        );

        assertThrows(RuntimeException.class, () -> service.generateDigest("abc").block());

        assertEquals(1.0, meterRegistry.get("pulse.ai.calls")
                .tag("operation", "digest")
                .tag("model", "claude-haiku-4-5")
                .tag("result", "error")
                .counter()
                .count());
    }

    private Message messageWithText(String text) {
        TextBlock textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(text);
        ContentBlock block = mock(ContentBlock.class);
        when(block.text()).thenReturn(Optional.of(textBlock));
        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(block));
        return message;
    }

    private List<String> observationValues() {
        List<String> values = new ArrayList<>();
        assertThat(observationRegistry).hasHandledContextsThatSatisfy(contexts -> contexts.forEach(context ->
                StreamSupport.stream(context.getAllKeyValues().spliterator(), false)
                        .forEach(keyValue -> values.add(keyValue.getValue()))
        ));
        return values;
    }
}
