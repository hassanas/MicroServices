package com.vaimo.microservices.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GatewayCircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public GatewayCircuitBreakerHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public Health health() {
        Map<String, String> states = new LinkedHashMap<>();
        boolean anyOpen = false;

        for (CircuitBreaker circuitBreaker : circuitBreakerRegistry.getAllCircuitBreakers()) {
            String state = circuitBreaker.getState().name();
            states.put(circuitBreaker.getName(), state);
            if (CircuitBreaker.State.OPEN.name().equals(state)) {
                anyOpen = true;
            }
        }

        Health.Builder builder = anyOpen ? Health.down() : Health.up();
        return builder
            .withDetail("count", states.size())
            .withDetail("states", states)
            .build();
    }
}

