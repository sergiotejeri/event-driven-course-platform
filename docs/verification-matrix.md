# Matriz de verificación

| Requisito | Implementación | Evidencia automatizada |
|---|---|---|
| Dominio e invariantes | Invariantes puras en dominio y cambios concurrentes mediante SQL condicional | `DomainInvariantTest`, `EnrollmentLifecycleIntegrationTest` |
| CRUD y búsqueda del catálogo | Servicios de catálogo, archivado, filtros combinables y cursor keyset | `CatalogControllerTest` |
| Paginación y sort seguro | `SortSpec` con lista blanca y desempate | `SortSpecTest`, `RepositoryIntegrationTest` |
| Inscripción idempotente | Clave por actor y operación | `EnrollmentControllerIntegrationTest`, `EnrollmentConcurrencyIntegrationTest` |
| Aforo concurrente | Reserva condicional y constraint SQL | `EnrollmentConcurrencyIntegrationTest`, `MigrationIntegrationTest` |
| Atomicidad inscripción-outbox | Una transacción Spring | `OutboxTransactionalIntegrationTest` |
| Publicación fiable | Lease, publisher confirm y reprogramación | `PublishOutboxBatchUseCaseTest`, `OutboxPublisherIntegrationTest` |
| Pagos asíncronos | Eventos de creación, simulación y resultado | `PaymentMessagingIntegrationTest` |
| Retry, duplicados y DLQ | Spring Retry, DLX/DLQ y `processed_events` | `PaymentMessagingIntegrationTest` |
| Progreso y cancelación | Actualizaciones condicionales | `EnrollmentLifecycleIntegrationTest` |
| Certificado por evento | Consumidor idempotente y unicidad SQL | `CertificateMessagingIntegrationTest` |
| JWT, roles y ownership | Spring Security y `AuthorizationService` | `SecurityIntegrationTest`, `OwnershipIntegrationTest` |
| Flyway y restricciones | Migraciones V1-V4 y `ddl-auto=validate` | `MigrationIntegrationTest` |
| Relaciones sin N+1 | Proyecciones JDBC paginadas | `RepositoryIntegrationTest` |
| Errores consistentes | `ProblemDetail` y correlation ID, también en seguridad y rate limiting | `ApiErrorContractTest`, `SecurityIntegrationTest`, `RateLimitFilterTest` |
| OpenAPI integrable | Propósito, parámetros, seguridad y respuestas | `OpenApiMcpIntegrationTest` |
| Redis y rate limiting | Cache-aside y token bucket Lua | `RedisFeaturesIntegrationTest`, `RateLimitFilterTest` |
| MCP real | 29 tools autenticadas sin identidad suplantable | `McpAuthorizationIntegrationTest`, `SecurityIntegrationTest` |
| Observabilidad | Health, métricas, correlación, trazas y dashboard Grafana provisionado | `ObservabilityIntegrationTest`, `observability/grafana/provisioning/dashboards` |
| Límites arquitectónicos | Reglas ArchUnit | `LayerBoundaryTest` |
| Sistema desplegado | Compose de siete servicios | `scripts/smoke-test.ps1`, `scripts/smoke-test.sh` |
