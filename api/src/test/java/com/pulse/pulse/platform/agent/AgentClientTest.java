package com.pulse.pulse.platform.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClientTest {

    /**
     * Captured verbatim from a live {@code POST /chat} against the running agent — this is the
     * shape the Java side has to keep decoding.
     */
    private static final String AGENT_RESPONSE = """
            {"message":"Based on your recent study notes, you have been focusing on Pulse.",
             "sources":[{"sourceType":"obsidian_diff","sourceKey":"2026-05-19","score":0.033333335}],
             "trajectory":[{"sourceType":"plan","decision":"retrieve","reason":"To find the most recent dated study notes."},
                           {"sourceType":"obsidian_diff","decision":"retrieved","reason":"1 chunks, top RRF 0.033"}]}
            """;

    @Test
    void decodesTheAgentResponseAndSendsTheInternalToken() throws IOException {
        AtomicReference<String> token = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            byte[] body = AGENT_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        try {
            AgentClient client = new AgentClient(WebClient.builder(),
                    "http://localhost:" + server.getAddress().getPort(), "dev", 5);

            AgentClient.AgentChatResponse response = client.chat(5L, "what have I studied?", List.of());

            assertEquals("dev", token.get());
            assertEquals(List.of(new AgentClient.Citation("obsidian_diff", "2026-05-19", 0.033333335)),
                    response.sources());
            assertEquals(2, response.trajectory().size());
            assertEquals(new AgentClient.TrajectoryStep("plan", "retrieve",
                    "To find the most recent dated study notes."), response.trajectory().get(0));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableAgentDegradesInsteadOfThrowing() {
        // Port 1 is never listening: the connection is refused before the timeout matters.
        AgentClient client = new AgentClient(WebClient.builder(), "http://localhost:1", "dev", 5);

        AgentClient.AgentChatResponse response = client.chat(1L, "how am I doing?", List.of());

        assertTrue(response.message().contains("temporarily unavailable"), response.message());
        assertEquals(List.of(), response.sources());
        assertEquals(List.of(), response.trajectory());
    }
}
