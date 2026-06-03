# Kafka Setup & Configuration

## Architecture

```
order-service (producer)
      │
      │  topic: "order-placed"  (JSON, no type headers)
      ▼
Kafka Broker (KRaft, port 9092)
      │
      ▼
notification-service (consumer)
      │
      └──► JavaMailSender → Mailtrap SMTP → Customer email
```

---

## Infrastructure

Kafka runs as a **single-node KRaft broker** (no Zookeeper).

```bash
# Start
cd kafka-service && docker compose up -d

# Stop
cd kafka-service && docker compose down

# Kafka UI
http://localhost:8989/kafka-ui
```

| Container | Port | Purpose |
|-----------|------|---------|
| kafka-broker | 9092 | Kafka broker (EXTERNAL listener for host apps) |
| kafka-ui | 8989 | Visual dashboard (topics, consumer groups, messages) |

---

## Critical Kafka Single-Broker Settings

The following environment variables **must** be set in `kafka-service/docker-compose.yaml` when running a single broker. Without them, the internal `__consumer_offsets` topic cannot be created and **all consumers silently receive 0 records**.

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
```

---

## Order Service — Kafka Producer

**File:** `order-service/src/main/resources/application.properties`

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.template.default-topic=order-placed
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.properties.spring.json.add.type.headers=false
```

> `spring.json.add.type.headers=false` — prevents embedding the producer-side fully-qualified class name in the Kafka message header. Without this, the consumer would try to deserialize into `com.vaimo.microservices.order.event.OrderPlacedEvent`, which doesn't exist in the notification-service classpath.

**Publishing in code:**

```java
private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

kafkaTemplate.send("order-placed", new OrderPlacedEvent(order.getOrderNumber(), order.getEmail()));
```

---

## Notification Service — Kafka Consumer

**File:** `notification-service/src/main/resources/application.properties`

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=notification-consumer-v4
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.properties.spring.json.value.default.type=com.vaimo.microservices.notification.order.OrderPlacedEvent
spring.kafka.consumer.properties.spring.json.use.type.headers=false
```

| Property | Value | Why |
|----------|-------|-----|
| `trusted.packages` | `*` | Allows deserializing any package |
| `value.default.type` | `...notification.order.OrderPlacedEvent` | Target class for deserialization |
| `use.type.headers` | `false` | Ignores any `__TypeId__` header from the producer |

**Listener:**

```java
@KafkaListener(topics = "order-placed", groupId = "notification-consumer-v4")
public void listen(OrderPlacedEvent orderPlacedEvent) {
    // send email
}
```

> The `groupId` in `@KafkaListener` must match `spring.kafka.consumer.group-id` in `application.properties`.

---

## Event DTO

**Order Service** (`com.vaimo.microservices.order.event.OrderPlacedEvent`):
```java
@Data @AllArgsConstructor @NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
    private String email;
}
```

**Notification Service** (`com.vaimo.microservices.notification.order.OrderPlacedEvent`):
```java
@Data @AllArgsConstructor @NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
    private String email;
}
```

These are two separate copies — the field names must match for JSON deserialization to work. They do not need to be in the same package.

---

## Email Configuration (Mailtrap)

```properties
spring.mail.host=sandbox.smtp.mailtrap.io
spring.mail.port=2525
spring.mail.username=<mailtrap-username>
spring.mail.password=<mailtrap-password>
```

---

## Verifying the Full Flow

```bash
# 1. Watch notification service consumer
tail -f logs/startup-notification-service.log | grep -E "(Received OrderPlaced|Email successfully|ERROR)"

# 2. Place an order via API
curl -X POST http://localhost:9000/api/order \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"productId":"...", "quantity":1, "price":99.99, "sku":"SKU001", "email":"user@example.com"}'

# Expected log output in notification-service:
# INFO  NotificationService : Received OrderPlacedEvent OrderPlacedEvent(orderNumber=..., email=user@example.com)
# INFO  NotificationService : Email successfully sent to user@example.com
```

---

## Troubleshooting

### Consumer receives 0 records

**Most likely cause:** `__consumer_offsets` topic cannot be created.

```bash
docker logs kafka-broker --since 5m 2>&1 | grep "INVALID_REPLICATION_FACTOR"
```

If you see this error, ensure `kafka-service/docker-compose.yaml` has `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1` and **recreate** the container (restart alone does not apply env var changes):

```bash
cd kafka-service
docker rm -f kafka-broker kafka-ui
docker compose up -d
```

Then restart both services:
```bash
bash restart-service.sh --order-service
bash restart-service.sh --notification-service
```

### Consumer has stale committed offset (misses already-published messages)

The old consumer group committed an offset past all existing messages. Use a new group ID:

1. Change `spring.kafka.consumer.group-id` in `application.properties`
2. Change `groupId` in `@KafkaListener` to match
3. Restart notification-service

### `UNKNOWN_TOPIC_OR_PARTITION` at startup

The `order-placed` topic doesn't exist yet — it's auto-created when the first order is placed. This warning is normal at cold start and resolves itself after the first order.

### Duplicate message processing

Do **not** set `spring.kafka.consumer.enable-auto-commit=true` together with Spring Kafka's default `AckMode`. Spring Kafka manages commits automatically — enabling auto-commit conflicts and causes duplicate delivery.

