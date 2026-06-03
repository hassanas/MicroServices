# gRPC Implementation Guide - Order & Inventory Services

## Overview

gRPC is a modern, high-performance RPC (Remote Procedure Call) framework developed by Google. In your microservices architecture, **Order Service** uses gRPC to communicate with **Inventory Service** for stock verification before persisting orders.

### Why gRPC Instead of REST?
- **Performance**: Uses HTTP/2 with binary Protocol Buffers (smaller payload, faster)
- **Type Safety**: Contract defined in `.proto` files - strongly typed messages
- **Efficiency**: Superior for service-to-service communication
- **Streaming Capable**: Supports bidirectional streaming (future enhancement)

---

## Project Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  External Client (REST)                                     │
├─────────────────────────────────────────────────────────────┤
│              ↓ HTTP/REST                                    │
│      POST /api/orders (with product details)               │
│              ↓                                              │
├─────────────────────────────────────────────────────────────┤
│           Order Service (REST Controller)                   │
│           └─→ OrderService (Business Logic)                │
│              └─→ INTERNAL gRPC CALL (HTTP/2)               │
│                 InventoryStockService.CheckStock()         │
│                     ↓ gRPC                                 │
├─────────────────────────────────────────────────────────────┤
│        Inventory Service (gRPC Server)                      │
│        └─→ InventoryStockService Implementation            │
│           └─→ Database Query (Stock Check)                 │
│                     ↓ StockResponse                        │
├─────────────────────────────────────────────────────────────┤
│  Returns: {"inStock": true/false} via gRPC                 │
│  Order Service persists order only if stock available      │
└─────────────────────────────────────────────────────────────┘
```

**Key Point**: gRPC is **internal only** - not exposed to external REST clients. The REST API endpoint (`POST /api/orders`) handles the HTTP request, and gRPC is used internally for service-to-service communication.

---

## Proto File Structure

### Location
```
order-service/src/main/proto/inventory.proto
inventory-service/src/main/proto/inventory.proto
```

Both services contain the same proto definition to ensure contract compatibility.

### Proto File Contents

```protobuf
syntax = "proto3";

package inventory;

option java_multiple_files = true;
option java_package = "com.vaimo.microservices.order.grpc";
option java_outer_classname = "InventoryProto";

// ============ RPC Service Definition ============
service InventoryStockService {
  rpc CheckStock(StockRequest) returns (StockResponse);
}

// ============ Request Message ============
message StockRequest {
  string product_id = 1;     // Unique identifier of the product
  int32 quantity = 2;        // Quantity to check against inventory
}

// ============ Response Message ============
message StockResponse {
  bool in_stock = 1;         // true if requested quantity is available
}
```

### What Each Component Means

| Component | Meaning | Example |
|-----------|---------|---------|
| `syntax = "proto3"` | Protocol Buffers version 3 syntax | Latest standard |
| `package inventory` | Namespace for proto definitions | Prevents name conflicts |
| `option java_package` | Where generated Java classes go | `com.vaimo.microservices.order.grpc` |
| `service InventoryStockService` | gRPC service with methods | Analogous to REST controller |
| `rpc CheckStock(...)` | RPC method (like REST endpoint) | Called from Order Service |
| `message StockRequest` | Input message (like JSON request body) | Contains `product_id` and `quantity` |
| `message StockResponse` | Output message (like JSON response) | Contains `in_stock` boolean |
| Field numbers (1, 2) | Unique identifier for each field | Used in binary serialization |

---

## Data Flow: Creating an Order with gRPC Stock Check

### Sequence Diagram

```
Client                 Order Service              Inventory Service
  │                          │                           │
  ├─ POST /api/orders ───────>│                           │
  │  (ProductRequest)         │                           │
  │                           ├─ Create StockRequest ─────>│
  │                           │  (product_id, quantity)    │
  │                           │                           │
  │                           │<─ Get StockResponse ───────┤
  │                           │  (in_stock: true/false)    │
  │                           │                           │
  │<─ 201 CREATED ────────────┤                           │
  │  (OrderResponse)          │                           │
  │                           │                           │
```

### Step-by-Step Execution

#### 1. **Client sends REST request**
```bash
POST http://localhost:8081/api/orders
Content-Type: application/json

{
  "customer_id": "CUST001",
  "product_id": "PROD001",
  "product_name": "Laptop",
  "quantity": 2,
  "unit_price": 999.99
}
```

#### 2. **Order Service receives REST request**
```java
@PostMapping
public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
    // Business logic triggers here
    Order order = orderService.createOrder(request);
    return ResponseEntity.status(201).body(new OrderResponse(...));
}
```

#### 3. **Order Service makes gRPC call to Inventory Service**
```java
public Order createOrder(OrderRequest request) {
    // Create gRPC StockRequest
    StockRequest stockRequest = StockRequest.newBuilder()
        .setProductId(request.productId())
        .setQuantity(request.quantity())
        .build();
    
    // Call Inventory Service via gRPC
    StockResponse response = inventoryStub.checkStock(stockRequest);
    
    // Check if product is in stock
    if (!response.getInStock()) {
        throw new OutOfStockException("Product not available");
    }
    
    // Persist order only if stock verified
    return orderRepository.save(order);
}
```

#### 4. **Inventory Service processes gRPC request**
```java
@Override
public void checkStock(StockRequest request, StreamObserver<StockResponse> responseObserver) {
    // Query database for stock
    Inventory inventory = inventoryRepository.findByProductId(request.getProductId());
    
    // Check availability
    boolean inStock = inventory != null && inventory.getQuantity() >= request.getQuantity();
    
    // Build response
    StockResponse response = StockResponse.newBuilder()
        .setInStock(inStock)
        .build();
    
    // Send response back to Order Service
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

#### 5. **Client receives REST response**
```json
{
  "id": "ORD-123",
  "customer_id": "CUST001",
  "product_id": "PROD001",
  "quantity": 2,
  "status": "CREATED",
  "created_at": "2026-05-29T10:30:00Z"
}
```

---

## Key Concepts for New Users

### 1. **Proto Buffers (`.proto` files)**
- **Binary format** for representing structured data
- **Smaller** than JSON/XML for same data
- **Strongly typed** - defines exact message structure
- Generated Java classes handle serialization/deserialization

### 2. **gRPC Service Definition**
```protobuf
service InventoryStockService {
  rpc CheckStock(StockRequest) returns (StockResponse);
}
```
- **Service**: Container for RPC methods (like a REST controller)
- **rpc**: Remote Procedure Call method
- **Method signature**: `rpc MethodName(InputMessage) returns (OutputMessage)`

### 3. **Message Types**
```protobuf
message StockRequest {
  string product_id = 1;
  int32 quantity = 2;
}
```
- Think of it like a class with typed fields
- Field numbers (1, 2) are identifiers for serialization, not values
- Cannot be reordered or deleted in future versions

### 4. **Generated Code**
The build process auto-generates Java classes from `.proto` files:

**From `StockRequest`:**
```java
StockRequest.Builder builder = StockRequest.newBuilder();
builder.setProductId("PROD001");
builder.setQuantity(2);
StockRequest request = builder.build();
```

**From `InventoryStockService`:**
```java
// Server side - implement this
class InventoryStockServiceImpl extends InventoryStockServiceGrpc.InventoryStockServiceImplBase {
    @Override
    public void checkStock(StockRequest request, StreamObserver<StockResponse> responseObserver) {
        // Your implementation
    }
}

// Client side - call this
InventoryStockServiceGrpc.InventoryStockServiceBlockingStub stub = ...;
StockResponse response = stub.checkStock(stockRequest);
```

---

## Building gRPC Code

### Maven Build Process

```bash
# Compile proto files and generate Java classes
mvn clean compile
```

**What happens:**
1. Read `.proto` files from `src/main/proto/`
2. Run `protoc` compiler
3. Generate Java classes in `target/generated-sources/protobuf/java/`
4. Compile generated classes into your JAR

### Generated Files

After build, you'll have:

**From `inventory.proto`:**
- `StockRequest.java` - Request message class
- `StockResponse.java` - Response message class
- `InventoryStockServiceGrpc.java` - Service stubs
  - `InventoryStockServiceBlockingStub` - Client side (synchronous)
  - `InventoryStockServiceImplBase` - Server side (extend this)

---

## Dependency Configuration

Both Order and Inventory services require these dependencies:

```xml
<!-- Protocol Buffers -->
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>3.25.5</version>
</dependency>

<!-- gRPC Core -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>1.69.0</version>
</dependency>

<!-- gRPC Protocol Buffers -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>1.69.0</version>
</dependency>

<!-- gRPC Netty (transport layer) -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>1.69.0</version>
</dependency>
```

---

## Testing gRPC Manually

### Option 1: Using cURL (for REST→gRPC flow)

Test the REST endpoint which internally uses gRPC:

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "CUST001",
    "product_id": "PROD001",
    "product_name": "Laptop",
    "quantity": 1,
    "unit_price": 999.99
  }'
```

### Option 2: Using grpcurl (direct gRPC testing)

Install grpcurl:
```bash
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

List available services:
```bash
grpcurl -plaintext localhost:9095 list
```

Call CheckStock directly:
```bash
grpcurl -plaintext -d '{"product_id":"PROD001","quantity":2}' \
  localhost:9095 inventory.InventoryStockService/CheckStock
```

---

## Common Issues & Solutions

### Issue 1: Proto File Changes Not Reflected
**Solution**: Clean and rebuild
```bash
mvn clean compile
```

### Issue 2: Generated Classes Not Found
**Solution**: Ensure `maven-protobuf-plugin` is configured in `pom.xml` with correct paths

### Issue 3: gRPC Port Already in Use
**Default gRPC port for this project**: 9095 (configured via `inventory.grpc.port` in `order-service/application.properties` and `grpc.server.port` in `inventory-service/application.properties`)

```properties
# order-service/application.properties
inventory.grpc.host=localhost
inventory.grpc.port=9095

# inventory-service/application.properties
grpc.server.port=9095
```

### Issue 4: Service Not Reachable
**Check**:
1. Both services are running
2. Firewall allows gRPC port communication
3. Service URLs match in client configuration

---

## Future Enhancements

### 1. **Bidirectional Streaming**
Current: Unary (single request → single response)
```protobuf
rpc CheckStocks(stream StockRequest) returns (stream StockResponse);
```

### 2. **Error Handling**
Add status codes to responses:
```protobuf
message StockResponse {
  bool in_stock = 1;
  string error_message = 2;
  int32 status_code = 3;
}
```

### 3. **gRPC Interceptors**
Add logging, authentication, metrics:
```java
// Server interceptor for logging
class LoggingInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(...) {
        log.info("gRPC call: {}", method.getFullMethodName());
        // ...
    }
}
```

### 4. **gRPC Reflection**
Enable service discovery:
```xml
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-services</artifactId>
    <version>1.69.0</version>
</dependency>
```

---

## Quick Reference

| Aspect | Details |
|--------|---------|
| **Protocol** | HTTP/2 with Protocol Buffers |
| **Service Type** | Internal (service-to-service) |
| **Port** | 9095 (configured in `inventory.grpc.port`) |
| **Request** | StockRequest (product_id, quantity) |
| **Response** | StockResponse (in_stock: bool) |
| **Flow** | Order Service → Inventory Service |
| **Use Case** | Stock verification before order creation |
| **Performance Benefit** | Binary serialization, multiplexing, lower latency |

---

## Related Files

| File | Purpose |
|------|---------|
| `order-service/src/main/proto/inventory.proto` | gRPC contract & messages |
| `inventory-service/src/main/proto/inventory.proto` | Same contract (must match) |
| `order-service/src/main/java/.../grpc/` | Generated stubs & client usage |
| `inventory-service/src/main/java/.../grpc/` | Generated stubs & server implementation |
| `pom.xml` | Proto compiler & gRPC dependencies |

---

## Additional Resources

- [gRPC Official Docs](https://grpc.io/docs/)
- [Protocol Buffers Guide](https://developers.google.com/protocol-buffers)
- [gRPC Java Tutorial](https://grpc.io/docs/languages/java/)
- Original PDF Guide: `gRPC-Order-Inventory-Guide.pdf` (this folder)

