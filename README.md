# Clipping Growth OS

Clipping Growth OS is an incremental, production-oriented platform for turning long-form creator content into reviewable short-form clips.

The repository currently contains the Stage 0 repository foundation, PostgreSQL persistence infrastructure, a local PostgreSQL development environment, and the independent media worker foundation. Product domain features, media processing, frontend code, distribution, and analytics are intentionally out of scope at this stage.

## Repository structure

```text
clipping-growth-os/
├── backend/                Java/Spring Boot application
├── workers/media-worker/   Python/FastAPI processing service
└── compose.yaml            Local PostgreSQL infrastructure
```

## Local development

Prerequisites:

- Java 25
- Docker Desktop
- No system Maven installation is required; use the included Maven Wrapper.

From the repository root, start PostgreSQL and then run the backend directly from Maven:

```powershell
docker compose up -d
cd backend
.\mvnw.cmd spring-boot:run
```

The Compose service exposes PostgreSQL only on `localhost:5432`. Once Spring Boot starts, verify it at:

```text
GET http://localhost:8080/actuator/health
```

Stop the local database without deleting its data:

```powershell
docker compose down
```

To intentionally reset the local database and delete the named volume, run:

```powershell
docker compose down -v
```

## Database configuration

The backend reads its PostgreSQL connection from Spring Boot's normal environment-backed configuration:

- `DB_URL` (local default: `jdbc:postgresql://localhost:5432/clipping_growth`)
- `DB_USERNAME` (local default: `clipping_growth`)
- `DB_PASSWORD` (local default: `clipping_growth`)

The root `.env.example` documents these values. Spring Boot does not load that file automatically; export the variables in your shell or configure them through your development environment.

Flyway owns schema evolution. Hibernate schema generation is disabled because the project does not have mapped domain entities yet.

The Compose PostgreSQL data is stored in a named volume, so it survives container restarts and recreation unless the volume is explicitly removed.

## Media worker

Prerequisites:

- Python 3.14
- `uv`

From `workers/media-worker/`, install the locked dependencies and start the development server on a port separate from Spring Boot:

```powershell
uv sync
uv run uvicorn app.main:app --reload --port 8001
```

Verify the worker at:

```text
GET http://localhost:8001/health
```

Run the worker tests with:

```powershell
uv run pytest
```

## Build and test

From `backend/`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw` instead.

The tests start an isolated PostgreSQL 18 container and do not use a developer-installed PostgreSQL instance.
