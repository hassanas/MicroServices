#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICES=("api-gateway" "product-service" "order-service" "inventory-service" "kafka-service" "notification-service")
PORTS=("9000" "8080" "8081" "8082" "8989" "8083")
SELECTED_SERVICES=()

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Stopping Microservices${NC}"
echo -e "${BLUE}========================================${NC}"

# Function to print status
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_usage() {
    cat <<EOF
Usage: bash stop-services.sh [service-flags]

Service flags:
  --api-gateway
  --product-service
  --order-service
  --inventory-service
  --kafka-service
  --notification-service

Examples:
  bash stop-services.sh                           # stop everything + containers
  bash stop-services.sh --product-service         # stop only product-service
  bash stop-services.sh --notification-service    # stop only notification-service
  bash stop-services.sh --product-service --inventory-service
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

stop_single_service() {
    local service=$1
    local port=$2

    local pids_on_port
    pids_on_port=$(lsof -ti :"$port" 2>/dev/null | tr '\n' ' ')
    if [ -n "$pids_on_port" ]; then
        kill $pids_on_port 2>/dev/null
        print_status "Stopped ${service} on port ${port} (PID(s): ${pids_on_port})"
    else
        print_warning "No process found on port ${port} for ${service}"
    fi

    pkill -f "/${service}/.*spring-boot:run" 2>/dev/null || true
}

for arg in "$@"; do
    case "$arg" in
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
        --kafka-service)
            add_selected_service "kafka-service"
            ;;
        --notification-service)
            add_selected_service "notification-service"
            ;;
        --help)
            print_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $arg"
            print_usage
            exit 1
            ;;
    esac
done

if [ ${#SELECTED_SERVICES[@]} -eq 0 ]; then
    SELECTED_SERVICES=("${SERVICES[@]}")
    STOP_ALL=true
else
    STOP_ALL=false
fi

echo -e "\n${BLUE}Stopping selected services...${NC}"
for service in "${SELECTED_SERVICES[@]}"; do
    for i in "${!SERVICES[@]}"; do
        if [ "${SERVICES[$i]}" = "$service" ]; then
            stop_single_service "$service" "${PORTS[$i]}"
            break
        fi
    done
done

if [ "$STOP_ALL" = true ]; then
    # Kill services from PID file for full-stop mode.
    if [ -f "$PROJECT_ROOT/.microservices.pids" ]; then
        while read -r PID; do
            if ps -p "$PID" > /dev/null 2>&1; then
                kill "$PID" 2>/dev/null
            fi
        done < "$PROJECT_ROOT/.microservices.pids"
        rm -f "$PROJECT_ROOT/.microservices.pids"
    fi

    echo -e "\n${BLUE}Stopping any remaining service processes...${NC}"
    pkill -f "spring-boot:run" 2>/dev/null || true
    print_status "Spring Boot processes stopped"

    echo -e "\n${BLUE}Stopping containers...${NC}"
    cd "$PROJECT_ROOT"
    docker-compose down
    if [ $? -eq 0 ]; then
        print_status "Containers stopped successfully"
    else
        print_error "Error stopping containers"
    fi

    echo -e "\n${GREEN}All services stopped!${NC}"
else
    # Keep only active PIDs in PID file after partial stop.
    if [ -f "$PROJECT_ROOT/.microservices.pids" ]; then
        tmp_file=$(mktemp)
        while read -r PID; do
            if ps -p "$PID" > /dev/null 2>&1; then
                echo "$PID" >> "$tmp_file"
            fi
        done < "$PROJECT_ROOT/.microservices.pids"
        mv "$tmp_file" "$PROJECT_ROOT/.microservices.pids"
    fi

    echo -e "\n${GREEN}Selected services stopped. Other services and containers are still running.${NC}"
fi
