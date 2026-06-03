# Alternative: Docker Compose Extend Pattern (Docker Compose < 2.20)

If your Docker Compose version is older than 2.20 and doesn't support `include`, use this alternative approach.

## Check Your Docker Compose Version

```bash
docker-compose version
```

- ✅ 2.20+: Use the current `docker-compose.yaml` (recommended)
- ❌ < 2.20: Use this alternative

## Alternative Approach: Using Extends

If you need to support older Docker Compose versions, replace `/MicroServices/docker-compose.yaml` with this alternative:

### Option A: Base Service Files Approach

Create `docker-compose.base.yaml`:

```yaml
version: '3.8'

services:
  # This file defines base services that can be extended
  db-base: &db-base
    restart: always

networks:
  microservices:
    driver: bridge
```

Then in the main `docker-compose.yaml`, use `extends`:

```yaml
version: '3.8'

services:
  product-mongo:
    extends:
      file: docker-compose.base.yaml
      service: db-base
    image: mongo:7.0.5
    container_name: product-mongo
    ports:
      - "27018:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: password
      MONGO_INITDB_DATABASE: product-service
    volumes:
      - ./product-service/data:/data/db:Z
    networks:
      - microservices

  order-mysql:
    extends:
      file: docker-compose.base.yaml
      service: db-base
    image: mysql:8.3.0
    container_name: order-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: mysql
    volumes:
      - ./order-service/mysql-data:/var/lib/mysql:Z
      - ./order-service/docker/mysql/init.sql:/docker-entrypoint-initdb.d/init.sql:Z
    networks:
      - microservices

  inventory-postgres:
    extends:
      file: docker-compose.base.yaml
      service: db-base
    image: postgres:16.3
    container_name: inventory-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_PASSWORD: postgres
      POSTGRES_USER: postgres
      POSTGRES_DB: inventory_service
    volumes:
      - ./inventory-service/postgres-data:/var/lib/postgresql/data:Z
      - ./inventory-service/docker/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:Z
    networks:
      - microservices

networks:
  microservices:
    driver: bridge
```

**.advantages:**
- Works with Docker Compose 3.x+
- Still doesn't modify individual service files
- Central location for all databases

### Option B: Simple Manual Composition

If `extends` doesn't work, manually copy all service definitions into root docker-compose.yaml:

```bash
# Note: This modifies the root file but keeps service files unchanged
cat product-service/docker-compose.yaml >> docker-compose.yaml
cat order-service/docker-compose.yaml >> docker-compose.yaml
cat inventory-service/docker-compose.yaml >> docker-compose.yaml
```

Then manually manage service definitions.

## Upgrade Docker Compose (Recommended)

The easiest solution is to upgrade Docker Desktop:

### On macOS
```bash
# Update via Homebrew
brew install docker-desktop
# Or download from: https://www.docker.com/products/docker-desktop
```

### On Windows
```powershell
# Update via Store or direct download
# https://www.docker.com/products/docker-desktop
```

### On Linux
```bash
# Upgrade Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
docker-compose version
```

---

## Recommendation

🎯 **Upgrade to Docker Compose 2.20+** - It's the cleanest and most maintainable approach.

The current `docker-compose.yaml` using `include` is the recommended solution for:
- ✅ No modification to service files
- ✅ Cleaner configuration
- ✅ Easier to maintain
- ✅ Better performance


