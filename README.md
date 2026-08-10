# Clipping Growth OS

Clipping Growth OS is an incremental, production-oriented platform for turning long-form creator content into reviewable short-form clips.

The repository currently contains the Stage 0 repository foundation and a Spring Boot backend skeleton. Media processing, persistence, domain features, frontend code, infrastructure, and distribution are intentionally out of scope at this stage.

## Repository structure

```text
clipping-growth-os/
└── backend/    Java/Spring Boot application
```

## Backend prerequisites

- Java 25
- No system Maven installation is required; use the included Maven Wrapper.

## Build and test

From `backend/`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw` instead.

## Run locally

From `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

The standard Actuator health endpoint is then available at:

```text
GET http://localhost:8080/actuator/health
```

No environment variables or external services are required yet.
