#!/bin/bash

# Dynamic service discovery from docker-compose files
# Automatically detects services, containers, and ports without hardcoding

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JSON_MODE=false
RESTART_TARGET=""

print_usage() {
    cat <<EOF
Usage: bash check-services.sh [--json] [--restart <service-name>] [--help]

Options:
  --json                      Print status in JSON format.
  --restart <service-name>    Restart a service.
  --help                      Show this help message.
EOF
}

# Parse arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --json)
            JSON_MODE=true
            ;;
        --restart)
            if [ -n "$2" ]; then
                RESTART_TARGET="$2"
                shift
            else
                echo "Missing value for --restart" >&2
                exit 1
            fi
            ;;
        --help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            print_usage
            exit 1
            ;;
    esac
    shift
done

if [ -n "$RESTART_TARGET" ]; then
    bash "$PROJECT_ROOT/restart-service.sh" "$RESTART_TARGET"
    exit $?
fi

# Utility functions
http_status() {
    local url=$1
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$url" 2>/dev/null)
    if [ -z "$code" ]; then
        echo "000"
    else
        echo "$code"
    fi
}

tcp_port_open() {
    local port=$1
    (echo > "/dev/tcp/127.0.0.1/$port") > /dev/null 2>&1
}

port_listening() {
    local port=$1
    ss -ltn "sport = :$port" 2>/dev/null | awk 'NR>1 {print; exit}' | grep -q .
}

get_service_status() {
    local service=$1
    local port=$2
    local debug_port=$3
    local debug_enabled=false

    if port_listening "$debug_port"; then
        debug_enabled=true
    fi

    # Handle Kafka UI check path differently since it uses /kafka-ui instead of standard actuators
    if [ "$service" = "kafka-service" ]; then
        health_code=$(http_status "http://localhost:$port/kafka-ui")
        root_code=$(http_status "http://localhost:$port/kafka-ui")
    else
        health_code=$(http_status "http://localhost:$port/actuator/health")
        root_code=$(http_status "http://localhost:$port/")
    fi

    # Explicit check logic: HTTP 200, 302 (Redirects), or active socket responses indicate running status
    if [ "$health_code" = "200" ] || [ "$health_code" = "302" ] || [ "$root_code" != "000" ] || [ "$health_code" != "000" ]; then
        if [ "$debug_enabled" = true ]; then
            echo "RUNNING_DEBUG"
        else
            echo "RUNNING"
        fi
    elif [ "$debug_enabled" = true ]; then
        echo "WAITING_FOR_DEBUGGER"
    elif tcp_port_open "$port"; then
        echo "STARTING"
    else
        echo "STOPPED"
    fi
}

print_service_status() {
    local service=$1
    local port=$2
    local debug_port=$3
    local status=$4

    case "$status" in
        RUNNING)
            echo -e "${GREEN}✓${NC} $service (port $port) - ${GREEN}RUNNING${NC}"
            ;;
        RUNNING_DEBUG)
            echo -e "${GREEN}✓${NC} $service (port $port) - ${GREEN}RUNNING${NC} (${YELLOW}debug enabled${NC})"
            ;;
        WAITING_FOR_DEBUGGER)
            echo -e "${YELLOW}⚠${NC} $service (port $port) - ${YELLOW}WAITING FOR DEBUGGER${NC} (debug port $debug_port is open)"
            ;;
        STARTING)
            echo -e "${YELLOW}⚠${NC} $service (port $port) - ${YELLOW}STARTING${NC}"
            ;;
        *)
            echo -e "${RED}✗${NC} $service (port $port) - ${RED}STOPPED${NC}"
            ;;
    esac
}

container_state() {
    local container=$1
    local status
    status=$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null)
    if [ -n "$status" ]; then
        echo "$status"
    else
        echo "not_created"
    fi
}

is_running_status() {
    local status=$1
    [ "$status" = "RUNNING" ] || [ "$status" = "RUNNING_DEBUG" ]
}

# Discover microservices dynamically
declare -a SERVICES
declare -a PORTS
declare -a DEBUG_PORTS

# Map of known services with their debug ports (Expanded to include notification-service)
declare -A DEBUG_PORT_MAP=(
    [api-gateway]="5005"
    [product-service]="5006"
    [order-service]="5007"
    [inventory-service]="5008"
    [kafka-service]="5009"
    [notification-service]="5010"
)

# Get service info from docker-compose ps output (JSON)
if docker compose ps --format json > /tmp/compose-services.json 2>/dev/null; then
    # Parse services from docker-compose ps
    while read -r line; do
        service=$(echo "$line" | jq -r '.Service' 2>/dev/null)

        if [ -n "$service" ] && [[ "$service" =~ ^(api-gateway|product-service|order-service|inventory-service|kafka-service|notification-service)$ ]]; then
            SERVICES+=("$service")

            # Map known ports for microservices
            case "$service" in
                api-gateway)
                    PORTS+=(9000)
                    ;;
                product-service)
                    PORTS+=(8080)
                    ;;
                order-service)
                    PORTS+=(8081)
                    ;;
                inventory-service)
                    PORTS+=(8082)
                    ;;
                kafka-service)
                    PORTS+=(8989)
                    ;;
                notification-service)
                    PORTS+=(8083)
                    ;;
            esac

            DEBUG_PORTS+=("${DEBUG_PORT_MAP[$service]}")
        fi
    done < <(jq -c '.[]' /tmp/compose-services.json 2>/dev/null || echo "")
fi

# Fallback to hardcoded if docker-compose ps fails (Expanded to include notification-service)
if [ ${#SERVICES[@]} -eq 0 ]; then
    SERVICES=("api-gateway" "product-service" "order-service" "inventory-service" "kafka-service" "notification-service")
    PORTS=(9000 8080 8081 8082 8989 8083)
    DEBUG_PORTS=(5005 5006 5007 5008 5009 5010)
fi

# Get all containers dynamically
declare -a ALL_CONTAINERS
while read -r container; do
    ALL_CONTAINERS+=("$container")
done < <(docker ps -a --format "{{.Names}}" 2>/dev/null | sort)

if [ "$JSON_MODE" = false ]; then
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}Microservices Status Check${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo -e "\n${BLUE}Checking services...${NC}\n"
fi

total=0
running=0
declare -a SERVICE_STATUSES
declare -a CONTAINER_STATUSES

# Check microservices
for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    PORT="${PORTS[$i]}"
    DEBUG_PORT="${DEBUG_PORTS[$i]}"
    STATUS=$(get_service_status "$SERVICE" "$PORT" "$DEBUG_PORT")

    SERVICE_STATUSES+=("$STATUS")
    ((total++))

    if [ "$JSON_MODE" = false ]; then
        print_service_status "$SERVICE" "$PORT" "$DEBUG_PORT" "$STATUS"
    fi

    if is_running_status "$STATUS"; then
        ((running++))
    fi
done

# Check all containers
for container in "${ALL_CONTAINERS[@]}"; do
    CONTAINER_STATUSES+=("$(container_state "$container")")
done

if [ "$JSON_MODE" = true ]; then
    printf '{\n'
    printf '  "summary": {"running": %d, "total": %d},\n' "$running" "$total"
    printf '  "services": [\n'
    for i in "${!SERVICES[@]}"; do
        comma=","
        if [ "$i" -eq $((${#SERVICES[@]} - 1)) ]; then
            comma=""
        fi

        running_flag="false"
        if is_running_status "${SERVICE_STATUSES[$i]}"; then
            running_flag="true"
        fi

        printf '    {"name": "%s", "port": %s, "debugPort": %s, "status": "%s", "running": %s}%s\n' \
            "${SERVICES[$i]}" "${PORTS[$i]}" "${DEBUG_PORTS[$i]}" "${SERVICE_STATUSES[$i]}" "$running_flag" "$comma"
    done
    printf '  ],\n'

    printf '  "containers": [\n'
    for i in "${!ALL_CONTAINERS[@]}"; do
        comma=","
        if [ "$i" -eq $((${#ALL_CONTAINERS[@]} - 1)) ]; then
            comma=""
        fi
        printf '    {"name": "%s", "status": "%s"}%s\n' "${ALL_CONTAINERS[$i]}" "${CONTAINER_STATUSES[$i]}" "$comma"
    done
    printf '  ]\n'
    printf '}\n'
    exit 0
fi

echo -e "\n${BLUE}========================================${NC}"
echo -e "Status summary: ${running}/${total} services running successfully."
echo -e "${BLUE}========================================${NC}"
