# System design & trade-offs

**Layers (MVC):** `controller` (HTTP only) → `service` (orchestration, transactions) → `repository` (JPA) / `soap` (anti-corruption client). `dto` isolates the API from entities; `web` holds the filter and global error handler.

| Concern | Decision | Why / trade-off |
|---|---|---|
| SOAP integration | Hand-built SOAP 1.2 envelope via `RestClient` + hardened DOM parser | No WSDL codegen/plugin, tiny footprint, easy to test. Trade-off: no compile-time contract; if the WSDL grows, switch to Spring-WS + JAXB. |
| Stateless | No session/state in pods; all state in MySQL | Any pod serves any request → horizontal scaling (HPA 3–10 replicas), rolling deploys with zero downtime. |
| Caching | Caffeine (per-pod, 24h) on ISO code and full info | Country data is near-static, so repeat lookups never hit the slow upstream. Trade-off: per-pod cache; use Redis if a shared cache is needed. |
| Timeouts | 3s connect / 10s read on SOAP, 5s Hikari connection timeout | Bounded latency; threads never hang on a dead upstream. |
| Retry | 3 attempts, exponential backoff (500ms ×2) on upstream failures only | Absorbs transient faults; "country not found" is never retried. |
| Circuit breaker | Opens at 50% failures over 20 calls, 30s open, auto half-open | Fails fast and protects the upstream and our threads; returns 503 + `Retry-After`. |
| Fallback | If upstream down and the country is already stored → return stored copy | Graceful degradation instead of an error. |
| DB transactions | SOAP calls run *outside* transactions | A slow upstream never pins a DB connection. |
| Idempotency | Upsert by unique `iso_code` | Repeated POSTs are safe; concurrent duplicates → 409 via unique constraint. |
| High load | Pagination (max 100), batch fetching (no N+1), pool size configurable, graceful shutdown | |
| Queuing | Not used: lookups are synchronous & cheap. For bulk ingestion add Kafka/RabbitMQ and consume asynchronously. | Avoids unneeded complexity. |
| Observability | JSON (ECS) logs with `correlationId` (MDC, echoed in `X-Correlation-Id`), Micrometer → `/actuator/prometheus` (HTTP, JVM, Hikari, Resilience4j, cache metrics) | Trace one request across pods/log lines. |
| Security | Non-root container, dropped capabilities, secrets via K8s Secret, input validation, XXE-safe XML parsing | Demo secret is a placeholder: use Vault/Sealed Secrets in prod. |
| Schema | `ddl-auto=update` for the exercise | Production: Flyway migrations + `validate`. |
| DB | In-cluster MySQL StatefulSet for demo | Production: managed MySQL with HA + backups. |
