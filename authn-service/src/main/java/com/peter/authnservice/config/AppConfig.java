package com.peter.authnservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Creates a set of trusted proxy IP addresses from the configuration.
     * <p>
     * These IP addresses are used to validate that proxy headers (X-Forwarded-For, X-Real-IP)
     * are only trusted when the request comes from a known reverse proxy or load balancer.
     * This prevents malicious clients from spoofing their IP address by setting these headers directly.
     * <p>
     * The trusted proxy IPs should include:
     * <ul>
     *   <li>Localhost addresses (127.0.0.1, ::1) for local development</li>
     *   <li>Internal network addresses of reverse proxies (e.g., API Gateway, load balancers)</li>
     *   <li>Docker network addresses if running in containers</li>
     * </ul>
     *
     * @param trustedProxyIps comma-separated list of trusted proxy IP addresses from configuration
     * @return a set of trusted proxy IP addresses for validation
     */
    @Bean
    public Set<String> trustedProxyIps(@Value("${trusted-proxy-ips:127.0.0.1,::1}") String trustedProxyIps) {
        return Arrays.stream(trustedProxyIps.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toSet());
    }
}
