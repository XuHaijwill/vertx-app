#!/bin/bash
# ============================================================
# stop.sh - vertx-app shutdown script
# Usage: ./stop.sh [options]
#   ./stop.sh           # graceful stop (SIGTERM)
#   ./stop.sh -9        # force kill (SIGKILL)
#   ./stop.sh --status  # show running status
# ============================================================

set -e

# ---- Paths ----
APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$APP_HOME/app.pid"
CONFIG_DIR="$APP_HOME/config"
LOG_DIR="$APP_HOME/logs"

# ---- Colors ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}==>${NC}   $1"; }

# ---- Read port from active profile (fallback: scan common ports) ----

get_active_port() {
    # Try to detect from PID's command line
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            local cmd=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
            if [[ -z "$cmd" ]]; then
                # macOS fallback
                cmd=$(ps -p $pid -o args= 2>/dev/null)
            fi
            # Extract profile from -Dapp.profile=XXX
            local profile=$(echo "$cmd" | grep -oP '(?<=-Dapp.profile=)\S+' || true)
            if [[ -n "$profile" ]]; then
                local yml="$CONFIG_DIR/application-${profile}.yml"
                if [[ -f "$yml" ]]; then
                    local port=$(grep -A2 '^app:' "$yml" | grep 'http-port' | awk '{print $2}' | tr -d '"' | tr -d "'" | head -1)
                    echo "${port:-8888}"
                    return
                fi
            fi
        fi
    fi
    echo "8888"
}

# ---- Stop logic ----

stop_graceful() {
    local pid=$1
    log_info "Sending SIGTERM to PID $pid..."
    kill "$pid" 2>/dev/null

    local waited=0
    local max_wait=15
    while [[ $waited -lt $max_wait ]]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            log_info "Process $pid stopped gracefully."
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
        printf "\r${YELLOW}[WAIT]${NC}  Shutting down... (${waited}s/${max_wait}s)"
    done

    echo ""
    log_warn "Graceful shutdown timed out, forcing kill..."
    kill -9 "$pid" 2>/dev/null || true
    log_info "Process $pid killed."
}

stop_by_pid_file() {
    if [[ ! -f "$PID_FILE" ]]; then
        return 1
    fi

    local pid=$(cat "$PID_FILE")

    if ! kill -0 "$pid" 2>/dev/null; then
        log_warn "PID $pid from $PID_FILE is not running (stale PID file)"
        rm -f "$PID_FILE"
        return 1
    fi

    log_step "Stopping vertx-app (PID: $pid)..."
    stop_graceful "$pid"
    rm -f "$PID_FILE"
    return 0
}

stop_by_port() {
    local port=$1
    local pids=()

    if command -v lsof &>/dev/null; then
        pids=($(lsof -ti:$port 2>/dev/null))
    elif command -v ss &>/dev/null; then
        pids=($(ss -tlnp "sport = :$port" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | sort -u))
    elif command -v netstat &>/dev/null; then
        pids=($(netstat -tlnp 2>/dev/null | grep ":$port " | awk '{print $NF}' | cut -d'/' -f1 | sort -u))
    fi

    if [[ ${#pids[@]} -eq 0 ]]; then
        return 1
    fi

    for pid in "${pids[@]}"; do
        log_step "Found process on port $port (PID: $pid), stopping..."
        stop_graceful "$pid"
    done
    return 0
}

show_status() {
    log_step "vertx-app status:"
    echo ""

    local running=false

    # Check PID file
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo -e "  PID:     $pid ${GREEN}(running)${NC}"
            running=true

            # Show profile and port
            local port=$(get_active_port)
            echo -e "  Port:    $port"

            # Show uptime
            local start=$(ps -p $pid -o lstart= 2>/dev/null || echo "unknown")
            echo -e "  Started: $start"
        else
            echo -e "  PID:     $pid ${RED}(not running — stale PID file)${NC}"
        fi
    else
        echo "  PID file not found"
    fi

    echo ""

    # Check ports
    local ports=(8881 8888 8889 80)
    for port in "${ports[@]}"; do
        if command -v lsof &>/dev/null; then
            if lsof -i:$port &>/dev/null; then
                echo -e "  Port $port: ${RED}IN USE${NC}"
            fi
        elif command -v ss &>/dev/null; then
            if ss -tlnp "sport = :$port" 2>/dev/null | grep -q ":"; then
                echo -e "  Port $port: ${RED}IN USE${NC}"
            fi
        fi
    done

    if [[ "$running" == false ]]; then
        echo -e "  Status:  ${YELLOW}NOT RUNNING${NC}"
    fi

    echo ""
    echo "  Log: $LOG_DIR/app.log"
}

# ---- Main ----

case "${1:-}" in
    --status|-s)
        show_status
        ;;
    -9|--force)
        if [[ -f "$PID_FILE" ]]; then
            local pid=$(cat "$PID_FILE")
            if kill -0 "$pid" 2>/dev/null; then
                log_step "Force killing PID $pid..."
                kill -9 "$pid" 2>/dev/null || true
                rm -f "$PID_FILE"
                log_info "Killed."
            else
                log_warn "PID $pid not running, cleaning up PID file"
                rm -f "$PID_FILE"
            fi
        else
            # Try by common ports
            for port in 8881 8888 8889 80; do
                stop_by_port "$port" && break
            done
        fi
        ;;
    ""|--graceful)
        # Try PID file first, then port scan
        if ! stop_by_pid_file; then
            local port=$(get_active_port)
            if ! stop_by_port "$port"; then
                # Try common ports
                local found=false
                for p in 8881 8888 8889 80; do
                    if stop_by_port "$p"; then
                        found=true
                        break
                    fi
                done
                if [[ "$found" == false ]]; then
                    log_warn "No running vertx-app found."
                fi
            fi
        fi
        ;;
    *)
        echo "Usage: ./stop.sh [--status|--force|-9]"
        exit 1
        ;;
esac
