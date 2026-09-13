package com.xiaoyang.aiticketplatform.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.config.AiServiceProperties;
import com.xiaoyang.aiticketplatform.dto.request.ai.AiAnalysisRequest;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAnalysisResponse;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestClientAiServiceClientTest {

    @Test
    void validResponseIsDeserialized() throws Exception {
        withServer((exchange) -> respond(exchange, 200,
                "{\"category\":\"ACCOUNT\",\"suggestedPriority\":\"HIGH\","
                        + "\"reason\":\"mock\",\"confidence\":0.9}"), client -> {
            AiAnalysisResponse response = client.analyze(request());
            assertEquals("ACCOUNT", response.category());
            assertEquals(TicketPriority.HIGH, response.suggestedPriority());
        });
    }

    @Test
    void malformedResponseMapsToInvalidResponseError() throws Exception {
        withServer((exchange) -> respond(exchange, 200, "not-json"), client -> {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> client.analyze(request()));
            assertEquals(ErrorCode.AI_RESPONSE_INVALID, exception.getErrorCode());
        });
    }

    @Test
    void upstreamFourHundredMapsToInvalidResponseError() throws Exception {
        withServer((exchange) -> respond(exchange, 422, "{\"detail\":\"invalid\"}"), client -> {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> client.analyze(request()));
            assertEquals(ErrorCode.AI_RESPONSE_INVALID, exception.getErrorCode());
        });
    }

    @Test
    void upstreamFiveHundredMapsToUnavailableError() throws Exception {
        withServer((exchange) -> respond(exchange, 500, "{\"detail\":\"failed\"}"), client -> {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> client.analyze(request()));
            assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, exception.getErrorCode());
        });
    }

    @Test
    void connectionRefusedMapsToUnavailableError() {
        AiServiceProperties properties = properties("http://127.0.0.1:1", Duration.ofMillis(100));
        RestClientAiServiceClient client = new RestClientAiServiceClient(
                httpClient(properties.getReadTimeout()), properties, new ObjectMapper());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.analyze(request()));
        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void readTimeoutMapsToUnavailableError() throws Exception {
        withServer((exchange) -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, "{}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, client -> {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> client.analyze(request()));
            assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, exception.getErrorCode());
        }, Duration.ofMillis(50));
    }

    private static AiAnalysisRequest request() {
        return new AiAnalysisRequest(10L, "title", "description", TicketPriority.MEDIUM);
    }

    private static void withServer(Handler handler, ClientAction action) throws Exception {
        withServer(handler, action, Duration.ofSeconds(2));
    }

    private static void withServer(Handler handler, ClientAction action, Duration timeout) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/ai/ticket-analysis", handler::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        try {
            AiServiceProperties properties = properties(
                    "http://127.0.0.1:" + server.getAddress().getPort(), timeout);
            RestClientAiServiceClient client = new RestClientAiServiceClient(
                    httpClient(timeout), properties, new ObjectMapper());
            action.run(client);
        } finally {
            server.stop(0);
        }
    }

    private static AiServiceProperties properties(String baseUrl, Duration timeout) {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(baseUrl);
        properties.setReadTimeout(timeout);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static HttpClient httpClient(Duration timeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @FunctionalInterface
    private interface ClientAction {
        void run(RestClientAiServiceClient client);
    }
}
