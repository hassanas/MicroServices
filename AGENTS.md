# AGENTS.md - Microservices Project

## Project Overview

Spring Boot 4.0.6 / Java 21 microservices system. Five Spring Boot services + Kafka broker, all Maven-based.

| Service | Port | Tech | Role |
|---------|------|------|------|
| api-gateway | 9000 | Spring Cloud Gateway | Single entry point, Keycloak JWT auth |
| product-service | 8080 | MongoDB | Product catalogue CRUD |
| order-service | 8081 | MySQL, Kafka producer, gRPC client | Order placement |
| inventory-service | 8082 | PostgreSQL, gRPC server | Stock verification |
| notification-service | 8083 | Kafka consumer, JavaMailSender | Order confirmation emails |
| kafka-service | 9092 / 8989 | Apache Kafka (KRaft), Kafka UI | Message broker |

---

## Architecture & Data Flow

### Request Flow
```
External Client
  → API Gateway (9000) — Keycloak JWT validation
  → order-service (8081) — places order, calls inventory via gRPC
      → inventory-service (8082) — checks stock (gRPC server, port 9095)
  → order-service publishes OrderPlacedEvent to Kafka topic "order-placed"
  → notification-service (8083) — consumes event, sends email via Mailtrap
```

### Layered Pattern (all services)
```
HTTP/gRPC Request → Controller → Service → Repository → DB
```

---

## Kafka Setup (Critical — Read Before Working on order/notification)

### Single-broker requirement
Kafka defaults assume a 3-broker cluster. Running locally with 1 broker **requires** these settings in `kafka-service/docker-compose.yaml` — they are already set:

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
```

Without `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1`, the internal `__consumer_offsets` topic cannot be created. Consumers silently receive `0 records` with no error — **always check broker logs first** when a consumer is not receiving messages:
```bash
docker logs kafka-broker 2>&1 | grep -E "ERROR|WARN|INVALID_REPLICATION_FACTOR"
```

### Producer configuration (order-service)
```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.template.default-topic=order-placed
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.properties.spring.json.add.type.headers=false   # REQUIRED — prevents class name embedding
```

### Consumer configuration (notification-service)
```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=notification-consumer-v4
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.properties.spring.json.value.default.type=com.vaimo.microservices.notification.order.OrderPlacedEvent
spring.kafka.consumer.properties.spring.json.use.type.headers=false   # REQUIRED — ignore producer type header
```

The `groupId` in `@KafkaListener` **must match** `spring.kafka.consumer.group-id` in `application.properties`.

### Kafka consumer diagnostic order
When a consumer receives `0 records`:
1. Check Kafka broker logs for `INVALID_REPLICATION_FACTOR`
2. Confirm topic exists and has messages (`kafka-get-offsets.sh`)
3. Confirm partition assignment in consumer logs (`partitions assigned:`)
4. Check deserialization config (trusted packages, type headers, value type)

---

## Service-Specific Notes

### product-service (8080 / MongoDB 27018)

**Architecture:**
```
HTTP Request → ProductController → ProductService → ProductRepository → MongoDB
```

**Key packages:**
- `controller` — REST endpoints, accepts/returns DTOs
- `service` — business logic, DTO ↔ model transformation
- `repository` — extends `MongoRepository<Product, String>`
- `model` — `@Document(value = "product")`, `@Id` on String field
- `dto` — Java Records (`ProductRequest`, `ProductResponse`)
- `config` — OpenAPI bean, `OpenApiAccessFilter`, `OpenApiAccessProperties`
- `exception` — `@RestControllerAdvice`, `ApiErrorResponse`

**Endpoints:**
- `POST /api/product` → 201 CREATED
- `GET /api/product` → 200 OK with array

**Configuration:**
```properties
spring.application.name=product-service
spring.mongodb.uri=mongodb://root:password@127.0.0.1:27018/product-service?authSource=admin
openapi.gateway.docs-access-token=${OPENAPI_GATEWAY_DOCS_ACCESS_TOKEN:local-gateway-docs-token}
logging.file.name=logs/product-service.log
```

**Model construction (Builder pattern):**
```java
Product product = Product.builder()
    .name(productRequest.name())
    .description(productRequest.description())
    .sku(productRequest.sku())
    .price(productRequest.price())
    .build();
```

**Testing:**
- `@SpringBootTest(webEnvironment = MOCK)` with `MockMvc`
- `@Import(TestcontainersConfiguration.class)` — MongoDBContainer with `@ServiceConnection`
- JSONPath assertions: `jsonPath("$.id")`, `jsonPath("$").isArray()`
- Surefire requires: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED`

---

### order-service (8081 / MySQL 3306)

**Role:** Accepts REST orders, verifies stock via gRPC → persists order → publishes Kafka event.

**Key files:**
- `event/OrderPlacedEvent.java` — `{ orderNumber, email }` — published to Kafka
- `client/InventoryGrpcClient.java` — gRPC stub calling inventory-service on port 9095
- `service/OrderService.java` — orchestrates gRPC call → save → Kafka publish

**Configuration:**
```properties
inventory.grpc.host=localhost
inventory.grpc.port=9095
spring.kafka.producer.properties.spring.json.add.type.headers=false
```

---

### inventory-service (8082 / PostgreSQL 5432)

**Role:** gRPC server for stock checks; REST endpoints for inventory management.

**gRPC server port:** 9095 (not the default 9090)

**Proto contract** (both services must have identical copy):
```protobuf
service InventoryStockService {
  rpc CheckStock(StockRequest) returns (StockResponse);
}
message StockRequest { string product_id = 1; int32 quantity = 2; }
message StockResponse { bool in_stock = 1; }
```

---

### notification-service (8083 / No DB)

**Role:** Kafka consumer only — no REST endpoints exposed to clients.

**Event DTO** (`com.vaimo.microservices.notification.order.OrderPlacedEvent`):
```java
@Data @AllArgsConstructor @NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
    private String email;
}
```

This is a **local copy** of the order-service event — field names must match for JSON deserialization. Packages differ intentionally.

**Listener:**
```java
@KafkaListener(topics = "order-placed", groupId = "notification-consumer-v4")
public void listen(OrderPlacedEvent orderPlacedEvent) { ... }
```

**Mail config (Mailtrap sandbox):**
```properties
spring.mail.host=sandbox.smtp.mailtrap.io
spring.mail.port=2525
```

**Log file:** `logs/notification-service.log`

---

## Shared Patterns Across All Services

### Dependency Injection
```java
@RequiredArgsConstructor  // Lombok
public class SomeService {
    private final SomeDependency dependency;  // final → constructor-injected
}
```

### DTO Pattern (Records)
```java
public record ProductRequest(
    @NotBlank String name,
    @NotNull BigDecimal price
) {}
```

### Service Layer Transformation
```java
// Entity → Response DTO
return new ProductResponse(product.getId(), product.getName(), ...);
```

### Stream Operations
```java
repository.findAll().stream()
    .map(entity -> new ResponseDto(...))
    .toList();
```

### OpenAPI Docs Access Filter (product, order, inventory)
All three API services protect `/v3/api-docs` with a shared internal token header:
- Direct calls return `404` unless `X-Internal-OpenApi-Access: local-gateway-docs-token` is present
- `application.properties`: `openapi.gateway.docs-access-token=${OPENAPI_GATEWAY_DOCS_ACCESS_TOKEN:local-gateway-docs-token}`

### SLF4J Logging
```java
@Slf4j  // Lombok
public class OrderService {
    log.info("Order {} placed for email {}", order.getOrderNumber(), order.getEmail());
}
```

---

## Critical Gotchas

1. **Kafka single-broker:** `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` must be `1` — missing this causes silent consumer failure (`0 records`)
2. **Kafka consumer debugging:** Always check broker logs first — `docker logs kafka-broker 2>&1 | grep INVALID`
3. **Kafka type headers:** Producer must set `spring.json.add.type.headers=false`; consumer must set `spring.json.use.type.headers=false` when producer/consumer use different package names for the same DTO
4. **groupId consistency:** `@KafkaListener(groupId=...)` overrides `spring.kafka.consumer.group-id` — keep them in sync
5. **Stale consumer offsets:** If a misconfigured consumer ran before and committed offsets, new consumers with the same group ID start from the committed position (possibly at end). Use a new group ID or reset offsets
6. **MongoDB port:** Runs on `27018` (not `27017`) — verify in connection URI
7. **gRPC port:** Inventory service gRPC listens on `9095` (not Kafka default `9090`)
8. **DTOs are Immutable Records:** Cannot be modified after construction — provide all fields at creation
9. **OpenAPI Docs Filtered:** Direct `/v3/api-docs` returns `404` without `X-Internal-OpenApi-Access` header
10. **Lombok annotation processing:** Maven compiler config must include Lombok in `annotationProcessorPaths`
11. **Java 21 + Testcontainers:** Surefire requires `--add-opens java.base/java.lang=ALL-UNNAMED` argLine

---

## Related Files Reference

### Project-level
- **Documentation:** `docs/INDEX.md` (start here), `docs/KAFKA_SETUP.md`, `docs/LOGGING_GUIDE.md`
- **Scripts:** `start-services.sh`, `stop-services.sh`, `restart-service.sh`, `check-services.sh`
- **Kafka infra:** `kafka-service/docker-compose.yaml`

### product-service
- **Entrypoint:** `com.vaimo.microservices.product.ProductServiceApplication`
- **Config:** `src/main/resources/application.properties`
- **OpenAPI Config:** `config/OpenApiConfiguration.java`, `config/OpenApiAccessFilter.java`
- **Error Handling:** `exception/GlobalExceptionHandler.java`, `exception/ApiErrorResponse.java`
- **Test Infrastructure:** `TestcontainersConfiguration.java`, `ProductServiceApplicationTests.java`

### notification-service
- **Listener:** `service/NotificationService.java`
- **Event DTO:** `order/OrderPlacedEvent.java`
- **Config:** `src/main/resources/application.properties`

### order-service
- **Business Logic:** `service/OrderService.java`
- **Kafka Event:** `event/OrderPlacedEvent.java`
- **gRPC Client:** `client/InventoryGrpcClient.java`
