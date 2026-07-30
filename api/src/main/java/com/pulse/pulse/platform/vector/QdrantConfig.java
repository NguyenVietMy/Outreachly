package com.pulse.pulse.platform.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Qdrant client bean, gated on {@code qdrant.enabled} so the app still boots where
 * QDRANT_URL is absent (CI, local runs that do not touch retrieval).
 */
@Configuration
@ConditionalOnProperty(name = "qdrant.enabled", havingValue = "true")
public class QdrantConfig {

    /** Qdrant serves gRPC on 6334; the URL Qdrant Cloud hands out is the REST one, on 6333. */
    private static final int DEFAULT_GRPC_PORT = 6334;

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(@Value("${qdrant.url}") String url,
                                     @Value("${qdrant.api-key}") String apiKey) {
        URI uri = URI.create(url);
        int port = uri.getPort() == -1 ? DEFAULT_GRPC_PORT : uri.getPort();

        return new QdrantClient(
                QdrantGrpcClient.newBuilder(uri.getHost(), port, "https".equals(uri.getScheme()))
                        .withApiKey(apiKey)
                        .build());
    }
}
