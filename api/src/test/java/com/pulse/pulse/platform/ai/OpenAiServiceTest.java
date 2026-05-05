package com.pulse.pulse.platform.ai;

import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private TestObservationRegistry observationRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();
    }

    @Test
    void generateDigestRecordsSuccessMetricsAndObservation() {
        OpenAiService service = new OpenAiService(
                WebClient.builder().exchangeFunction(successExchange("{\"choices\":[{\"message\":{\"content\":\"done\"}}]}")),
                "test-key",
                new PulseObservability(observationRegistry, meterRegistry)
        );

        String response = service.generateDigest("abc").block();

        assertEquals("done", response);
        assertEquals(1.0, meterRegistry.get("pulse.openai.calls")
                .tag("operation", "digest")
                .tag("model", "gpt-3.5-turbo")
                .tag("result", "success")
                .counter()
                .count());
        assertEquals(1L, meterRegistry.get("pulse.openai.call.duration").timer().count());
        assertThat(observationRegistry).hasObservationWithNameEqualTo("pulse.openai.chat");
        assertThat(observationValues()).doesNotContain("abc", "done");
    }

    @Test
    void generateDigestRecordsErrorMetricsWhenCallFails() {
        OpenAiService service = new OpenAiService(
                WebClient.builder().exchangeFunction(request -> Mono.error(new RuntimeException("boom"))),
                "test-key",
                new PulseObservability(observationRegistry, meterRegistry)
        );

        assertThrows(RuntimeException.class, () -> service.generateDigest("abc").block());

        assertEquals(1.0, meterRegistry.get("pulse.openai.calls")
                .tag("operation", "digest")
                .tag("model", "gpt-3.5-turbo")
                .tag("result", "error")
                .counter()
                .count());
    }

    private ExchangeFunction successExchange(String body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build());
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
