# Clipping Growth OS repository guidance

## Product and current scope

Clipping Growth OS will eventually turn long-form creator content into short-form clips through transcription, moment detection, clip generation, subtitles, variants, human review, and export. The MVP stops at human review and export; automatic publishing, analytics, experiments, and multi-account SaaS capabilities are later work.

Build incrementally. Implement only the explicitly requested stage and do not scaffold future features. The current repository contains the Stage 0 foundation and PostgreSQL persistence infrastructure, but no business/domain model.

## Architectural boundaries

- Use a monorepo with a modular-monolith Java backend and a separate media/AI worker only when that worker is requested.
- Preserve the communication topology Frontend -> Spring Boot -> Media Worker. The browser must never call the worker directly; Spring Boot is the public API boundary and orchestration layer.
- Java owns domain state, orchestration, persistence, and processing-job lifecycle.
- A future Python worker owns media and AI processing, not business state. Communicate through explicit service contracts; never launch Python from Spring with `Runtime.exec` or an equivalent.
- PostgreSQL will be the source of truth for structured data. Never store media binaries in PostgreSQL.
- Put media storage behind an abstraction rather than coupling domain logic to S3.
- Treat long-running media work as asynchronous and preserve complete artifact lineage.
- Do not introduce microservices, Kafka, RabbitMQ, Redis, Kubernetes, authentication, Lombok, or MapStruct without an explicit requirement.

## Engineering rules

- Prefer simple, explicit, readable code and concrete dependencies over speculative abstractions.
- Keep modules loosely coupled and do not silently change architecture or product scope.
- Do not add future-stage domain entities or placeholder directories.
- Never commit secrets. Add `.env.example` when environment variables are introduced.
- Add meaningful tests for behavior; do not chase arbitrary coverage.
- Every change must compile, pass relevant tests, and package successfully.
- Review the final diff for accidental or unnecessary changes.
- Do not create commits unless the user explicitly asks.

## Backend

- Location: `backend/`
- Package root: `com.clippinggrowth`
- Runtime: Java 25
- Framework: Spring Boot 4.1 with Maven
- Use the Maven Wrapper (`mvnw` or `mvnw.cmd`) for commands.
- Run tests from `backend/` with `./mvnw test` (or `.\mvnw.cmd test` on Windows).
- Package with `./mvnw package` (or `.\mvnw.cmd package` on Windows).

## Persistence foundation

- PostgreSQL 18 is the target database.
- Runtime connection settings come from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` through Spring Boot configuration.
- Flyway exclusively owns schema evolution; Hibernate schema generation must remain disabled.
- Do not create JPA entities or repositories until an explicit domain stage requires them.
- Persistence integration tests use a PostgreSQL 18 Testcontainer and must not fall back to H2, mocks, or a locally installed database.
- The root `compose.yaml` provides PostgreSQL 18 only for local development; run the backend directly from Maven or the IDE unless containerization is explicitly requested.
