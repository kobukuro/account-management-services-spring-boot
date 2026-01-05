package com.peter.apigateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
class GatewayRoutingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static MockWebServer mockBackendServer;

    @BeforeEach
    void setUp() throws IOException {
        mockBackendServer = new MockWebServer();
        mockBackendServer.start(8080);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockBackendServer.shutdown();
    }

    @Test
    void shouldRoutePostRequestToUsersEndpoint() throws InterruptedException {
        // Given
        String expectedResponse = "{\"message\":\"User registered\"}";
        mockBackendServer.enqueue(new MockResponse()
                .setBody(expectedResponse)
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json"));

        String requestBody = "{\"email\":\"test@example.com\",\"password\":\"password123\"}";

        // When
        webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                // Then
                .expectStatus().isCreated()
                .expectBody().json(expectedResponse);

        RecordedRequest recordedRequest = mockBackendServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/users");
    }

    @Test
    void shouldRoutePostRequestToActivationEndpoint() throws InterruptedException {
        // Given
        String expectedResponse = "{\"message\":\"Account activated\"}";
        mockBackendServer.enqueue(new MockResponse()
                .setBody(expectedResponse)
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        // When
        webTestClient.post()
                .uri("/api/v1/users/activation")
                .exchange()
                // Then
                .expectStatus().isOk()
                .expectBody().json(expectedResponse);

        RecordedRequest recordedRequest = mockBackendServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/users/activation");
    }

    @Test
    void shouldRouteToOtherUserEndpoints() throws InterruptedException {
        // Given
        String expectedResponse = "{\"message\":\"Login successful\"}";
        mockBackendServer.enqueue(new MockResponse()
                .setBody(expectedResponse)
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        String requestBody = "{\"email\":\"test@example.com\",\"password\":\"password123\"}";

        // When
        webTestClient.post()
                .uri("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                // Then
                .expectStatus().isOk()
                .expectBody().json(expectedResponse);

        RecordedRequest recordedRequest = mockBackendServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/users/login");
    }

    @Test
    void shouldForwardHeadersToBackend() throws InterruptedException {
        String requestBody = "{\"currentPassword\":\"oldPass123\",\"newPassword\":\"NewPass!234\"}";

        // Given
        mockBackendServer.enqueue(new MockResponse()
                .setBody(requestBody)
                .setResponseCode(204)
                .addHeader("Content-Type", "application/json"));

        // When
        webTestClient.post()
                .uri("/api/v1/users/change-password")
                .header("Authorization", "Bearer test-token")
                .exchange()
                // Then
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);

        RecordedRequest recordedRequest = mockBackendServer.takeRequest();
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test
    void shouldHandleBackendErrorResponse() throws InterruptedException {
        // Given
        String errorResponse = "{\"error\":\"Bad Request\"}";
        mockBackendServer.enqueue(new MockResponse()
                .setBody(errorResponse)
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json"));

        String requestBody = "{\"email\":\"invalid\"}";

        // When
        webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                // Then
                .expectStatus().isBadRequest()
                .expectBody().json(errorResponse);

        RecordedRequest recordedRequest = mockBackendServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }

    @Test
    void shouldHandle404FromBackend() throws InterruptedException {
        // Given
        mockBackendServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"error\":\"Not found\"}"));

        // When
        webTestClient.get()
                .uri("/api/v1/users/nonexistent")
                .exchange()
                // Then
                .expectStatus().isNotFound();

        RecordedRequest recordedRequest = mockBackendServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/users/nonexistent");
    }
}
