#!/usr/bin/env bash
# =============================================================================
# PowerAuth Observability Demo
# =============================================================================
# Demonstrates structured logging, distributed tracing, and metrics using the
# Grafana LGTM stack (Loki + Grafana + Tempo + Metrics/Prometheus).
#
# Usage:
#   ./demo.sh [--skip-build] [--stop]
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENTS_DIR="$SCRIPT_DIR/agents"
WAR_DEST="$REPO_ROOT/powerauth-java-server/docker/images-and-libs"

OTEL_AGENT_VERSION="2.26.1"
OTEL_AGENT_JAR="opentelemetry-javaagent.jar"
OTEL_AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/${OTEL_AGENT_JAR}"

PAS_BASE_URL="http://localhost:8080/powerauth-java-server"
CORRELATION_ID="demo-$(date +%s)-observability"

BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
RESET="\033[0m"

info()    { echo -e "${CYAN}[INFO]${RESET} $*"; }
success() { echo -e "${GREEN}[OK]${RESET}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET} $*"; }
error()   { echo -e "${RED}[ERR]${RESET}  $*" >&2; }
header()  { echo -e "\n${BOLD}━━━ $* ━━━${RESET}"; }

# ── Handle --stop ─────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--stop" ]]; then
    header "Stopping demo stack"
    cd "$SCRIPT_DIR" && docker compose down -v
    success "Demo stack stopped and volumes removed."
    exit 0
fi

SKIP_BUILD=false
if [[ "${1:-}" == "--skip-build" ]]; then
    SKIP_BUILD=true
fi

# ── 1. Prerequisites ──────────────────────────────────────────────────────────
header "Checking prerequisites"

check_cmd() {
    if ! command -v "$1" &>/dev/null; then
        error "Required command not found: $1"
        exit 1
    fi
    success "$1 found"
}

check_cmd docker
check_cmd curl
if ! $SKIP_BUILD; then
    check_cmd mvn
    JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [[ "$JAVA_VER" -lt 21 ]]; then
        error "Java 21+ required (found Java $JAVA_VER)"
        exit 1
    fi
    success "Java $JAVA_VER found"
fi

# ── 2. Download OTel Java agent ───────────────────────────────────────────────
header "OTel Java Agent (v${OTEL_AGENT_VERSION})"

if [[ -f "$AGENTS_DIR/$OTEL_AGENT_JAR" ]]; then
    success "Agent already present at demo/agents/$OTEL_AGENT_JAR"
else
    info "Downloading from GitHub releases..."
    curl -fsSL -o "$AGENTS_DIR/$OTEL_AGENT_JAR" "$OTEL_AGENT_URL"
    success "Downloaded $OTEL_AGENT_JAR"
fi

# ── 3. Build WAR ──────────────────────────────────────────────────────────────
if ! $SKIP_BUILD; then
    header "Building PowerAuth Server WAR"
    info "Running: mvn package -DskipTests -pl powerauth-java-server (from repo root)"
    cd "$REPO_ROOT"
    mvn package -DskipTests -pl powerauth-java-server -am -q
    success "Build complete"

    mkdir -p "$WAR_DEST"
    cp "$REPO_ROOT"/powerauth-java-server/target/powerauth-java-server*.war "$WAR_DEST/powerauth-java-server.war"
    success "WAR copied to $WAR_DEST"
else
    warn "--skip-build: skipping Maven build"
    if [[ ! -f "$WAR_DEST/powerauth-java-server.war" ]]; then
        error "WAR not found at $WAR_DEST/powerauth-java-server.war — run without --skip-build first"
        exit 1
    fi
fi

# ── 4. Start Docker Compose stack ─────────────────────────────────────────────
header "Starting demo stack"
cd "$SCRIPT_DIR"
docker compose up -d --build
success "All containers started"

# ── 4a. Initialize database schema ────────────────────────────────────────────
header "Initializing database schema"
info "Waiting for PostgreSQL to be ready..."
until docker compose exec -T postgres pg_isready -U powerauth -q; do
    echo -n "."
    sleep 2
done
echo ""

# Only run schema init if tables don't exist yet
TABLE_COUNT=$(docker compose exec -T postgres psql -U powerauth -d powerauth -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='pa_application'" 2>/dev/null || echo "0")
if [[ "$TABLE_COUNT" == "0" ]]; then
    docker compose exec -T postgres psql -U powerauth -d powerauth \
        < "$REPO_ROOT/docs/sql/postgresql/create_schema.sql" > /dev/null 2>&1
    success "Schema created"
else
    success "Schema already initialized — skipping"
fi

# ── 5. Wait for PowerAuth Server ──────────────────────────────────────────────
header "Waiting for PowerAuth Server to be ready"
info "Health endpoint: $PAS_BASE_URL/actuator/health"

MAX_WAIT=120
ELAPSED=0
until curl -sf "$PAS_BASE_URL/actuator/health" | grep -q '"status":"UP"'; do
    if [[ $ELAPSED -ge $MAX_WAIT ]]; then
        error "PowerAuth Server did not become healthy within ${MAX_WAIT}s"
        echo "Check logs with: docker compose logs powerauth-server"
        exit 1
    fi
    echo -n "."
    sleep 3
    ELAPSED=$((ELAPSED + 3))
done
echo ""
success "PowerAuth Server is UP"

# ── 6. API Calls ──────────────────────────────────────────────────────────────
header "Running API demo (X-Correlation-ID: $CORRELATION_ID)"

info "Step 1/3 — Create application 'demo-app'"
CREATE_RESPONSE=$(curl -sf -X POST \
    "$PAS_BASE_URL/rest/v4/application/create" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORRELATION_ID" \
    -d '{"requestObject":{"applicationId":"demo-app"}}')
echo "$CREATE_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$CREATE_RESPONSE"
success "Application created"

info "Step 2/3 — Get application detail"
DETAIL_RESPONSE=$(curl -sf -X POST \
    "$PAS_BASE_URL/rest/v4/application/detail" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORRELATION_ID" \
    -d '{"requestObject":{"applicationId":"demo-app"}}')
echo "$DETAIL_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$DETAIL_RESPONSE"
success "Application detail retrieved"

info "Step 3/3 — List all applications"
LIST_RESPONSE=$(curl -sf -X POST \
    "$PAS_BASE_URL/rest/v4/application/list" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORRELATION_ID" \
    -d '{}')
echo "$LIST_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$LIST_RESPONSE"
success "Application list retrieved"

# ── 7. Summary ────────────────────────────────────────────────────────────────
header "Demo complete — Grafana URLs"

echo ""
echo -e "  ${BOLD}Grafana Dashboard${RESET}  →  ${GREEN}http://localhost:3000/d/powerauth-demo${RESET}"
echo ""
echo -e "  ${BOLD}Direct datasource links:${RESET}"
echo -e "    Logs (Loki)    →  http://localhost:3000/explore?schemaVersion=1&panes=%7B%22a%22%3A%7B%22datasource%22%3A%22loki%22%7D%7D"
echo -e "    Traces (Tempo) →  http://localhost:3000/explore?schemaVersion=1&panes=%7B%22a%22%3A%7B%22datasource%22%3A%22tempo%22%7D%7D"
echo -e "    Metrics        →  http://localhost:9090"
echo ""
echo -e "  ${BOLD}Correlation ID used:${RESET} ${CYAN}$CORRELATION_ID${RESET}"
echo -e "  ${BOLD}Search in Loki with:${RESET} {service=\"powerauth-server\"} | json | correlation_id=\"$CORRELATION_ID\""
echo ""
echo -e "  ${BOLD}Stop the stack:${RESET}  ./demo.sh --stop"
echo ""
