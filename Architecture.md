# Architecture

## Overview

Task Tracker is a stateless Java 21 REST microservice backed by a PostgreSQL database. It exposes two endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/tasks/{taskId}/durations` | Record a task duration |
| `GET`  | `/api/v1/tasks/{taskId}/average`   | Retrieve the running average |

---

## Design Patterns

### 1. Layered Architecture (Controller → Service → Repository)

Each layer has a single responsibility and communicates only with the adjacent layer below it. Controllers handle HTTP concerns, Services hold business rules, and Repositories abstract data access. This separation makes each layer independently testable and replaceable.

### 2. Repository Pattern (Spring Data JPA)

All database interaction is routed through `TaskStatisticsRepository`. The rest of the application is unaware of SQL or JDBC. If the storage engine ever changes (e.g., migrating to a time-series database), only the repository layer needs to change.

### 3. DTO Pattern (Java Records)

`TaskDurationRequest` and `TaskAverageResponse` are immutable Java records. They decouple the external API contract from the internal JPA entity, allowing each to evolve independently. Bean Validation annotations live on the DTOs, keeping entity classes clean.

### 4. Welford's Online Algorithm

The central challenge is computing a running mean without storing every recorded duration. Welford's one-pass formula is used:

```
new_mean = old_mean + (new_value - old_mean) / new_count
```

This gives an exact arithmetic mean with O(1) memory per task, O(1) time per update, and is numerically stable even for very large counts.

The algorithm is embedded directly in a single PostgreSQL `INSERT … ON CONFLICT DO UPDATE` statement:

```sql
INSERT INTO task_statistics (task_id, count, mean_duration_ms, created_at, updated_at)
VALUES (:taskId, 1, :durationMs, NOW(), NOW())
ON CONFLICT (task_id) DO UPDATE
SET
    mean_duration_ms = task_statistics.mean_duration_ms +
        (:durationMs - task_statistics.mean_duration_ms) / (task_statistics.count + 1),
    count            = task_statistics.count + 1,
    updated_at       = NOW()
```

#### Why embed the formula in SQL?

An application-level read-modify-write cycle (read current stats → calculate → write back) would require either pessimistic row-level locking or an optimistic-locking retry loop to remain correct under concurrent requests for the same `taskId`. Embedding the formula in the `UPDATE` expression moves the atomicity guarantee to the database engine, which serialises concurrent writes to the same row without any additional coordination in the application. This is both simpler and faster.

### 5. Database Migration with Flyway

Schema changes are tracked as versioned SQL scripts under `src/main/resources/db/migration/`. Flyway runs them automatically on startup. This ensures the schema is always in sync with the application version, enables rollback strategies, and gives a full audit trail of schema evolution.

### 6. Java 21 Virtual Threads

Tomcat's thread executor is replaced with a virtual-thread-per-request executor:

```java
handler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

Each HTTP request runs on a lightweight virtual thread. Because virtual threads are cheap to create and park, the service can handle far more concurrent requests than a bounded platform-thread pool without any manual pool-size tuning. This is especially valuable for a service whose dominant cost is waiting for a database round-trip.

### 7. Structured Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) translates domain exceptions into consistent JSON error responses. The controller layer is kept free of try-catch blocks. Adding a new error case only requires defining an exception class and a single handler method.

---

## Data Model

```
task_statistics
├── task_id          VARCHAR(255)  PK   -- external identifier
├── count            BIGINT             -- number of durations recorded
├── mean_duration_ms DOUBLE PRECISION  -- running arithmetic mean (ms)
├── created_at       TIMESTAMPTZ
└── updated_at       TIMESTAMPTZ
```

Only three values are persisted per task regardless of how many durations are recorded. The database survives process restarts by design; the service is stateless and recovers fully by reconnecting to the existing data.

---

## Non-Functional Properties

| Concern | Approach |
|---------|----------|
| **Persistence** | PostgreSQL with a named Docker volume; data survives container and process restarts |
| **Concurrency** | Atomic SQL upsert serialises concurrent writes to the same task at the database row level |
| **Scalability** | Virtual threads remove platform-thread limits; stateless app tier scales horizontally behind a load balancer |
| **Performance** | Single round-trip per write; index on `task_id` (PK); HikariCP connection pool |
| **Observability** | Spring Actuator health/metrics endpoints; Prometheus metrics exposed at `/actuator/prometheus` |
| **Correctness** | Welford's algorithm is numerically stable; SQL constraints prevent negative counts or means |
| **Testability** | Unit tests mock the repository; integration tests use Testcontainers with a real PostgreSQL instance |

---

## Test Strategy

| Layer | Tool | What is tested |
|-------|------|----------------|
| Controller | `@WebMvcTest` + `MockMvc` | HTTP status codes, request validation, JSON shape |
| Service | JUnit 5 + Mockito | Business logic, exception paths |
| Integration | `@SpringBootTest` + Testcontainers | Full stack against a real database: average correctness, persistence, isolation between tasks |

---

## Possible Future Work

- **Authentication & authorisation** — add an API-key or OAuth2 Bearer token filter to prevent unauthenticated writes.
- **Percentile statistics** — store a compact histogram (e.g., HdrHistogram) alongside the mean to expose p50/p95/p99 without storing raw durations.
- **Soft-delete / task archiving** — allow callers to reset or retire a task's statistics without destroying the row.
- **Horizontal scaling** — the service is already stateless; add a Kubernetes `HorizontalPodAutoscaler` backed on CPU or custom Prometheus metrics.
- **Read replica** — route `GET /average` reads to a PostgreSQL read replica to offload the primary.
- **Async writes** — for extremely high write throughput, buffer duration events in Kafka and apply the upsert in a consumer, accepting a short eventual-consistency window on reads in exchange for write throughput.
- **Rate limiting** — add a gateway-level or filter-level rate limiter to protect the database from write storms.
- **OpenAPI documentation** — add `springdoc-openapi` to auto-generate a Swagger UI from the controller annotations.
