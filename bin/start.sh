#!/bin/bash
# ============================================================
# start.sh - vertx-app startup script
# Usage: ./start.sh [profile] [options]
#   ./start.sh              # default DEV profile
#   ./start.sh UAT          # start with UAT profile
#   ./start.sh PROD         # start with PROD profile
#   ./start.sh DEV --build  # rebuild before starting
# ============================================================

set -e

# ---- Paths ----
APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_NAME="vertx-app-1.0.0-SNAPSHOT.jar"
JAR_PATH="$APP_HOME/target/$JAR_NAME"
CONFIG_DIR="$APP_HOME/config"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/app.pid"

# ---- Defaults ----
PROFILE="${1:-DEV}"
BUILD_FLAG="${2:-}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

# ---- Colors ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ---- Pre-flight checks ----

check_java() {
    if ! command -v java &>/dev/null; then
        log_error "Java not found. Install JDK 21+ and add to PATH."
        exit 1
    fi
    local ver=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
    if [[ "$ver" -lt 21 ]]; then
        log_error "Java 21+ required, found: $(java -version 2>&1 | head -1)"
        exit 1
    fi
    log_info "Java: $(java -version 2>&1 | head -1)"
}

check_port() {
    local port=$1
    if command -v ss &>/dev/null; then
        ss -tlnp 2>/dev/null | grep -q ":$port " && return 0
    elif command -v netstat &>/dev/null; then
        netstat -tlnp 2>/dev/null | grep -q ":$port " && return 0
    elif command -v lsof &>/dev/null; then
        lsof -i:$port &>/dev/null && return 0
    fi
    return 1
}

read_port_from_profile() {
    local profile=$1
    local yml="$CONFIG_DIR/application-${profile}.yml"
    local port=8888
    if [[ -f "$yml" ]]; then
        port=$(grep -A2 '^app:' "$yml" | grep 'http-port' | awk '{print $2}' | tr -d '"' | tr -d "'" | head -1)
        port=${port:-8888}
    fi
    echo "$port"
}

check_already_running() {
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            log_error "Application already running (PID: $pid)"
            log_error "Use ./stop.sh first, or: kill $pid"
            exit 1
        else
            log_warn "Stale PID file found, removing..."
            rm -f "$PID_FILE"
        fi
    fi
}

# ---- Build ----

build_app() {
    log_info "Building application..."
    cd "$APP_HOME"
    if command -v mvn &>/dev/null; then
        mvn package -DskipTests -q
    elif [[ -x "$APP_HOME/mvnw" ]]; then
        ./mvnw package -DskipTests -q
    else
        log_error "Maven not found. Install Maven or use mvnw."
        exit 1
    fi
    log_info "Build complete."
}

# ---- Start ----

start_app() {
    cd "$APP_HOME"

    if [[ ! -f "$JAR_PATH" ]]; then
        log_error "JAR not found: $JAR_PATH"
        log_error "Run with --build flag: ./start.sh $PROFILE --build"
        exit 1
    fi

    local port=$(read_port_from_profile "$PROFILE")

    if check_port "$port"; then
        log_error "Port $port already in use!"
        log_error "Check: ss -tlnp | grep $port"
        exit 1
    fi

    mkdir -p "$LOG_DIR"

    log_info "=========================================="
    log_info "  vertx-app starting"
    log_info "  Profile:  $PROFILE"
    log_info "  Port:     $port"
    log_info "  Config:   $CONFIG_DIR/application-${PROFILE}.yml"
    log_info "  JVM:      $JAVA_OPTS"
    log_info "=========================================="

    nohup java $JAVA_OPTS \
        -Dapp.profile="$PROFILE" \
        -jar "$JAR_PATH" \
        > "$LOG_DIR/app.log" 2>&1 &

    local pid=$!
    echo "$pid" > "$PID_FILE"

    # Wait and verify startup
    local waited=0
    local max_wait=30
    while [[ $waited -lt $max_wait ]]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            log_error "Process died! Check logs: $LOG_DIR/app.log"
            rm -f "$PID_FILE"
            exit 1
        fi
        if check_port "$port"; then
            echo ""
            log_info "Application started successfully!"
            log_info "  PID:      $pid"
            log_info "  Health:   http://localhost:$port/health"
            log_info "  Users:    http://localhost:$port/api/users"
            log_info "  Products: http://localhost:$port/api/products"
            log_info "  Swagger:  http://localhost:$port/docs"
            log_info "  Log:      $LOG_DIR/app.log"
            log_info "  Stop:     ./stop.sh"
            exit 0
        fi
        sleep 1
        waited=$((waited + 1))
        printf "\r${YELLOW}[WAIT]${NC}  Waiting for port $port... (${waited}s/${max_wait}s)"
    done

    log_warn "App is running but port $port not detected within ${max_wait}s"
    log_warn "Check logs: tail -f $LOG_DIR/app.log"
}

# ---- Main ----

check_java
check_already_running

if [[ "$BUILD_FLAG" == "--build" || "$BUILD_FLAG" == "-b" ]]; then
    build_app
fi

start_app
