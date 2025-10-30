package com.peter.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("ci")
class GatewayConfigTest {

    private final GatewayConfig gatewayConfig = new GatewayConfig();
    private final String usersEndpoint = "/api/v1/users";

    @Test
    void ipKeyResolver_shouldResolveClientIpAddress() {
        // Given
        KeyResolver keyResolver = gatewayConfig.ipKeyResolver();
        String expectedIp = "192.168.1.100";

        MockServerHttpRequest request = MockServerHttpRequest
                .get(usersEndpoint)
                .remoteAddress(new InetSocketAddress(expectedIp, 8080))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> result = keyResolver.resolve(exchange);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedIp)
                .verifyComplete();
    }

    @Test
    void ipKeyResolver_shouldResolveDifferentIpAddresses() {
        // Given
        KeyResolver keyResolver = gatewayConfig.ipKeyResolver();
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        MockServerHttpRequest request1 = MockServerHttpRequest
                .get(usersEndpoint)
                .remoteAddress(new InetSocketAddress(ip1, 8080))
                .build();

        MockServerHttpRequest request2 = MockServerHttpRequest
                .get(usersEndpoint)
                .remoteAddress(new InetSocketAddress(ip2, 8080))
                .build();

        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);

        // When
        Mono<String> result1 = keyResolver.resolve(exchange1);
        Mono<String> result2 = keyResolver.resolve(exchange2);

        // Then
        StepVerifier.create(result1)
                .expectNext(ip1)
                .verifyComplete();

        StepVerifier.create(result2)
                .expectNext(ip2)
                .verifyComplete();
    }

    @Test
    void ipKeyResolver_shouldResolveLocalhostIpAddress() {
        // Given
        KeyResolver keyResolver = gatewayConfig.ipKeyResolver();

        MockServerHttpRequest request = MockServerHttpRequest
                .get(usersEndpoint)
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> result = keyResolver.resolve(exchange);

        // Then
        StepVerifier.create(result)
                .expectNext("127.0.0.1")
                .verifyComplete();
    }

    @Test
    void ipKeyResolver_beanShouldNotBeNull() {
        // When
        KeyResolver keyResolver = gatewayConfig.ipKeyResolver();

        // Then
        assertThat(keyResolver).isNotNull();
    }
}
