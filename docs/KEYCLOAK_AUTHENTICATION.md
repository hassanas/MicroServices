# Keycloak Authentication & OpenAPI Access Filter

## Overview

All three API microservices (`product-service`, `order-service`, `inventory-service`) use a **gateway-level Keycloak authentication model**. Authentication is handled entirely at the API Gateway (port `9000`) — individual services do not validate JWT tokens.

Each service additionally protects its **OpenAPI documentation endpoint** (`/v3/api-docs`) with a shared internal access token, preventing API schemas from being publicly exposed even when a service port is directly reachable.

> This document uses `inventory-service` as the reference implementation. The pattern is identical in `product-service` and `order-service`.

```
External Client
     │
     ▼
[API Gateway :9000]  ◄── Keycloak Token Validation (OAuth2/JWT)
     │
     │  forwards authenticated requests + X-Internal-OpenApi-Access header
     ▼
[Any Service :808x]
     │
     ├── OpenApiAccessFilter  ◄── Validates internal token header
     │
     └── Controller           ◄── Business logic (no auth code here)
```

---

## Authentication Components

### 1. `OpenApiAccessFilter.java`
**Location:** `src/main/java/com/vaimo/microservices/inventory/config/OpenApiAccessFilter.java`

This is the **core authentication class** in this service. It is a Spring `OncePerRequestFilter` that runs on every HTTP request with the **highest precedence** (`Ordered.HIGHEST_PRECEDENCE`).

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenApiAccessFilter extends OncePerRequestFilter {
```

#### What it protects

| Path | Behaviour |
|---|---|
| `/swagger-ui/**` | Always returns `404 Not Found` — Swagger UI is fully disabled |
| `/swagger-ui.html` | Always returns `404 Not Found` |
| `/v3/api-docs/**` | Returns `404 Not Found` **unless** the request carries the correct `X-Internal-OpenApi-Access` token header |
| All other paths | Allowed through without any token check |

#### How it authenticates

The filter checks for a custom HTTP header named `X-Internal-OpenApi-Access`:

```java
private static final String INTERNAL_ACCESS_HEADER = "X-Internal-OpenApi-Access";

if (requestPath.startsWith(OPEN_API_PATH)
        && !openApiAccessProperties.docsAccessToken().equals(
                request.getHeader(INTERNAL_ACCESS_HEADER))) {
    response.sendError(HttpStatus.NOT_FOUND.value());
    return;
}
```

- If the header **is present and matches** the configured token → the request is passed through to the API docs endpoint.
- If the header **is missing or doesn't match** → `404 Not Found` is returned (not `401`, intentionally masking the existence of the endpoint).

#### Full filter logic

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {

    String requestPath = request.getRequestURI();

    // Block Swagger UI entirely
    if (requestPath != null &&
            (requestPath.startsWith(SWAGGER_UI_PATH) ||
             SWAGGER_UI_HTML_PATH.equals(requestPath))) {
        response.sendError(HttpStatus.NOT_FOUND.value());
        return;
    }

    // Protect /v3/api-docs with token check
    if (requestPath != null && requestPath.startsWith(OPEN_API_PATH)
            && !openApiAccessProperties.docsAccessToken()
                    .equals(request.getHeader(INTERNAL_ACCESS_HEADER))) {
        response.sendError(HttpStatus.NOT_FOUND.value());
        return;
    }

    // All other requests: continue normally
    filterChain.doFilter(request, response);
}
```

---

### 2. `OpenApiAccessProperties.java`
**Location:** `src/main/java/com/vaimo/microservices/inventory/config/OpenApiAccessProperties.java`

This is a Spring Boot `@ConfigurationProperties` record that binds the token value from the application configuration.

```java
@ConfigurationProperties(prefix = "openapi.gateway")
public record OpenApiAccessProperties(String docsAccessToken) {
}
```

It maps to the property `openapi.gateway.docs-access-token` in `application.properties`.

---

### 3. `InventoryServiceApplication.java`
**Location:** `src/main/java/com/vaimo/microservices/inventory/InventoryServiceApplication.java`

The main application class enables the configuration properties binding with `@EnableConfigurationProperties`:

```java
@SpringBootApplication
@EnableConfigurationProperties(OpenApiAccessProperties.class)
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

Without `@EnableConfigurationProperties(OpenApiAccessProperties.class)`, the `OpenApiAccessProperties` bean would not be registered and the filter would fail to inject its dependency.

---

## Configuration

### `application.properties`

```properties
openapi.gateway.docs-access-token=${OPENAPI_GATEWAY_DOCS_ACCESS_TOKEN:local-gateway-docs-token}
```

This sets the expected token value. The pattern `${ENV_VAR:default}` means:
- In **production**, read the token from the environment variable `OPENAPI_GATEWAY_DOCS_ACCESS_TOKEN`.
- In **local development** (when the env var is absent), fall back to the hardcoded value `local-gateway-docs-token`.

### OpenAPI docs toggle per environment

| Property | `application.properties` | `application-dev.properties` | `application-prod.properties` |
|---|---|---|---|
| `springdoc.api-docs.enabled` | `true` | `true` | **`false`** |
| `springdoc.swagger-ui.enabled` | `false` | `false` | `false` |

In production, the Spring Boot API docs endpoint is fully disabled at the framework level — so even if someone sends the correct `X-Internal-OpenApi-Access` token, `/v3/api-docs` will return nothing. The filter provides defence-in-depth for non-production environments.

---

## How the API Gateway Uses This

The API Gateway (on port `9000`) is configured as a server in `OpenApiConfiguration.java`:

```java
.servers(List.of(
    new Server()
        .url("http://localhost:9000")
        .description("API Gateway"),
    new Server()
        .url("http://localhost:8082")
        .description("Local environment")
))
```

The expected flow is:

1. The API Gateway receives external requests and validates **Keycloak JWT/Bearer tokens**.
2. For internal documentation scraping (e.g. aggregating API specs), the gateway forwards requests to `/v3/api-docs` with the `X-Internal-OpenApi-Access` header set to the shared token.
3. The `OpenApiAccessFilter` in this service validates that header and either allows or denies access.

---

## Request Flow Summary

### Regular API request (e.g. `POST /api/inventory`)

```
Request arrives
     │
     ▼
OpenApiAccessFilter.doFilterInternal()
     │
     ├── path = /api/inventory → not a docs path
     │
     ▼
filterChain.doFilter()  →  InventoryController.checkInventory()
```

No authentication token is checked for regular API calls — Keycloak JWT validation happens at the API Gateway before the request reaches this service.

### API docs request with valid token (e.g. `GET /v3/api-docs`)

```
Request arrives with header: X-Internal-OpenApi-Access: local-gateway-docs-token
     │
     ▼
OpenApiAccessFilter.doFilterInternal()
     │
     ├── path starts with /v3/api-docs ✓
     ├── token matches openApiAccessProperties.docsAccessToken() ✓
     │
     ▼
filterChain.doFilter()  →  SpringDoc serves the OpenAPI JSON
```

### API docs request with missing or wrong token

```
Request arrives with no header (or wrong token)
     │
     ▼
OpenApiAccessFilter.doFilterInternal()
     │
     ├── path starts with /v3/api-docs ✓
     ├── token does NOT match ✗
     │
     ▼
response.sendError(404)  →  Request is rejected
```

### Swagger UI request (always blocked)

```
Request to /swagger-ui/index.html
     │
     ▼
OpenApiAccessFilter.doFilterInternal()
     │
     ├── path starts with /swagger-ui ✓
     │
     ▼
response.sendError(404)  →  Request is rejected (no token check needed)
```

---

## Security Design Decisions

| Decision | Reason |
|---|---|
| Returns `404` instead of `401` on token mismatch | Avoids revealing that a protected endpoint exists at all |
| `@Order(Ordered.HIGHEST_PRECEDENCE)` | Ensures the filter runs before any other filter in the chain |
| `OncePerRequestFilter` base class | Guarantees the filter logic executes exactly once per request, even in forward/include chains |
| Token value from environment variable | Allows different tokens per environment without code changes |
| Prod profile disables api-docs at framework level | Provides an extra layer — even a valid token cannot serve docs in production |
| Swagger UI unconditionally blocked | Prevents any attempt to browse the API interactively from this service directly |

---

## Files Changed / Added for Authentication

| File | Role |
|---|---|
| `config/OpenApiAccessFilter.java` | Core filter — intercepts and authenticates requests to docs endpoints |
| `config/OpenApiAccessProperties.java` | Binds `openapi.gateway.docs-access-token` from config into a typed record |
| `config/OpenApiConfiguration.java` | Registers the OpenAPI bean and defines server URLs (including the gateway) |
| `InventoryServiceApplication.java` | Adds `@EnableConfigurationProperties(OpenApiAccessProperties.class)` to activate the config binding |
| `application.properties` | Defines `openapi.gateway.docs-access-token` with env-var override support |
| `application-dev.properties` | Keeps API docs enabled in dev |
| `application-prod.properties` | Disables API docs entirely in production |

---

## Local Testing

To access the API docs locally (default token):

```bash
curl http://localhost:8082/v3/api-docs \
  -H "X-Internal-OpenApi-Access: local-gateway-docs-token"
```

To test that the filter correctly blocks access:

```bash
# Should return 404
curl -i http://localhost:8082/v3/api-docs

# Should also return 404
curl -i http://localhost:8082/v3/api-docs \
  -H "X-Internal-OpenApi-Access: wrong-token"
```

To override the token via environment variable when running the service:

```bash
export OPENAPI_GATEWAY_DOCS_ACCESS_TOKEN=my-secure-token
./mvnw spring-boot:run
```
