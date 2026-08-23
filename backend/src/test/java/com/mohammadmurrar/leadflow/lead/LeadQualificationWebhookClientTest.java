package com.mohammadmurrar.leadflow.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.qualification.api.QualificationDispatchRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadQualificationWebhookClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void acceptsOnlyAcceptedAcknowledgementWithoutReadingResponseBody() throws Exception {
        CapturedRequest captured = serve(202, "ignored response body");

        assertThat(client().send(request())).isTrue();
        assertRequestContract(captured);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204})
    void rejectsUnexpectedSuccessfulAcknowledgements(int status) throws Exception {
        String responseBody = status == 204 ? "" : "sensitive response content";
        serve(status, responseBody);

        var assertion = assertThatThrownBy(() -> client().send(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected webhook acknowledgement status: " + status);
        if (!responseBody.isEmpty()) assertion.hasMessageNotContaining(responseBody);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void preservesNonSuccessfulResponseHandling(int status) throws Exception {
        serve(status, "ignored error body");

        assertThatThrownBy(() -> client().send(request()))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void preservesConfiguredTransportTimeouts() {
        var client = new LeadQualificationWebhookClient("http://127.0.0.1:1/lead-qualification");
        Object restClient = ReflectionTestUtils.getField(client, "restClient");
        Object requestFactory = ReflectionTestUtils.getField(restClient, "clientRequestFactory");

        assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(5000);
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(15000);
    }

    private CapturedRequest serve(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext("/lead-qualification", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(exchange.getRequestBody().readAllBytes());
            send(exchange, status, response);
        });
        server.start();
        return new CapturedRequest(method, contentType, authorization, requestBody);
    }

    private static void send(HttpExchange exchange, int status, byte[] response) throws IOException {
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
        }
        exchange.close();
    }

    private LeadQualificationWebhookClient client() {
        return new LeadQualificationWebhookClient("http://127.0.0.1:" + server.getAddress().getPort()
                + "/lead-qualification");
    }

    private QualificationDispatchRequest request() {
        Lead lead = Lead.create("Test Lead", "test@example.com", null, null,
                "Consulting", null, null, "A sufficiently detailed test message", "test");
        lead.startQualification();
        return new QualificationDispatchRequest(lead.getId(), UUID.randomUUID(), 1,
                com.mohammadmurrar.leadflow.lead.api.LeadResponse.from(lead));
    }

    private void assertRequestContract(CapturedRequest captured) throws Exception {
        assertThat(captured.method().get()).isEqualTo("POST");
        assertThat(captured.contentType().get()).startsWith("application/json");
        assertThat(captured.authorization().get()).isNull();
        JsonNode body = objectMapper.readTree(captured.body().get());
        assertThat(body.propertyStream().map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(Set.of("leadId", "attemptId", "attemptNumber", "lead"));
    }

    private record CapturedRequest(AtomicReference<String> method,
                                   AtomicReference<String> contentType,
                                   AtomicReference<String> authorization,
                                   AtomicReference<byte[]> body) {}
}
