# PowerAuth Observability Demo

A self-contained demo that shows **structured logging, distributed tracing, and metrics** for PowerAuth Server using the Grafana LGTM stack.

> This demo implements all observability improvements from the [Service Observability roadmap](https://github.com/wultra/tasklist/issues/862) through the application create/get code path as a concrete, runnable example.

---

## What the demo shows

| Layer | What you see |
|-------|-------------|
| **Structured logs** (L2–L4) | JSON log events with `appname`, `trace_id`, `span_id`, `correlation_id` as top-level fields in Loki |
| **Correlation header** (L5) | `X-Correlation-ID` header propagated into MDC and visible in every log line |
| **Structured arguments** (L6) | `applicationId`, `count` etc. as searchable JSON fields — not embedded in the message string |
| **action/state pattern** (L7) | `action=createApplication, state=initiated/succeeded` in every service log entry |
| **HTTP access log** (L-HTTP) | Every request logged with `method`, `path`, `status`, `duration_ms` as JSON fields |
| **Database query log** (L-DB) | Hibernate SQL queries visible in logs (debug level — demo only) |
| **Distributed traces** | OTel Java agent (v2.26.1) auto-instruments the JVM; traces viewable in Tempo with log correlation |
| **Metrics** | JVM, HikariCP, and HTTP metrics scraped by Prometheus and visualised in Grafana |

---

## Prerequisites

| Tool | Version |
|------|---------|
| Docker + Docker Compose | 24+ |
| Java | 21+ |
| Maven | 3.9+ |
| curl | any |

---

## Quick start

```bash
# From the demo/ directory:
./demo.sh
```

The script will:
1. Check prerequisites
2. Download the OTel Java agent JAR into `demo/agents/`
3. Build the PowerAuth Server WAR (`mvn package -DskipTests`)
4. Start the full stack via Docker Compose
5. Wait for the server to become healthy
6. Run 3 API calls (create app → get detail → list apps) with a `X-Correlation-ID` header
7. Print direct Grafana URLs

To stop and clean up:

```bash
./demo.sh --stop
```

To restart without rebuilding:

```bash
./demo.sh --skip-build
```

---

## Architecture

```
  curl (demo.sh)
       │  X-Correlation-ID header
       ▼
┌─────────────────────────────────┐
│   powerauth-java-server:8080    │
│                                 │
│  OTel Java Agent (javaagent)    │──── OTLP traces ──▶  Tempo:4318
│  LogstashEncoder (JSON logs)    │──── Docker logs ──▶  Promtail ──▶ Loki
│  /actuator/prometheus           │◀─── scrape ───────   Prometheus
└─────────────────────────────────┘
         │
         ▼
   postgres:5432 (PostgreSQL 18)

Grafana:3000  ◀──  Loki + Tempo + Prometheus
```

---

## Grafana walkthrough

Open **http://localhost:3000/d/powerauth-demo** after `./demo.sh` completes.

### Panel: Structured Logs
- Query: `{service="powerauth-server"}`
- Expand any log line → every field is a first-class JSON key:
  - `action`, `state`, `applicationId` — structured arguments (L6/L7)
  - `trace_id`, `span_id` — OTel context injected via MDC (L4)
  - `correlation_id` — propagated from the `X-Correlation-ID` request header (L5)
  - `appname` — from `spring.application.name` via `<springProperty>` (L3)
- Click **Tempo** link on `trace_id` to jump directly to the trace

### Panel: Trace Search
- Query: `{resource.service.name="powerauth-java-server"}`
- Select any trace → see the full span tree for a single request
- Click **Logs** on any span to correlate back to Loki

### Panel: HTTP Request Rate
- Metric: `rate(http_server_requests_seconds_count[1m])`
- Shows per-endpoint request rate broken down by `method`, `uri`, `status`

### Panel: HTTP Request Duration (p99)
- Metric: `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))`

### Panel: JVM Heap Used
- Metric: `jvm_memory_used_bytes{area="heap"}`

### Panel: DB Connection Pool
- Metric: `hikaricp_connections_active` / `hikaricp_connections_idle`

---

## Searching by Correlation ID

Every API call in `demo.sh` sends the same `X-Correlation-ID` header. To find all logs and traces for a single demo run:

**Loki:**
```logql
{service="powerauth-server"} | json | correlation_id="<your-correlation-id>"
```

**Tempo TraceQL:**
```
{span.http.request.header.x-correlation-id="<your-correlation-id>"}
```

The correlation ID is printed at the end of `demo.sh` output.

---

## Observability improvements implemented

This demo is a concrete implementation of the following items from the observability roadmap:

| ID | Description | File |
|----|-------------|------|
| L2 | JSON structured logging for all modules | `powerauth-admin/src/main/resources/logback-spring.xml` |
| L3 | Dynamic `appname` from `spring.application.name` | `pas-logback.xml` |
| L4 | `trace_id`, `span_id`, `correlation_id` MDC field mappings | `pas-logback.xml` |
| L5 | Correlation header enabled and propagated | `application.properties` |
| L6 | `StructuredArguments.kv()` for structured log fields | `ApplicationController` v4, `ApplicationServiceBehavior`, `ApplicationDetailServiceBehavior` v4 |
| L7 | `action`/`state` log pattern | `ApplicationServiceBehavior` |
| L8 | Fixed `logger.warn(ex.getMessage(), ex)` anti-pattern | `ApplicationServiceBehavior`, `ApplicationDetailServiceBehavior` v4 |
| L-HTTP | HTTP access logging filter (`method`, `path`, `status`, `duration_ms`) | `RequestLoggingFilter` |
| L-DB | Hibernate SQL query logging (demo-only, not for production) | `application.properties` |

Tracing is provided by the **OTel Java agent** (v2.26.1) — zero code changes, production-grade auto-instrumentation.

---

## Notes

- **L-DB (Hibernate SQL logging)** is intentionally verbose and is included for demonstration purposes only. It should not be enabled in production.
- The OTel Java agent JAR is excluded from version control (`.gitignore`) and downloaded on first run.
- Security (`powerauth.service.restrictAccess`) is disabled for the demo — do not use this configuration in production.
