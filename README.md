# Clipping Growth OS

Clipping Growth OS is an incremental, production-oriented platform for turning long-form creator content into reviewable short-form clips.

The repository currently contains the Stage 0 repository foundation and PostgreSQL persistence infrastructure. Product domain features, media processing, frontend code, distribution, and analytics are intentionally out of scope at this stage.

## Repository structure

```text
clipping-growth-os/
└── backend/    Java/Spring Boot application
```

## Backend prerequisites

- Java 25
- PostgreSQL 18 for normal application startup
- A Docker-compatible container runtime for integration tests
- No system Maven installation is required; use the included Maven Wrapper.

## Database configuration

The backend reads its PostgreSQL connection from Spring Boot's normal environment-backed configuration:

- `DB_URL` (local default: `jdbc:postgresql://localhost:5432/clipping_growth`)
- `DB_USERNAME` (local default: `clipping_growth`)
- `DB_PASSWORD` (local default: `clipping_growth`)

The root `.env.example` documents these values. Spring Boot does not load that file automatically; export the variables in your shell or configure them through your development environment.

Flyway owns schema evolution. Hibernate schema generation is disabled because the project does not have mapped domain entities yet.

## Build and test

From `backend/`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw` instead.

The tests start an isolated PostgreSQL 18 container and do not use a developer-installed PostgreSQL instance.

## Run locally

From `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

The configured PostgreSQL database must be available before starting the application. Docker Compose is intentionally not part of this stage.

The standard Actuator health endpoint is then available at:

```text
GET http://localhost:8080/actuator/health
```

No environment variables or external services are required yet.
