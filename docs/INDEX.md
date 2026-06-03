# 🎯 Microservices — Reference Index

> **Quick navigation:** Use this as your starting point for commands, service details, and doc links.

---

## ⚡ 30-Second Quick Start

```bash
cd /home/hassan/D/work/springboot/MicroServices

# 1. Start Kafka (required first — order + notification depend on it)
cd kafka-service && docker compose up -d && cd ..

# 2. Start all services
bash start-services.sh

# 3. Verify everything is healthy
bash check-services.sh
```

---

## Services at a Glance

| Service | Port | Database / Infra | Role |
|---------|------|-----------------|------|
| API Gateway | 9000 | — | Single entry point, Keycloak JWT auth |
| Product Service | 8080 | MongoDB 27018 | Product catalogue CRUD |
| Order Service | 8081 | MySQL 3306 | Order placement, Kafka producer |
| Inventory Service | 8082 | PostgreSQL 5432 | Stock checks via gRPC |
| Notification Service | 8083 | — | Kafka consumer → sends order emails |
| Kafka Broker | 9092 | kafka-data/ | Message broker (KRaft, single-node) |
| Kafka UI | 8989 | — | Visual Kafka management dashboard |

---

## Scripts

| Command | What it does |
|---------|-------------|
| `bash start-services.sh` | Build + start all Spring Boot services in background |
| `bash start-services.sh --debug` | Same, with remote debug ports open (5005–5008) |
| `bash start-services.sh --debug-suspend` | Wait for debugger to attach before JVM boot |
| `bash stop-services.sh` | Kill all running service processes |
| `bash check-services.sh` | Print health status of all services |
| `bash check-services.sh --json` | Machine-readable JSON output (CI-friendly) |
| `bash restart-service.sh <name\|number>` | Restart a single service |
| `bash healthcheck-project.sh` | Full security-aware end-to-end health check |
| `bash healthcheck-project.sh --json` | JSON output for CI pipelines |

### Service numbers for `restart-service.sh`

| # | Service |
|---|---------|
| 1 | api-gateway |
| 2 | product-service |
| 3 | order-service |
| 4 | inventory-service |
| 5 | kafka-service |
| 6 | notification-service |

---

## Kafka Setup (required before order/notification services)

```bash
# Start Kafka broker + Kafka UI
cd /home/hassan/D/work/springboot/MicroServices/kafka-service
docker compose up -d

# Stop
docker compose down

# Kafka UI dashboard
open http://localhost:8989/kafka-ui
```

Kafka is a **KRaft single-node broker** — no Zookeeper required.

> ⚠️ **Single-broker requirement:** `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` must be `1`.
> This is already configured in `kafka-service/docker-compose.yaml`.

---

## Database Containers

| Service | Container | Port | Credentials |
|---------|-----------|------|-------------|
| Product | product-mongo | 27018 | root / password |
| Order | order-mysql | 3306 | root / mysql |
| Inventory | inventory-postgres | 5432 | postgres / postgres |

Start databases individually:
```bash
cd product-service  && docker compose up -d   # MongoDB
cd order-service    && docker compose up -d   # MySQL
cd inventory-service && docker compose up -d  # PostgreSQL
```

---

## Workflows

### Full automated start
```bash
cd kafka-service && docker compose up -d && cd ..
bash start-services.sh
bash check-services.sh
```

### Databases + IDE services (best for debugging)
```bash
cd kafka-service && docker compose up -d && cd ..
docker-compose up -d              # starts all 3 databases
# Then run each service from IntelliJ using Debug mode
```

### Restart one service after a change
```bash
bash restart-service.sh --notification-service
tail -f logs/startup-notification-service.log
```

### Debug mode (attach IntelliJ remote debugger)
```bash
bash start-services.sh --debug
# Ports: api-gateway=5005, product=5006, order=5007, inventory=5008
```

---

## Viewing Logs

```bash
# Startup / build logs (root logs/)
tail -f logs/startup-order-service.log
tail -f logs/startup-notification-service.log

# Application logs (inside each service folder)
tail -f order-service/logs/order-service.log
tail -f notification-service/logs/notification-service.log

# All at once
tail -f logs/*.log
```

See [LOGGING_GUIDE.md](LOGGING_GUIDE.md) for full log paths and error-correlation patterns.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Notification consumer gets `0 records` | Kafka `__consumer_offsets` requires `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` in `kafka-service/docker-compose.yaml` — already set |
| `UNKNOWN_TOPIC_OR_PARTITION` on consumer start | Topic not yet created; the producer auto-creates it on first publish — place an order |
| Consumer group has stale committed offset | Use a new `groupId` in `@KafkaListener` and `application.properties` |
| Port already in use | `lsof -i :<port>` to find the conflicting process |
| Service won't start after code change | `bash restart-service.sh --<service-name>` |
| Build fails | `./mvnw clean` inside the service folder, then retry |
| Docker Compose `include` error | See [DOCKER_COMPOSE_ALTERNATIVES.md](DOCKER_COMPOSE_ALTERNATIVES.md) |

---

## Documentation

| Document | Purpose |
|----------|---------|
| [INDEX.md](INDEX.md) | **This file** — commands, services, quick reference |
| [KAFKA_SETUP.md](KAFKA_SETUP.md) | Kafka broker config, producer/consumer setup, troubleshooting |
| [LOGGING_GUIDE.md](LOGGING_GUIDE.md) | Log locations, live tail, error correlation |
| [KEYCLOAK_AUTHENTICATION.md](KEYCLOAK_AUTHENTICATION.md) | Gateway-level auth + internal OpenAPI token filter |
| [OPENAPI_IMPLEMENTATION_SUMMARY.md](OPENAPI_IMPLEMENTATION_SUMMARY.md) | OpenAPI/Swagger setup across all API services |
| [gRPC_IMPLEMENTATION_GUIDE.md](gRPC_IMPLEMENTATION_GUIDE.md) | Order→Inventory gRPC stock-check contract and flow |
| [DOCKER_COMPOSE_ALTERNATIVES.md](DOCKER_COMPOSE_ALTERNATIVES.md) | Fallback for Docker Compose < 2.20 |

---

## File Structure

```
MicroServices/
├── AGENTS.md                          ← AI coding-agent instructions
├── README.md                          ← Project entry point
├── docker-compose.yaml                ← Root DB orchestration
├── start-services.sh / stop-services.sh / check-services.sh
├── restart-service.sh                 ← Restart a single named service
├── healthcheck-project.sh             ← End-to-end security health check
├── logs/                              ← Startup + build logs
├── docs/                              ← All documentation (this folder)
├── api-gateway/                       ← Spring Cloud Gateway + Keycloak
├── product-service/                   ← MongoDB, REST, OpenAPI
├── order-service/                     ← MySQL, REST, gRPC client, Kafka producer
├── inventory-service/                 ← PostgreSQL, gRPC server, REST, OpenAPI
├── notification-service/              ← Kafka consumer, sends order confirmation emails
└── kafka-service/                     ← Kafka broker (KRaft) + Kafka UI
```

---

## Requirements

- Docker with Compose 2.20+
- Java 21
- Maven Wrapper (`./mvnw`) — included in each service folder

```bash
docker compose version
java -version
```


