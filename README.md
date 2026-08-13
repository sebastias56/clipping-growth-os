# Clipping Growth OS

Clipping Growth OS is an incremental, production-oriented platform for turning long-form creator content into reviewable short-form clips.

The repository contains the technical foundation plus the first product vertical slices: a React frontend, a Spring Boot application boundary, PostgreSQL persistence for Creators and their SourceVideos, a FastAPI media worker, the cross-service status contract, locked quality tooling, and CI. Creators can be created, listed, and retrieved individually. The backend can also create SourceVideos under a Creator, list them with pagination, and retrieve them individually; the SourceVideo frontend workspace, media processing, distribution, and analytics are intentionally absent.

## Repository structure

```text
clipping-growth-os/
├── backend/                Java/Spring Boot application
├── frontend/               React/TypeScript application
├── workers/media-worker/   Python/FastAPI processing service
└── compose.yaml            Local PostgreSQL infrastructure
```

## Local development

Prerequisites:

- Java 25
- Docker Desktop
- Python 3.14 and `uv`
- Node.js 24 (the CI reference runtime; `frontend/package.json` defines the precise supported range) and npm
- No system Maven installation is required; use the included Maven Wrapper.

Start the four local services in this order, using a separate terminal for each long-running process.

1. From the repository root, start PostgreSQL:

```powershell
docker compose up -d
```

2. Start the media worker on port 8001:

```powershell
cd workers/media-worker
uv sync --locked
uv run --locked uvicorn app.main:app --reload --port 8001
```

3. Start the backend on port 8080:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

4. Start the frontend on port 5173:

```powershell
cd frontend
npm ci
npm run dev
```

The local endpoints are:

```text
Frontend:                   http://localhost:5173/
Frontend creators:          http://localhost:5173/creators
Backend infrastructure:    http://localhost:8080/actuator/health
Backend application status: http://localhost:8080/api/system/status
Backend creators:           http://localhost:8080/api/creators
Backend SourceVideo list:   http://localhost:8080/api/creators/{creatorId}/source-videos
Backend SourceVideo detail: http://localhost:8080/api/source-videos/{sourceVideoId}
Media worker health:       http://localhost:8001/health
```

Application clients communicate with the media worker through Spring Boot. The browser uses the backend's `/api` contract and must not consume worker endpoints directly; Vite proxies `/api` to `http://localhost:8080` during local development.

The Compose service exposes PostgreSQL only on `localhost:5432`.

Stop the local database without deleting its data:

```powershell
docker compose down
```

To intentionally reset the local database and delete the named volume, run:

```powershell
docker compose down -v
```

## Backend configuration

The backend reads its PostgreSQL connection and media-worker location from Spring Boot's normal environment-backed configuration:

- `DB_URL` (local default: `jdbc:postgresql://localhost:5432/clipping_growth`)
- `DB_USERNAME` (local default: `clipping_growth`)
- `DB_PASSWORD` (local default: `clipping_growth`)
- `MEDIA_WORKER_BASE_URL` (local default: `http://localhost:8001`)

The root `.env.example` documents these values. Spring Boot does not load that file automatically; export the variables in your shell or configure them through your development environment. Worker requests use a one-second connection timeout and a two-second read timeout, as configured in `backend/src/main/resources/application.properties`.

Flyway owns schema evolution. Hibernate schema generation remains disabled so database migrations are the exclusive source of schema changes.

The Compose PostgreSQL data is stored in a named volume, so it survives container restarts and recreation unless the volume is explicitly removed.

## Media worker

Prerequisites:

- Python 3.14
- `uv`

From `workers/media-worker/`, install the locked dependencies and start the development server on a port separate from Spring Boot:

```powershell
uv sync --locked
uv run --locked uvicorn app.main:app --reload --port 8001
```

Verify the worker at:

```text
GET http://localhost:8001/health
```

## Frontend

Prerequisites:

- Node.js 24 (the CI reference runtime; `frontend/package.json` defines the precise supported range)
- npm

From `frontend/`, install the locked dependencies and start the Vite development server:

```powershell
npm ci
npm run dev
```

The frontend is available at:

```text
http://localhost:5173/
```

## Quality checks

From `backend/`, the Maven Enforcer validates Java and Maven before the tests run:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

From `workers/media-worker/`:

```powershell
uv sync --locked
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked pytest
```

From `frontend/`:

```powershell
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

On macOS or Linux, use `./mvnw` for the backend commands.

The tests start an isolated PostgreSQL 18 container and do not use a developer-installed PostgreSQL instance.

## Continuous integration

Pull requests targeting `main` and pushes to `main` run the backend, media-worker, and frontend quality gates as independent jobs in `.github/workflows/ci.yml`.
