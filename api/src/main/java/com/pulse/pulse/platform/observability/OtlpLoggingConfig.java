package com.pulse.pulse.platform.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "management.otlp.logging.export.enabled", havingValue = "true")
public class OtlpLoggingConfig {

    private final OpenTelemetry openTelemetry;
    private final String endpoint;
    private final String authorization;
    private final String serviceName;
    private final String serviceNamespace;
    private final String environment;

    public OtlpLoggingConfig(
            OpenTelemetry openTelemetry,
            @Value("${management.otlp.logging.export.endpoint}") String endpoint,
            @Value("${management.otlp.logging.export.headers.Authorization:}") String authorization,
            @Value("${management.opentelemetry.resource-attributes[service.name]:pulse}") String serviceName,
            @Value("${management.opentelemetry.resource-attributes[service.namespace]:pulse}") String serviceNamespace,
            @Value("${management.opentelemetry.resource-attributes[deployment.environment]:local}") String environment) {
        this.openTelemetry = openTelemetry;
        this.endpoint = endpoint;
        this.authorization = authorization;
        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;
        this.environment = environment;
    }

    @PostConstruct
    void installLogAppender() {
        var exporterBuilder = OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint);
        if (authorization != null && !authorization.isEmpty()) {
            exporterBuilder.addHeader("Authorization", authorization);
        }

        Resource resource = Resource.builder()
                .put(AttributeKey.stringKey("service.name"), serviceName)
                .put(AttributeKey.stringKey("service.namespace"), serviceNamespace)
                .put(AttributeKey.stringKey("deployment.environment"), environment)
                .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporterBuilder.build()).build())
                .build();

        OpenTelemetrySdk sdkWithLogs = OpenTelemetrySdk.builder()
                .setLoggerProvider(loggerProvider)
                .setPropagators(openTelemetry.getPropagators())
                .build();

        OpenTelemetryAppender.install(sdkWithLogs);
    }
}
