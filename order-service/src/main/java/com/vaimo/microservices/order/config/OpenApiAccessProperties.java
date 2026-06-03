package com.vaimo.microservices.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openapi.gateway")
public record OpenApiAccessProperties(String docsAccessToken) {
}

