package com.pulse.pulse.platform.agent;

import com.pulse.pulse.platform.config.LangfuseConfig;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.metrics.web.reactive.client.ObservationWebClientCustomizer;
import org.springframework.web.reactive.function.client.DefaultClientRequestObservationConvention;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives one real chat through {@link AgentClient} against the running agent, so the resulting
 * Langfuse trace can be checked for both halves: the Java {@code pulse.rag.chat} span and the
 * Python spans underneath it. Excluded from {@code mvn test}; run with
 * {@code ./mvnw test -Plangfuse -Dtest=AgentTracePropagationTest}.
 *
 * <p>The agent must already be listening on {@code AGENT_URL}, with Langfuse keys from the same
 * project — otherwise the two halves land in different projects and there is nothing to see.
 *
 * <p>Requires LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY in the environment.
 */
@Tag("langfuse")
class AgentTracePropagationTest {

    @Test
    void oneChatProducesOneTraceSpanningBothServices() {
        String host = requireEnv("LANGFUSE_HOST");
        String publicKey = requireEnv("LANGFUSE_PUBLIC_KEY");
        String secretKey = requireEnv("LANGFUSE_SECRET_KEY");
        String agentUrl = envOrDefault("AGENT_URL", "http://localhost:8001");
        String internalToken = envOrDefault("INTERNAL_TOKEN", "dev");
        long userId = Long.parseLong(envOrDefault("AGENT_TRACE_USER_ID", "5"));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "pulse-api"))))
                .addSpanProcessor(new LangfuseConfig()
                        .langfuseSpanProcessor(host, publicKey, secretKey))
                .build();

        try (OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                // W3C is what Boot configures by default, and what the agent extracts.
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build()) {

            io.opentelemetry.api.trace.Tracer otelTracer = sdk.getTracer("pulse-trace-propagation");
            OtelTracer tracer = new OtelTracer(
                    otelTracer,
                    new OtelCurrentTraceContext(),
                    event -> {
                    });

            ObservationRegistry registry = ObservationRegistry.create();
            // Both handlers support a sender context, so they are grouped exactly as Boot's
            // observation auto-configuration groups them: first match wins, otherwise the outbound
            // call would get two spans.
            registry.observationConfig().observationHandler(
                    new ObservationHandler.FirstMatchingCompositeObservationHandler(
                            new PropagatingSenderTracingObservationHandler<>(tracer,
                                    new OtelPropagator(sdk.getPropagators(), otelTracer)),
                            new DefaultTracingObservationHandler(tracer)));

            // Boot's own customizer, not a hand-rolled equivalent: this is the single step that
            // makes the WebClient emit client observations, which is what injects traceparent.
            WebClient.Builder builder = WebClient.builder();
            new ObservationWebClientCustomizer(registry,
                    new DefaultClientRequestObservationConvention()).customize(builder);

            AgentClient agentClient = new AgentClient(builder, agentUrl, internalToken, 120);
            PulseObservability observability =
                    new PulseObservability(registry, new SimpleMeterRegistry());

            AtomicReference<String> traceId = new AtomicReference<>();
            // Mirrors ChatService.chat: the agent call happens inside the pulse.rag.chat scope.
            AgentClient.AgentChatResponse response = observability.observe("pulse.rag.chat",
                    obs -> observability.high(obs, "user.id", userId),
                    () -> {
                        traceId.set(tracer.currentSpan().context().traceId());
                        return agentClient.chat(userId,
                                "Compare my GitHub projects against my roadmap and resume",
                                List.of());
                    });

            assertNotNull(response);
            // AgentClient swallows failures and returns DEGRADED, which would otherwise let this
            // test pass green while the agent was down and no Python spans existed at all.
            assertFalse(response.message().contains("temporarily unavailable"),
                    "agent was unreachable, so no cross-service trace was produced");

            tracerProvider.forceFlush().join(30, TimeUnit.SECONDS);
            System.out.println("langfuse trace id: " + traceId.get());
        }
    }

    private String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " must be set to run the trace propagation verification");
        }
        return value;
    }

    private String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
