package com.peter.authnservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
public class CorsProperties {
    @Value("${allowed-origins}")
    private String allowedOrigins;
}
