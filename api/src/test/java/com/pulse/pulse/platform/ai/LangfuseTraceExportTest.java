package com.pulse.pulse.platform.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.pulse.pulse.platform.config.LangfuseConfig;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sends one real Anthropic call through the real Langfuse exporter so the resulting trace can be
 * audited in Langfuse. Excluded from {@code mvn test}; run with {@code ./mvnw test -Plangfuse}.
 *
 * <p>Requires ANTHROPIC_API_KEY, LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY
 * in the environment.
 */
@Tag("langfuse")
class LangfuseTraceExportTest {

    @Test
    void exportsAGenerationTraceToLangfuse() {
        String apiKey = requireEnv("ANTHROPIC_API_KEY");
        String host = requireEnv("LANGFUSE_HOST");
        String publicKey = requireEnv("LANGFUSE_PUBLIC_KEY");
        String secretKey = requireEnv("LANGFUSE_SECRET_KEY");

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "pulse-api"))))
                .addSpanProcessor(new LangfuseConfig()
                        .langfuseSpanProcessor(host, publicKey, secretKey))
                .build();

        try (OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()) {

            OtelTracer tracer = new OtelTracer(
                    sdk.getTracer("pulse-langfuse-verification"),
                    new OtelCurrentTraceContext(),
                    event -> {
                    });

            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig()
                    .observationHandler(new DefaultTracingObservationHandler(tracer));

            AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
            AnthropicService service = new AnthropicService(
                    client,
                    new PulseObservability(registry, new SimpleMeterRegistry()));

            assertNotNull(service.generateDigest(
                    "Opened 3 pull requests and reviewed 2 more.").block());

            // Resume scoring is the sensitive path: this text is deliberately full of PII so the
            // exported trace can be audited for leakage.
            assertNotNull(service.scoreResume(
                    "Dana Whitfield, dana.whitfield@example.com, (555) 0147.\n"
                            + "Senior Software Engineer, 6 years. Java, Spring Boot, AWS.\n"
                            + "Led a 4-person team migrating a monolith to ECS Fargate.").block());

            tracerProvider.forceFlush().join(30, TimeUnit.SECONDS);
        }
    }

    private String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " must be set to run the Langfuse export verification");
        }
        return value;
    }
}
