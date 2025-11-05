package com.peter.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ActiveProfiles("ci")
class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void redisTemplate_shouldReturnConfiguredTemplate() {
        // Given
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        // When
        RedisTemplate<String, String> template = redisConfig.redisTemplate(connectionFactory);

        // Then
        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(connectionFactory);
    }
}
