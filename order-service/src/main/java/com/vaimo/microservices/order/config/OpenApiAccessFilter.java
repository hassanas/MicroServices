package com.vaimo.microservices.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenApiAccessFilter extends OncePerRequestFilter {

    private static final String OPEN_API_PATH = "/v3/api-docs";
    private static final String SWAGGER_UI_PATH = "/swagger-ui";
    private static final String SWAGGER_UI_HTML_PATH = "/swagger-ui.html";
    private static final String INTERNAL_ACCESS_HEADER = "X-Internal-OpenApi-Access";

    private final OpenApiAccessProperties openApiAccessProperties;

    public OpenApiAccessFilter(OpenApiAccessProperties openApiAccessProperties) {
        this.openApiAccessProperties = openApiAccessProperties;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        if (requestPath != null && (requestPath.startsWith(SWAGGER_UI_PATH) || SWAGGER_UI_HTML_PATH.equals(requestPath))) {
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        if (requestPath != null && requestPath.startsWith(OPEN_API_PATH)
                && !openApiAccessProperties.docsAccessToken().equals(request.getHeader(INTERNAL_ACCESS_HEADER))) {
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        filterChain.doFilter(request, response);
    }
}

