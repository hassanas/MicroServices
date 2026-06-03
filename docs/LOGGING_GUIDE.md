# Logging Guide

This guide covers where logs are written, how to tail them live, and how to correlate errors across services.

## Log Locations

### Service-local application logs

| Service | Application Log | Access Log |
|---------|----------------|-----------|
| API Gateway | `api-gateway/logs/api-gateway.log` | `api-gateway/logs/access.YYYY-MM-DD.log` |
| Product Service | `product-service/logs/product-service.log` | `product-service/logs/access.YYYY-MM-DD.log` |
| Order Service | `order-service/logs/order-service.log` | `order-service/logs/access.YYYY-MM-DD.log` |
| Inventory Service | `inventory-service/logs/inventory-service.log` | `inventory-service/logs/access.YYYY-MM-DD.log` |
| Notification Service | `notification-service/logs/notification-service.log` | — (no HTTP endpoints) |

### Root startup / build logs

| File | Written by |
|------|-----------|
| `logs/startup-api-gateway.log` | `start-services.sh` / `restart-service.sh` |
| `logs/startup-product-service.log` | same |
| `logs/startup-order-service.log` | same |
| `logs/startup-inventory-service.log` | same |
| `logs/startup-notification-service.log` | same |
| `logs/startup-kafka-service.log` | same |
| `logs/build.log` | Maven build output |

> **Note:** Runtime application errors appear in the service-local logs, not in root startup logs.

---

## Live Tail

```bash
# All startup logs at once
tail -f logs/*.log

# Specific service application log
tail -f order-service/logs/order-service.log
tail -f notification-service/logs/notification-service.log
tail -f api-gateway/logs/api-gateway.log

# Today's access log
tail -f api-gateway/logs/access.$(date +%Y-%m-%d).log
```

---

## Kafka / Notification Debugging

```bash
# Watch consumer receive events and email dispatch
tail -f logs/startup-notification-service.log | grep -E "(Received OrderPlaced|Email successfully|ERROR|Received: [^0])"

# Watch Kafka polling activity (0 records = healthy idle, non-zero = consuming)
tail -f notification-service/logs/notification-service.log | grep "Received:"

# Check Kafka broker logs for consumer group issues
docker logs kafka-broker --since 10m 2>&1 | grep -E "(ERROR|WARN|consumer_offsets|INVALID)"
```

---

## Correlate a 500 from the Gateway to Root Cause

1. Check the gateway access log for the request status and path.
2. Check the gateway app log for a stack trace at the same timestamp.
3. If the request was forwarded to a backend service, check that service's access and app logs.

```bash
grep "GET /api/product" api-gateway/logs/access.$(date +%Y-%m-%d).log | tail -20

grep -E "ERROR|Exception|Caused by|CircuitBreaker|fallback" api-gateway/logs/api-gateway.log | tail -50
```

---

## Health Checks

```bash
curl -s -o /dev/null -w "gateway:%{http_code}\n"      http://localhost:9000/actuator/health
curl -s -o /dev/null -w "product:%{http_code}\n"      http://localhost:8080/actuator/health
curl -s -o /dev/null -w "order:%{http_code}\n"        http://localhost:8081/actuator/health
curl -s -o /dev/null -w "inventory:%{http_code}\n"    http://localhost:8082/actuator/health
curl -s -o /dev/null -w "notification:%{http_code}\n" http://localhost:8083/actuator/health
```

---

## Common Gateway Status Patterns

| Status | Meaning |
|--------|---------|
| `302` on `http://localhost:9000/api/...` | Redirect to Keycloak login (browser flow) |
| `401` on `http://localhost:9000/api/...` | Missing or invalid bearer token |
| `500` on a gateway route | Check `api-gateway/logs/api-gateway.log` for route/filter exception |

---

## Circuit Breaker Notes

If a gateway route filter is misconfigured, you may see `missing circuit breaker id` errors. Each route in `api-gateway/src/main/resources/application.yml` must declare a unique circuit breaker ID:

- `id: productServiceCircuitBreaker`
- `id: ordersServiceCircuitBreaker`
- `id: inventoryServiceCircuitBreaker`

---

## Restart a Service and Watch Startup

```bash
bash restart-service.sh --order-service
tail -f logs/startup-order-service.log

bash restart-service.sh --notification-service
tail -f logs/startup-notification-service.log
```
