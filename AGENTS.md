# AI Agent Guide - Project Tanuki Backend

This document serves as the single source of truth and architectural reference for AI agents (and engineers) working on,
modifying, optimizing, or extending the **Project Tanuki Backend** repository.

---

## 1. System & Architecture Overview

**Project Tanuki** is a backend platform for tracking Japanese temple seals (*goshuins*), temple records, user profiles,
and map explorations with automated asynchronous data enrichment (geocoding, translation, AI descriptions).

### Architecture Paradigm

- **Monorepo Layout**: Multi-module Maven structure for Java services and shared libraries, combined with a Python
  background worker service and a map tile server container.
- **Service Isolation**: Microservices operate independently with isolated PostgreSQL database schemas (`auth_schema`,
  `profile_schema`, `goshuin_schema`).
- **Contract-First (OpenAPI)**: All REST endpoints are defined in `openapi.yaml` within each service. Controllers
  implement generated Spring interfaces and consume generated DTOs.
- **Centralized Schema Migrations**: All Liquibase migration changelogs are managed in `libs/liquibase`.
- **Decoupled Asynchronous Processing**: Background data enrichment uses GCP Pub/Sub messaging and a specialized Python
  worker.

### Technology Stack Matrix

| Component / Layer        | Technology                            | Key Details & Version                                                 |
|:-------------------------|:--------------------------------------|:----------------------------------------------------------------------|
| **Java Platform**        | Java 25 / Eclipse Temurin             | Modern Java features (records, pattern matching, sealed types)        |
| **Framework**            | Spring Boot 4.0.1                     | Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3         |
| **Microservice Client**  | Spring Cloud OpenFeign + Resilience4j | Declarative REST clients with circuit breakers                        |
| **Security / Auth**      | Spring Security + JJWT 0.12.6         | Stateless JWT authentication, Google OAuth2 Client                    |
| **Python Worker**        | Python 3.12 / FastAPI / Pydantic      | Asynchronous event processing, Google Cloud SDKs                      |
| **Database**             | PostgreSQL 16+                        | Schema-per-service isolation, Liquibase migrations                    |
| **Mapping / Tiles**      | TileServer-GL (MapTiler)              | Serves Japan OSM vector map tiles (`.mbtiles`)                        |
| **Cloud Infrastructure** | Google Cloud Platform (GCP)           | Cloud Run, Cloud SQL, Cloud Storage (GCS), Pub/Sub, Artifact Registry |
| **CI / CD**              | GitHub Actions                        | Modular reusable workflows (`.github/workflows/`)                     |

---

## 2. Repository Structure

```
project-tanuki-back/
├── .github/
│   └── workflows/
│       ├── deploy.yml                   # Main orchestrator pipeline (triggers on push/dispatch)
│       ├── _build-push.yml              # Reusable: builds JAR, builds container, pushes to GAR, deploys to Cloud Run
│       ├── _liquibase-build.yml         # Reusable: builds Liquibase container, pushes to GAR, updates Cloud Run Job
│       ├── _liquibase-migrate.yml       # Reusable: runs Cloud Run Job to execute migration against Cloud SQL
│       └── enrichment-worker.yml        # CI/CD pipeline for the Python enrichment worker
├── .run/                                # Shared IntelliJ IDEA Run Configurations
│   ├── ALL_SERVICES.run.xml
│   ├── AuthService.run.xml
│   └── ProfileService.run.xml
├── api-tests/                           # IntelliJ HTTP Client files for manual/integration testing
│   ├── auth.http                        # Auth Service endpoints testing
│   ├── profile.http                     # Profile Service endpoints testing
│   ├── goshuin.http                     # Goshuin Service endpoints testing
│   └── temple.http                      # Temple endpoints testing
├── libs/                                # Shared Java libraries & database migrations
│   ├── common/                          # Global exception handler, common DTOs (ErrorResponse, UploadUrlResponse)
│   ├── gcp/                             # GCP Storage configuration & V4 signed URL generators
│   ├── security/                        # JWT authentication filter, JwtUtils, CorsProperties, SharedSecurityConfig
│   └── liquibase/                       # Centralized database changelogs, test SQL datasets, migration Dockerfile
├── services/                            # Core microservices & workers
│   ├── auth-service/                    # Port 8081: User authentication, registration, OAuth2, JWT tokens
│   ├── profile-service/                 # Port 8082: User profiles, avatars, GCS upload signed URLs
│   ├── goshuin-service/                 # Port 8083: Goshuins, Temples, i18n variants, comments, search
│   └── enrichment-worker/               # Port 8085 (8080 container): Python FastAPI worker (Pub/Sub consumer)
├── tileserver-data/                     # Port 8084 (8080 container): TileServer-GL configuration and styles
├── compose.yaml                         # Local development Docker Compose (PostgreSQL, TileServer, Worker)
├── Dockerfile                           # Multi-service base Dockerfile for Java Spring Boot services
├── ENRICHMENT.md                        # Enrichment concept, architecture proposal, and data model
├── pom.xml                              # Root parent POM managing dependency versions and plugins
└── README.md                            # General onboarding and setup guide
```

---

## 3. Services Breakdown

### 1. `auth-service`

- **Location**: `services/auth-service`
- **Stack**: Java 25, Spring Boot 4.0.1, Spring Data JPA, Spring Security, Spring Cloud OpenFeign
- **Default Port**: `8081` (`${PORT:8081}`)
- **Database Schema**: `auth_schema`
- **Key Responsibilities**:
    - User registration (`POST /api/v1/auth/register`) and password hashing (BCrypt).
    - User login (`POST /api/v1/auth/login`) issuing JWT access tokens (15m expiry) and refresh tokens (7d expiry).
    - Token refresh (`POST /api/v1/auth/refresh`) and logout token revocation (`POST /api/v1/auth/logout`).
    - Google OAuth2 Client integration (`/oauth2/authorization/google`, `/login/oauth2/code/google`).
    - Authenticated user introspection (`GET /api/v1/auth/me`).
- **Inter-Service Communication**:
    - Calls `profile-service` via OpenFeign `ProfileClient`
      (`services/auth-service/src/main/java/io/github/peterberghuis/auth/client/ProfileClient.java`) to create a profile
      when a new user registers.
    - Uses `ProfileClientFallbackFactory` with Resilience4j circuit breaking.

### 2. `profile-service`

- **Location**: `services/profile-service`
- **Stack**: Java 25, Spring Boot 4.0.1, Spring Data JPA, Spring Security, Google Cloud Storage
- **Default Port**: `8082` (`${PORT:8082}`)
- **Database Schema**: `profile_schema`
- **Key Responsibilities**:
    - User profile management (`GET /api/v1/profiles/me`, `PUT /api/v1/profiles/me`).
    - Public profile retrieval (`GET /api/v1/profiles/{id}`, `GET /api/v1/profiles/batch`).
    - Internal API for profile creation (`POST /internal/v1/profiles`).
    - Avatar management: Generating GCS V4 signed upload URLs (`POST /api/v1/profiles/me/avatar/upload-url`) with
      content-type restrictions (`image/jpeg`, `image/png`, `image/webp`) and size limits (max 5MB).
    - Asynchronous old avatar cleanup in GCS upon avatar updates (`AvatarChangedEvent` -> `AvatarCleanupService`).

### 3. `goshuin-service`

- **Location**: `services/goshuin-service`
- **Stack**: Java 25, Spring Boot 4.0.1, Spring Data JPA, Spring Security, Google Cloud Storage, OpenFeign
- **Default Port**: `8083` (`${PORT:8083}`)
- **Database Schema**: `goshuin_schema`
- **Key Responsibilities**:
    - Goshuin (temple seal) CRUD operations (`/api/v1/goshuins`).
    - Temple record CRUD operations (`/api/v1/temples`).
    - Multi-language localization (i18n): `TempleI18nEntity` and `GoshuinI18nEntity` supporting Japanese, English, and
      other locales.
    - Cursor-based pagination and proximity search (`CreatedAtCursor`, `CommentCountCursor`, `ProximityCursor`).
    - Goshuin image signed upload URLs (`POST /api/v1/goshuins/upload-url`, max 10MB).
    - Temple & Goshuin comments management.
    - Internal enrichment status tracking and callbacks (`EnrichmentController`).
- **Inter-Service Communication**:
    - Calls `profile-service` via OpenFeign to fetch author profile details.
    - Publishes enrichment requests to GCP Pub/Sub topic `enrichment-requests`.

### 4. `enrichment-worker`

- **Location**: `services/enrichment-worker`
- **Stack**: Python 3.12, FastAPI, Pydantic, Uvicorn
- **Default Port**: `8085` locally, `8080` in container (`${PORT:8080}`)
- **Key Responsibilities**:
    - Background worker receiving Pub/Sub push HTTP notifications on `POST /enrich`.
    - Health check endpoint on `GET /health` and `GET /`.
    - Extracts base64-encoded `EnrichmentRequest` (`resourceType`, `resourceId`, `originalLocale`).
    - Performs asynchronous geocoding (Google Maps API), multi-lingual translation (Google Cloud Translation API), and
      AI summary generation (Vertex AI / Gemini).
    - Updates `goshuin-service` via internal API callback upon completion.

### 5. `tileserver-data`

- **Location**: `tileserver-data`
- **Stack**: TileServer-GL (`maptiler/tileserver-gl:latest`)
- **Default Port**: `8084` locally, `8080` in container
- **Key Responsibilities**:
    - Serves vector map tiles (`/data/v3/{z}/{x}/{y}.pbf`), styles, and glyphs for frontend map rendering.
    - Uses `osm-2020-02-10-v3.11_asia_japan.mbtiles` placed in `tileserver-data/data/` (excluded from git due to 2GB
      size).

---

## 4. Shared Libraries (`libs/`)

All Java services depend on shared libraries located in the `libs/` directory.

### 1. `libs/common`

- **Artifact**: `io.github.peterberghuis:common`
- **Package**: `io.github.peterberghuis.common`
- **Contents**:
    - `ErrorResponse.java`: Unified error response DTO containing `timestamp`, `status`, `error`, `message`, `path`, and
      validation `errors`.
    - `UploadUrlResponse.java`: Unified DTO containing `uploadUrl` (GCS signed PUT URL) and `publicUrl` (final public
      URL).
    - `GlobalExceptionHandler.java`: `@RestControllerAdvice` providing uniform HTTP status codes and structured
      `ErrorResponse` payloads for `MethodArgumentNotValidException`, `EntityNotFoundException`,
      `AccessDeniedException`, `IllegalArgumentException`, and unhandled exceptions.

### 2. `libs/security`

- **Artifact**: `io.github.peterberghuis:security`
- **Package**: `io.github.peterberghuis.security`
- **Contents**:
    - `JwtUtils.java`: Cryptographic token parser and validator using JJWT `0.12.6`. Extracts user ID (`UUID`), email,
      and roles (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_SYSTEM`).
    - `JwtAuthenticationFilter.java`: `OncePerRequestFilter` that intercepts incoming requests, reads
      `Authorization: Bearer <token>`, validates the token via `JwtUtils`, and populates
      `SecurityContextHolder.getContext().setAuthentication(...)`.
    - `SharedSecurityConfig.java`: Configures `SecurityFilterChain` with stateless session policy
      (`SessionCreationPolicy.STATELESS`), CORS filter from `CorsProperties`, and registers `JwtAuthenticationFilter`.
    - `CorsProperties.java`: Configuration properties mapped to `app.cors` (`allowed-origins`, `allowed-methods`,
      `allowed-headers`, `allow-credentials`).
    - `SecurityUtils.java`: Static utility to retrieve the current authenticated `UUID` user ID or roles from
      `SecurityContext`.

### 3. `libs/gcp`

- **Artifact**: `io.github.peterberghuis:gcp`
- **Package**: `io.github.peterberghuis.gcp`
- **Contents**:
    - `GoogleCloudStorageConfig.java`: Spring `@Configuration` initializing the GCP `Storage` bean. Supports explicit
      JSON credentials (`gcp.storage.credentials-json`) or default Google application credentials.
    - `GcpStorageProperties.java`: Configuration properties bound to `gcp.storage` (`bucket-name`, `credentials-json`).

### 4. `libs/liquibase`

- **Artifact**: `io.github.peterberghuis:liquibase`
- **Contents**:
    - Contains all database migration changelogs in `src/main/resources/db/changelog/`.
    - Subdirectories per service: `auth-service/`, `profile-service/`, `goshuin-service/`.
    - Standalone Dockerfile (`libs/liquibase/Dockerfile`) built from `liquibase/liquibase:latest` with PostgreSQL CLI
      drivers (`lpm add postgresql --global`).

---

## 5. API-First Development & Code Generation

All Java microservices strictly follow an **API-First (Contract-Driven)** methodology using the
`openapi-generator-maven-plugin`.

### Workflow for Adding or Modifying APIs

1. **Never edit generated source files** in `target/generated-sources/openapi/`. They are generated at build time and
   will be overwritten.
2. **Edit `openapi.yaml`** in the root of the target service (e.g. `services/auth-service/openapi.yaml`).
3. **Regenerate Code**: Run the Maven compile step on the specific module:
   ```powershell
   .\mvnw compile -pl services/auth-service
   ```
4. **Inspect Generated Artifacts**:
    - Interfaces: `io.github.peterberghuis.<service>.api.<Name>Api`
    - DTOs: `io.github.peterberghuis.<service>.dto.<Name>`
5. **Implement in Controller**: Implement the generated interface in the service's `@RestController` class (e.g.
   `AuthController implements AuthControllerApi`).
6. **Shared DTO Mappings**: `openapi-generator-maven-plugin` is pre-configured with `importMappings` and `typeMappings`
   to reuse common DTOs from `libs/common`:
   ```xml
   <importMappings>
       <importMapping>ErrorResponse=io.github.peterberghuis.common.dto.ErrorResponse</importMapping>
       <importMapping>UploadUrlResponse=io.github.peterberghuis.common.dto.UploadUrlResponse</importMapping>
   </importMappings>
   ```

---

## 6. Database Schema & Liquibase Migrations

### Schema Isolation Strategy

- Each service connects to the same PostgreSQL instance (`tanuki-db`), but is isolated in its own dedicated database
  schema:
    - `auth-service` -> `auth_schema`
    - `profile-service` -> `profile_schema`
    - `goshuin-service` -> `goshuin_schema`
    - `public` -> Reserved exclusively for Liquibase tracking (`DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`).
- On connection initialization, services execute `CREATE SCHEMA IF NOT EXISTS <schema_name>;` via HikariCP
  (`connection-init-sql`).

### Changelog Structure & Conventions

Changelogs are stored centrally in `libs/liquibase/src/main/resources/db/changelog/<service-name>/`:

- Master file: `db.changelog-master.yaml` includes sequentially numbered changeset files.
- Each changeset is organized into numbered directories, e.g.:
    - `001-initial-schema/db.changelog-001.yaml`
    - `002-add-goshuin-table/db.changelog-002.yaml`
- **Cross-Schema Foreign Keys**: When referencing tables in another schema, explicitly declare `baseTableSchemaName` and
  `referencedTableSchemaName` in Liquibase:
  ```yaml
  - addForeignKeyConstraint:
      baseTableName: profiles
      baseTableSchemaName: profile_schema
      baseColumnNames: user_id
      referencedTableName: users
      referencedTableSchemaName: auth_schema
      referencedColumnNames: id
      constraintName: fk_profiles_users
  ```

### Local Development vs CI/CD Execution

- **Local (`application-local.yml`)**: `spring.liquibase.enabled: true`. Services apply changelogs automatically on
  startup.
- **Dev / Prod (`application-dev.yml` / `application-prod.yml`)**: `spring.liquibase.enabled: false`. Migrations are
  executed in CI/CD via dedicated Cloud Run jobs before services deploy.

---

## 7. CI/CD & Deployment Workflows

GitHub Actions workflows are located in `.github/workflows/`.

```
                  ┌─────────────────────────────────┐
                  │ deploy.yml (Master / Develop)  │
                  └────────────────┬────────────────┘
                                   │
         ┌─────────────────────────┴─────────────────────────┐
         ▼                                                   ▼
┌───────────────────────────────┐           ┌────────────────────────────────┐
│ _liquibase-build.yml          │           │ _build-push.yml (Parallel)     │
│ - Build Liquibase image       │           │ - Build JAR (mvn package -am)  │
│ - Push to GAR                 │           │ - Build Docker image           │
│ - Update Cloud Run Job        │           │ - Push to GAR                  │
└───────────────┬───────────────┘           │ - Deploy to Cloud Run          │
                │                           └────────────────────────────────┘
                ▼
┌───────────────────────────────┐
│ _liquibase-migrate.yml        │
│ - Execute Cloud Run Job       │
│   for auth, profile, goshuin  │
└───────────────────────────────┘
```

### Workflow Catalog

1. **`deploy.yml`**:
    - Triggered on push to `master` (environment: `prod`) or `develop` (environment: `dev`), or manually via
      `workflow_dispatch`.
    - Coordinates Liquibase build -> Liquibase migration execution -> Service builds & Cloud Run deployments.
2. **`_liquibase-build.yml`**:
    - Uses `libs/liquibase/Dockerfile` to build the migration container.
    - Pushes image tagged with `${{ github.sha }}` and `latest` to GCP Artifact Registry (GAR).
    - Updates Cloud Run Job `liquibase-migration-${{ inputs.environment }}` with retry logic.
3. **`_liquibase-migrate.yml`**:
    - Executes Cloud Run Job `liquibase-migration-${{ inputs.environment }}` per schema (`auth_schema`,
      `profile_schema`, `goshuin_schema`).
    - Runs before application service deployment to ensure zero downtime schema compatibility.
4. **`_build-push.yml`**:
    - Builds service JAR with `./mvnw clean package -pl services/${{ inputs.service_name }} -am`.
    - Uses root `Dockerfile` (`eclipse-temurin:25-jre-jammy`) with
      `--build-arg SERVICE_NAME=${{ inputs.service_name }}`.
    - Pushes image to GAR and deploys service to Cloud Run `tanuki-back-${{ inputs.image_name }}`.
5. **`enrichment-worker.yml`**:
    - Standalone workflow for Python enrichment worker.
    - Builds container from `services/enrichment-worker/Dockerfile`, pushes to GAR, deploys to Cloud Run
      `tanuki-back-enrichment-worker`.

### GCP Environments & Variables

- **Environments**: `dev` and `prod`.
- **Variables**: `GAR_LOCATION`, `GCP_PROJECT_ID`, `GAR_REPOSITORY`.
- **Secrets**: `GCP_SA_KEY` (Google Cloud Service Account credentials JSON).

---

## 8. Local Development & Operational Commands

### 1. Starting Local Infrastructure

Docker Compose manages local PostgreSQL, TileServer-GL, and the Enrichment Worker:

```powershell
docker compose up -d
```

- PostgreSQL: `localhost:5432` (User: `myuser`, Password: `secret`, DB: `tanuki-db`)
- TileServer-GL: `localhost:8084`
- Enrichment Worker: `localhost:8085`

### 2. Environment Variables & Secrets

Create a `.env` file at the root based on local requirements:

```env
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GCP_STORAGE_BUCKET_NAME=your-dev-bucket
GCP_STORAGE_CREDENTIALS_JSON=
JWT_SECRET=test-secret-key-that-needs-to-be-long-enough-to-be-secure-32-chars
```

### 3. Maven Commands (PowerShell & Bash)

Use `mvnw.cmd` (or `.\mvnw`) on Windows, `./mvnw` on Linux/macOS.

- **Full Project Build**:
  ```powershell
  .\mvnw clean install -DskipTests
  ```
- **Compile Single Service & Generate OpenAPI Sources**:
  ```powershell
  .\mvnw compile -pl services/auth-service
  ```
- **Run Specific Microservice**:
  ```powershell
  .\mvnw spring-boot:run -pl services/auth-service
  .\mvnw spring-boot:run -pl services/profile-service
  .\mvnw spring-boot:run -pl services/goshuin-service
  ```
- **Run Tests**:
  ```powershell
  # All tests
  .\mvnw test
  # Single module tests
  .\mvnw test -pl services/auth-service
  # Single test class
  .\mvnw test -pl services/goshuin-service -Dtest=GoshuinServiceTest
  ```

### 4. Running the Python Enrichment Worker

```powershell
cd services/enrichment-worker
uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8080 --reload
```

### 5. API Testing

Use the HTTP Client files in `api-tests/`:

- `api-tests/auth.http`: Register, login, token refresh, `/me` profile.
- `api-tests/profile.http`: Profile update, avatar upload URL generation.
- `api-tests/temple.http`: Create temple, search temples, get temple details.
- `api-tests/goshuin.http`: Create goshuin, upload goshuin image, query goshuins.

---

## 9. AI Agent Playbooks (How to Perform Common Tasks)

### Playbook A: Adding a New REST Endpoint

1. Open `services/<service>/openapi.yaml`.
2. Define the path, HTTP method, operation ID, request body, and response schemas.
3. Run `.\mvnw compile -pl services/<service>` to generate updated interfaces and DTOs.
4. Update the corresponding `@RestController` in `services/<service>/src/main/java/.../controller/` to implement the new
   method.
5. Implement business logic in the `@Service` class and database queries in the `@Repository` class.
6. Add unit/slice tests in `src/test/java/` verifying happy path and error cases.
7. Add a sample request in `api-tests/<service>.http`.

### Playbook B: Adding or Modifying a Database Table/Column

1. Create a new changeset YAML file in
   `libs/liquibase/src/main/resources/db/changelog/<service>/<next-number>-<description>/db.changelog-<next-number>.yaml`.
2. Register the new file in `libs/liquibase/src/main/resources/db/changelog/<service>/db.changelog-master.yaml`.
3. Update the JPA Entity in `services/<service>/src/main/java/.../entity/` to reflect the new mapping.
4. Ensure schema names are explicitly specified on `@Table(name = "...", schema = "<service>_schema")`.
5. Run tests or launch the service locally with `application-local.yml` to verify Liquibase migration runs cleanly.

### Playbook C: Adding Inter-Service Communication

1. In the calling service, define or update an OpenFeign interface in `.../client/` (e.g. `ProfileClient.java`).
2. Add
   `@FeignClient(name = "<service-name>", url = "${<SERVICE>_SERVICE_URL:http://localhost:<port>}", fallbackFactory = <Client>FallbackFactory.class)`.
3. Implement the fallback factory handling circuit breaker / network failures gracefully.
4. If authentication context propagation is needed, configure a `RequestInterceptor` that attaches the
   `Authorization: Bearer <token>` header from `SecurityContextHolder`.

---

## 10. Critical Rules & Guardrails for AI Agents

1. **Do NOT edit generated code**: Never touch files inside `target/` or `target/generated-sources/openapi/`. Always
   update `openapi.yaml` and recompile.
2. **Respect schema boundaries**: Always qualify tables with their respective schema (`auth_schema`, `profile_schema`,
   `goshuin_schema`). Never place application tables in the `public` schema.
3. **Handle security consistently**: Use `SecurityUtils` to retrieve the authenticated user's ID. Never accept `userId`
   from user-facing request bodies when the user ID should come from the validated JWT token.
4. **Preserve error handling uniformity**: Return errors using `libs/common` `ErrorResponse` and throw standard
   exceptions handled by `GlobalExceptionHandler`.
5. **Do NOT weaken tests**: Never disable (`@Disabled`, `@Ignore`), skip (`-DskipTests`), or hollow out assertions in
   existing test suites.
6. **Keep Liquibase migrations idempotent & immutable**: Never modify existing, already-deployed Liquibase changelog
   files. Always add a new changeset file.
7. **Windows vs Linux path compatibility**: Maven wrapper is `mvnw.cmd` on Windows (PowerShell) and `./mvnw` on Unix. In
   CI/CD scripts and Dockerfiles, use Unix paths.
