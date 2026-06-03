#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SERVICES=("api-gateway" "product-service" "order-service" "inventory-service" "kafka-service" "notification-service")
PORTS=("9000" "8080" "8081" "8082" "8989" "8083")
DEBUG_PORTS=("5005" "5006" "5007" "5008" "5009" "5010")
SELECTED_SERVICES=()
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
BUILD_LOG="$LOG_DIR/build.log"
PID_FILE="$PROJECT_ROOT/.microservices.pids"
DEBUG_MODE=false
DEBUG_SUSPEND="n"

# Set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/lib/jvm/temurin-21-jdk"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Microservices Startup Script${NC}"
echo -e "${BLUE}========================================${NC}"

if [ "$DEBUG_MODE" = true ]; then
    print_warning "Debug mode enabled (suspend=${DEBUG_SUSPEND})"
fi

# Function to print status
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_usage() {
    cat <<EOF
Usage: bash start-services.sh [--debug] [--debug-suspend] [service-flags]

Options:
  --debug          Start all services with remote debugging enabled.
  --debug-suspend  Start all services in debug mode and wait for the debugger to attach before application startup.
  --api-gateway    Start only api-gateway (can combine with other service flags).
  --product-service
  --order-service
  --inventory-service
  --help           Show this help message.

Remote debug ports:
  api-gateway       5005
  product-service   5006
  order-service     5007
  inventory-service 5008
EOF
}

add_selected_service() {
    local service=$1
    for existing in "${SELECTED_SERVICES[@]}"; do
        if [ "$existing" = "$service" ]; then
            return
        fi
    done
    SELECTED_SERVICES+=("$service")
}

while [ $# -gt 0 ]; do
    case "$1" in
        --debug)
            DEBUG_MODE=true
            ;;
        --debug-suspend)
            DEBUG_MODE=true
            DEBUG_SUSPEND="y"
            ;;
        --api-gateway)
            add_selected_service "api-gateway"
            ;;
        --product-service)
            add_selected_service "product-service"
            ;;
        --order-service)
            add_selected_service "order-service"
            ;;
        --inventory-service)
            add_selected_service "inventory-service"
            ;;
        --help)
            print_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo
            print_usage
            exit 1
            ;;
    esac
    shift
done

ACTIVE_SERVICES=()
ACTIVE_PORTS=()
ACTIVE_DEBUG_PORTS=()

if [ ${#SELECTED_SERVICES[@]} -eq 0 ]; then
    ACTIVE_SERVICES=("${SERVICES[@]}")
    ACTIVE_PORTS=("${PORTS[@]}")
    ACTIVE_DEBUG_PORTS=("${DEBUG_PORTS[@]}")
else
    for selected in "${SELECTED_SERVICES[@]}"; do
        for i in "${!SERVICES[@]}"; do
            if [ "${SERVICES[$i]}" = "$selected" ]; then
                ACTIVE_SERVICES+=("${SERVICES[$i]}")
                ACTIVE_PORTS+=("${PORTS[$i]}")
                ACTIVE_DEBUG_PORTS+=("${DEBUG_PORTS[$i]}")
                break
            fi
        done
    done
fi

# Check if docker is running
echo -e "\n${BLUE}Checking Docker...${NC}"
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker first."
    exit 1
fi
print_status "Docker is running"

# Prepare runtime directories
mkdir -p "$LOG_DIR"
print_status "Logs directory ready: $LOG_DIR"
rm -f "$PID_FILE"

# Clean up legacy root-level service logs
for SERVICE in "${SERVICES[@]}"; do
    rm -f "$PROJECT_ROOT/${SERVICE}.log"
done

# Start databases from root docker-compose.yaml
echo -e "\n${BLUE}Starting all databases...${NC}"
cd "$PROJECT_ROOT"
docker-compose up -d
if [ $? -eq 0 ]; then
    print_status "All databases started successfully"
    print_warning "The root docker-compose.yaml includes configurations from:"
    echo -e "  • product-service/docker-compose.yaml (MongoDB)"
    echo -e "  • order-service/docker-compose.yaml (MySQL)"
    echo -e "  • inventory-service/docker-compose.yaml (PostgreSQL)"
else
    print_error "Failed to start databases"
    exit 1
fi

# Wait for databases to be ready
echo -e "\n${BLUE}Waiting for databases to be healthy...${NC}"
sleep 10

# Build all modules
echo -e "\n${BLUE}Building all modules...${NC}"
export JAVA_HOME="/usr/lib/jvm/temurin-21-jdk"
./mvnw clean package -DskipTests -q > "$BUILD_LOG" 2>&1
if [ $? -eq 0 ]; then
    print_status "All modules built successfully"
    print_warning "Build logs: $BUILD_LOG"
else
    print_error "Build failed. Check: $BUILD_LOG"
    exit 1
fi

# Start services
echo -e "\n${BLUE}Starting microservices...${NC}"
for i in "${!ACTIVE_SERVICES[@]}"; do
    SERVICE="${ACTIVE_SERVICES[$i]}"
    PORT="${ACTIVE_PORTS[$i]}"

    echo -e "\n${BLUE}Starting ${SERVICE} on port ${PORT}...${NC}"

    # Start service in background
    cd "$PROJECT_ROOT/$SERVICE"
    export JAVA_HOME="/usr/lib/jvm/temurin-21-jdk"
    STARTUP_LOG="$LOG_DIR/startup-${SERVICE}.log"
    if [ "$DEBUG_MODE" = true ]; then
        DEBUG_PORT="${ACTIVE_DEBUG_PORTS[$i]}"
        nohup ./mvnw spring-boot:run "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=*:${DEBUG_PORT}" > "$STARTUP_LOG" 2>&1 &
    else
        nohup ./mvnw spring-boot:run > "$STARTUP_LOG" 2>&1 &
    fi
    PID=$!

    # Save PID for later
    echo $PID >> "$PID_FILE"

    print_status "${SERVICE} started (PID: $PID)"
    print_warning "Startup logs: tail -f $STARTUP_LOG"
    print_warning "Application logs: tail -f $PROJECT_ROOT/$SERVICE/logs/${SERVICE}.log"
    if [ "$DEBUG_MODE" = true ]; then
        print_warning "Remote debug: attach IntelliJ to localhost:${DEBUG_PORT}"
    fi

    # Wait a bit before starting next service to avoid startup conflicts
    sleep 5
done

echo -e "\n${BLUE}========================================${NC}"
echo -e "${GREEN}All microservices started!${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "\nServices running on:"
for i in "${!ACTIVE_SERVICES[@]}"; do
    echo -e "  ${GREEN}${ACTIVE_SERVICES[$i]}${NC}: http://localhost:${ACTIVE_PORTS[$i]}"
done

echo -e "\n${YELLOW}To view logs:${NC}"
echo -e "  tail -f $BUILD_LOG"
for SERVICE in "${ACTIVE_SERVICES[@]}"; do
    echo -e "  tail -f $LOG_DIR/startup-${SERVICE}.log"
    echo -e "  tail -f $PROJECT_ROOT/$SERVICE/logs/${SERVICE}.log"
    echo -e "  tail -f $PROJECT_ROOT/$SERVICE/logs/access.*.log"
done

if [ "$DEBUG_MODE" = true ]; then
    echo -e "\n${YELLOW}Remote debug ports:${NC}"
    for i in "${!ACTIVE_SERVICES[@]}"; do
        echo -e "  ${GREEN}${ACTIVE_SERVICES[$i]}${NC}: localhost:${ACTIVE_DEBUG_PORTS[$i]}"
    done
fi

echo -e "\n${YELLOW}To stop all services:${NC}"
echo -e "  bash $PROJECT_ROOT/stop-services.sh"

echo -e "\n${YELLOW}To check service status:${NC}"
echo -e "  bash $PROJECT_ROOT/check-services.sh"

