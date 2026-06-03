# MicroServices

A Spring Boot 4 / Java 21 microservices system with REST, gRPC, Kafka, and Keycloak.

## Technology Stack

**Core**
![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-6DB33F?logo=spring&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)

**APIs & Security**
![REST API](https://img.shields.io/badge/REST-API-005571)
![gRPC](https://img.shields.io/badge/gRPC-RPC-0F9D58?logo=grpc&logoColor=white)
![OpenAPI](https://img.shields.io/badge/OpenAPI-Docs-6BA539?logo=openapiinitiative&logoColor=white)
![Keycloak](https://img.shields.io/badge/Keycloak-JWT%20Auth-4D4D4D?logo=keycloak&logoColor=white)

**Data & Messaging**
![MongoDB](https://img.shields.io/badge/MongoDB-Database-47A248?logo=mongodb&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-Database-4479A1?logo=mysql&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-4169E1?logo=postgresql&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-Event%20Streaming-231F20?logo=apachekafka&logoColor=white)

**Infrastructure & Testing**
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Testcontainers](https://img.shields.io/badge/Testcontainers-Testing-2496ED?logo=testcontainers&logoColor=white)

**Start here for documentation:** [`docs/INDEX.md`](docs/INDEX.md)

---

## Services

| Service | Port | Role |
|---------|------|------|
| API Gateway | 9000 | Single entry point, Keycloak JWT auth |
| Product Service | 8080 | Product catalogue, MongoDB |
| Order Service | 8081 | Order placement, MySQL, Kafka producer |
| Inventory Service | 8082 | Stock checks via gRPC, PostgreSQL |
| Notification Service | 8083 | Kafka consumer, sends order emails |
| Kafka Broker | 9092 | KRaft message broker |
| Kafka UI | 8989 | Kafka management dashboard |

---

## Quick Start

```bash
# 1. Start Kafka first
cd kafka-service && docker compose up -d && cd ..

# 2. Start all services
bash start-services.sh

# 3. Check health
bash check-services.sh
```

## Debug Mode

```bash
bash start-services.sh --debug
# Ports: api-gateway=5005, product=5006, order=5007, inventory=5008
```

To wait for the debugger before startup:
```bash
bash start-services.sh --debug-suspend
```

## Restart a Single Service

```bash
bash restart-service.sh --notification-service
bash restart-service.sh --order-service
# or by number: bash restart-service.sh 3
```

## Check Status

```bash
bash check-services.sh
bash check-services.sh --json   # CI-friendly
```

## End-to-End Health Check

```bash
bash healthcheck-project.sh
bash healthcheck-project.sh --json
```

## Stop Services

```bash
bash stop-services.sh
cd kafka-service && docker compose down
```

## Logs

```bash
# All startup logs
tail -f logs/*.log

# Application logs
tail -f order-service/logs/order-service.log
tail -f notification-service/logs/notification-service.log
```
