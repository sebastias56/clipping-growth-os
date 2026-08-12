# Clipping Growth OS

Clipping Growth OS is an incremental, production-oriented platform for turning long-form creator content into reviewable short-form clips.

The repository currently contains the Stage 0 foundation and the Stage 0.9 cross-service status contract connecting the frontend, backend, and media worker. Product domain features, media processing, distribution, and analytics are intentionally out of scope at this stage.

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
- A Node.js version supported by `frontend/package.json` and npm
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
Backend infrastructure:    http://localhost:8080/actuator/health
Backend application status: http://localhost:8080/api/system/status
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
uv sync --locked
uv run --locked uvicorn app.main:app --reload --port 8001
```

Verify the worker at:

```text
GET http://localhost:8001/health
```

Run the worker tests with:

```powershell
uv run --locked pytest
```

## Frontend

Prerequisites:

- A Node.js version supported by `frontend/package.json`
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

Run the frontend checks with:

```powershell
npm run typecheck
npm test
npm run build
```

## Build and test

From `backend/`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw` instead.

The tests start an isolated PostgreSQL 18 container and do not use a developer-installed PostgreSQL instance.
