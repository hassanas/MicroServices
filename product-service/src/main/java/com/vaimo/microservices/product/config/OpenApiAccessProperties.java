package com.vaimo.microservices.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openapi.gateway")
public record OpenApiAccessProperties(String docsAccessToken) {
}

