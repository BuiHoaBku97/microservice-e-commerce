# E-commerce microservices

The local stack contains PostgreSQL, Kafka, Redis, Elasticsearch, Eureka, Gateway, Identity,
Catalog, Order, Payment, and Notification. Public APIs are exposed through
`http://localhost:8080/api/v1`; internal APIs are not routed through Gateway.

## Prerequisites

- JDK 21 for local Gradle tasks.
- Docker Desktop with Linux containers and Docker Compose.
- PowerShell 7 for the setup and smoke scripts.

## First run

1. Generate local keys and the callback secret:

   ```powershell
   powershell -File scripts/generate-dev-env.ps1
   ```

2. Edit `.env` and replace `ADMIN_PASSWORD` before using the stack outside an isolated local machine.
3. Validate and build the stack:

   ```powershell
   docker compose config --quiet
   docker compose build
   docker compose up -d --wait
   docker compose ps
   ```

4. Run the end-to-end smoke flow:

   ```powershell
   powershell -File tests/smoke/compose-smoke.ps1 `
     -AdminEmail admin@example.test -AdminPassword '<password-from-.env>'
   ```

5. Stop the stack with `docker compose down`. Add `-v` only when a full local data reset is intended.

The smoke flow registers a customer, creates an address and catalog data, initializes and receives
stock, checks out, simulates payment, and verifies order, notification, and inventory results.

## Verification

Run unit and component tests:

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
```

Run Testcontainers integration tests while Docker is available:

```powershell
.\gradlew.bat :event-flow-tests:integrationTest --rerun-tasks --no-daemon
```

Validate the generated OpenAPI file and its controller coverage:

```powershell
python tools/generate_openapi.py
python tools/check_openapi_coverage.py
```

Generate and run the local Postman acceptance data:

```powershell
python tools/generate_postman.py
```

Import `docs/e-commerce.postman_collection.json` and
`docs/e-commerce.postman_environment.json` into Postman, select the `E-commerce local`
environment, then run the collection from the Collection Runner. Set `adminEmail` and
`adminPassword` in the environment to the values from `.env` when they differ from the
local defaults. The collection creates unique customer, catalog, inventory, cart, order,
and payment data and carries response IDs and versions into later requests automatically.

Default host ports are Gateway `8080`, Eureka `8761`, PostgreSQL `5433`, Kafka `9092`, Redis
`6379`, and Elasticsearch `9200`. Application service ports remain inside the Compose network.

## Observability

The Compose stack also starts Prometheus, cAdvisor, Grafana, Loki, Grafana Alloy,
OpenTelemetry Collector, and Jaeger. Open the Grafana dashboard at `http://localhost:3000`
(default credentials are `admin`/`admin`), Prometheus at `http://localhost:9090`, and Jaeger at
`http://localhost:16686`. Spring services expose `/actuator/prometheus` internally; CPU and
memory panels come from cAdvisor. Application logs are collected from Docker stdout by Alloy
and stored in Loki. Traces are exported over OTLP to the Collector and then Jaeger.

Promtail is not used because it reached end of life on 2 March 2026; Alloy is its supported
replacement. `orderId` and `correlationId` remain the business/log correlation fields. No
`trace_id` database column is required.
