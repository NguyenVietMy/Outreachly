package com.pulse.pulse.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class EmbeddingService {

    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 1536;

    private final WebClient webClient;
    private final PulseObservability observability;

    public EmbeddingService(WebClient.Builder webClientBuilder,
                            @Value("${OPENAI_API_KEY}") String apiKey,
                            PulseObservability observability) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.observability = observability;
    }

    public Mono<float[]> embed(String text) {
        return embedBatch(List.of(text)).map(list -> list.get(0));
    }

    public Mono<List<float[]>> embedBatch(List<String> texts) {
        return Mono.defer(() -> {
            Observation observation = observability.start("pulse.embedding.call");
            Timer.Sample sample = observability.timerSample();
            AtomicReference<String> result = new AtomicReference<>("unknown");

            observability.low(observation, "model", MODEL);
            observability.high(observation, "input.count", texts.size());
            observability.high(observation, "input.total_chars", texts.stream().mapToInt(String::length).sum());

            Map<String, Object> requestBody = Map.of(
                    "model", MODEL,
                    "input", texts,
                    "dimensions", DIMENSIONS
            );

            return Mono.using(
                    observation::openScope,
                    scope -> webClient.post()
                            .uri("/embeddings")
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(this::extractEmbeddings)
                            .doOnNext(embeddings -> {
                                result.set("success");
                                observability.high(observation, "output.count", embeddings.size());
                            })
                            .doOnError(error -> {
                                result.set("error");
                                observation.error(error);
                                log.error("OpenAI embedding error: ", error);
                            })
                            .doFinally(signal -> {
                                observability.low(observation, "result", result.get());
                                observability.counter("pulse.embedding.calls",
                                                "model", MODEL,
                                                "result", result.get())
                                        .increment();
                                observability.stopTimer(sample, "pulse.embedding.call.duration",
                                        "model", MODEL,
                                        "result", result.get());
                                observation.stop();
                            }),
                    Observation.Scope::close
            );
        });
    }

    private List<float[]> extractEmbeddings(JsonNode response) {
        JsonNode data = response.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new RuntimeException("No embeddings returned from OpenAI");
        }

        List<float[]> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.get("embedding");
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }
            embeddings.add(embedding);
        }
        return embeddings;
    }

    public static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
