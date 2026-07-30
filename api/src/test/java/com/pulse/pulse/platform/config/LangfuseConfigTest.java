package com.pulse.pulse.platform.config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LangfuseConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    OtlpTracingAutoConfiguration.class))
            .withUserConfiguration(LangfuseConfig.class)
            .withPropertyValues(
                    "langfuse.host=https://us.cloud.langfuse.com",
                    "langfuse.public-key=pk-lf-test",
                    "langfuse.secret-key=sk-lf-test");

    @Test
    void doesNotExportToLangfuseWhenDisabled() {
        runner.withPropertyValues("langfuse.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("langfuseSpanProcessor"));
    }

    @Test
    void exportsToLangfuseWhenEnabled() {
        runner.withPropertyValues("langfuse.enabled=true")
                .run(context -> assertThat(context).hasBean("langfuseSpanProcessor"));
    }

    /**
     * Boot's own OTLP exporter is {@code @ConditionalOnMissingBean(OtlpHttpSpanExporter.class)},
     * so contributing an exporter bean for Langfuse would silently disable the primary
     * tracing backend. Guards the {@link SpanProcessor}-based approach against regression.
     */
    @Test
    void primaryOtlpExporterSurvivesAlongsideLangfuse() {
        runner.withPropertyValues(
                        "langfuse.enabled=true",
                        "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces")
                .run(context -> {
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                    assertThat(context).hasBean("langfuseSpanProcessor");
                    assertThat(context.getBeansOfType(SpanProcessor.class)).hasSizeGreaterThan(1);
                });
    }
}
