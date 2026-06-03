# Running All Microservices

## Prerequisites

Before starting the Spring Boot services, **Kafka must be running** — both `order-service` (producer) and `notification-service` (consumer) depend on it.

```bash
cd /home/hassan/D/work/springboot/MicroServices/kafka-service
docker compose up -d
cd ..
```

Kafka UI will be available at `http://localhost:8989/kafka-ui` for monitoring topics and consumer groups.

---

## Option 1: Start Everything (Recommended)

```bash
bash start-services.sh
```

This script:
1. ✓ Checks Docker is running
2. ✓ Starts all databases from root `docker-compose.yaml`
3. ✓ Builds all modules (`./mvnw clean package -DskipTests`)
4. ✓ Starts all 5 Spring Boot services in the background

**Services available at:**

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:9000 |
| Product Service | http://localhost:8080 |
| Order Service | http://localhost:8081 |
| Inventory Service | http://localhost:8082 |
| Notification Service | http://localhost:8083 |

---

## Option 1a: Debug Mode

```bash
bash start-services.sh --debug
```

Remote debug ports:

| Service | Debug Port |
|---------|-----------|
| api-gateway | 5005 |
| product-service | 5006 |
| order-service | 5007 |
| inventory-service | 5008 |

Create a **Remote JVM Debug** configuration in IntelliJ and connect to `localhost:<port>`.

To make the JVM wait for the debugger before executing startup code:

```bash
bash start-services.sh --debug-suspend
```

---

## Option 2: Databases Only + Manual Services

Start infrastructure only:

```bash
# Kafka
cd kafka-service && docker compose up -d && cd ..

# All databases
docker-compose up -d
```

Then start individual services from IntelliJ (Debug mode recommended) or terminal:

```bash
cd product-service      && ./mvnw spring-boot:run
cd order-service        && ./mvnw spring-boot:run
cd inventory-service    && ./mvnw spring-boot:run
cd notification-service && ./mvnw spring-boot:run
cd api-gateway          && ./mvnw spring-boot:run
```

---

## Stop Everything

```bash
bash stop-services.sh
```

Stop databases only:
```bash
docker-compose down
```

Stop Kafka:
```bash
cd kafka-service && docker compose down
```

---

## Check Status

```bash
bash check-services.sh

# CI/script-friendly JSON output
bash check-services.sh --json
```

---

## View Logs

```bash
# Startup logs (written to root logs/)
tail -f logs/startup-order-service.log
tail -f logs/startup-notification-service.log

# Application logs (written to each service's own logs/)
tail -f order-service/logs/order-service.log
tail -f notification-service/logs/notification-service.log
```

Full log path reference: [LOGGING_GUIDE.md](LOGGING_GUIDE.md)

For Kafka consumer/producer configuration details: [KAFKA_SETUP.md](KAFKA_SETUP.md)

---

## Database Configuration

| Service | Database | Container | Port |
|---------|----------|-----------|------|
| Product | MongoDB | product-mongo | 27018 |
| Order | MySQL | order-mysql | 3306 |
| Inventory | PostgreSQL | inventory-postgres | 5432 |

---

## Kafka Configuration

| Component | Container | Port | Notes |
|-----------|-----------|------|-------|
| Kafka Broker | kafka-broker | 9092 | KRaft mode (no Zookeeper) |
| Kafka UI | kafka-ui | 8989 | http://localhost:8989/kafka-ui |

**Important single-broker settings** (already in `kafka-service/docker-compose.yaml`):
```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
```

Without `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1`, the `__consumer_offsets` internal topic cannot be created on a single broker, and **all consumers will silently receive 0 records**.

---

## Troubleshooting

### Docker Compose `include` not supported
Your Docker Compose is older than 2.20. See [DOCKER_COMPOSE_ALTERNATIVES.md](DOCKER_COMPOSE_ALTERNATIVES.md).

### Port already in use

```bash
lsof -i :9092   # Kafka
lsof -i :8989   # Kafka UI
lsof -i :27018  # MongoDB
lsof -i :3306   # MySQL
lsof -i :5432   # PostgreSQL
lsof -i :8080   # Product Service
lsof -i :8081   # Order Service
lsof -i :8082   # Inventory Service
lsof -i :8083   # Notification Service
lsof -i :9000   # API Gateway
```

### Services won't start

```bash
docker ps                  # check containers
docker-compose ps          # check DB containers
docker-compose logs -f     # check DB logs
```

### Notification service not consuming messages

1. Confirm Kafka is running: `docker ps | grep kafka`
2. Confirm `__consumer_offsets` is writable: check Kafka broker logs for `INVALID_REPLICATION_FACTOR`
3. Confirm topic exists: place an order — order-service auto-creates the `order-placed` topic
4. Restart notification-service: `bash restart-service.sh --notification-service`
