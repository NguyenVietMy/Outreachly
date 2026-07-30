package com.pulse.pulse.platform.config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Exports spans to Langfuse over OTLP/HTTP, in addition to whatever exporter
 * {@code management.otlp.tracing.*} is pointed at.
 *
 * <p>This contributes a {@link SpanProcessor} rather than a {@link OtlpHttpSpanExporter}
 * bean on purpose: Spring Boot's own OTLP exporter is declared
 * {@code @ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class})},
 * so exposing an exporter bean here would make Boot back off and silently stop
 * exporting to the primary backend. {@code SpanProcessor} beans are additive.
 */
@Configuration
@ConditionalOnProperty(name = "langfuse.enabled", havingValue = "true")
public class LangfuseConfig {

    @Bean
    public SpanProcessor langfuseSpanProcessor(@Value("${langfuse.host}") String host,
                                               @Value("${langfuse.public-key}") String publicKey,
                                               @Value("${langfuse.secret-key}") String secretKey) {
        String credentials = Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(host.replaceAll("/+$", "") + "/api/public/otel/v1/traces")
                .addHeader("Authorization", "Basic " + credentials)
                .addHeader("x-langfuse-ingestion-version", "4")
                .build();

        return BatchSpanProcessor.builder(exporter).build();
    }
}
