# Task Tracker

A production-ready Java 21 REST microservice that continuously calculates the running average duration of named tasks. Data is persisted in PostgreSQL and survives service restarts.

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Docker | 20.10+ |
| Docker Compose | v2 (the `docker compose` plugin) |
| Java | 21 (only required to run tests or build locally) |
| Maven | 3.9.14 (only required to build locally) |

---

## Quick Start with Docker Compose

```bash
# 1. Clone the repository


# 2. Build and start both services (app + postgres)
docker compose up --build

# The service is ready when you see:
#   Started TaskTrackerApplication in X seconds
```

The API is available at `http://localhost:8080`.

Stop everything (data is preserved in the named volume):

```bash
docker compose down
```

To also delete all persisted data:

```bash
docker compose down -v
```

---

## API Reference

### Record a task duration

```
POST /api/v1/tasks/{taskId}/durations
Content-Type: application/json

{ "durationMs": 1500 }
```

| Field | Type | Description |
|-------|------|-------------|
| `durationMs` | `Long` | Duration in milliseconds, must be ≥ 0 |

**Responses**

| Status | Meaning |
|--------|---------|
| 200 OK | Duration recorded successfully |
| 400 Bad Request | Validation error (e.g. negative duration) |
| 500 Internal Server Error | Unexpected server error |

**Example**

```bash
curl -X POST http://localhost:8080/api/v1/tasks/image-resize/durations \
     -H "Content-Type: application/json" \
     -d '{"durationMs": 1500}'
```

---

### Get current average

```
GET /api/v1/tasks/{taskId}/average
```

**Responses**

| Status | Meaning |
|--------|---------|
| 200 OK | Returns the task ID and current average |
| 404 Not Found | No durations have been recorded for this task |

**Response body**

```json
{
  "taskId": "image-resize",
  "averageDurationMs": 1500.0
}
```

**Example**

```bash
curl http://localhost:8080/api/v1/tasks/image-resize/average
```

---

## End-to-End Example

```bash
# Record three durations
curl -s -X POST http://localhost:8080/api/v1/tasks/etl-job/durations \
     -H "Content-Type: application/json" -d '{"durationMs": 100}'

curl -s -X POST http://localhost:8080/api/v1/tasks/etl-job/durations \
     -H "Content-Type: application/json" -d '{"durationMs": 200}'

curl -s -X POST http://localhost:8080/api/v1/tasks/etl-job/durations \
     -H "Content-Type: application/json" -d '{"durationMs": 300}'

# Retrieve the average → 200.0
curl -s http://localhost:8080/api/v1/tasks/etl-job/average | jq .
```

Expected output:

```json
{
  "taskId": "etl-job",
  "averageDurationMs": 200.0
}
```

**Restart the service and verify persistence:**

```bash
docker compose restart app

# Wait ~15s for the service to come back up, then:
curl -s http://localhost:8080/api/v1/tasks/etl-job/average | jq .
# Still returns 200.0
```

---

## Building and Testing Locally

```bash
# Run unit and integration tests (requires Docker for Testcontainers)
mvn verify

# Build the JAR without running tests
mvn package -DskipTests

# Run locally (requires a running PostgreSQL instance)
DB_HOST=localhost DB_USERNAME=tasktracker DB_PASSWORD=tasktracker \
  java -jar target/task-tracker-1.0.0.jar
```

---

## Observability

Spring Boot Actuator endpoints are enabled:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Service and database health |
| `GET /actuator/metrics` | All available metrics |
| `GET /actuator/prometheus` | Prometheus-format metrics scrape endpoint |

```bash
curl http://localhost:8080/actuator/health | jq .
```

---

## Configuration

All settings can be overridden via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `tasktracker` | Database name |
| `DB_USERNAME` | `tasktracker` | Database user |
| `DB_PASSWORD` | `tasktracker` | Database password |
| `SERVER_PORT` | `8080` | HTTP port |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/coolplanet/tasktracker/
│   │   ├── TaskTrackerApplication.java
│   │   ├── config/          # Virtual thread configuration
│   │   ├── controller/      # REST endpoints
│   │   ├── dto/             # Request/response records
│   │   ├── exception/       # Exception classes and global handler
│   │   ├── model/           # JPA entity
│   │   ├── repository/      # Data access (atomic SQL upsert)
│   │   └── service/         # Business logic
│   └── resources/
│       ├── application.yml
│       └── db/migration/    # Flyway SQL scripts
└── test/
    └── java/com/coolplanet/tasktracker/
        ├── controller/      # @WebMvcTest slice tests
        ├── service/         # Unit tests (Mockito)
        └── integration/     # Full-stack tests (Testcontainers)
```

See [Architecture.md](Architecture.md) for design decisions and trade-offs.
