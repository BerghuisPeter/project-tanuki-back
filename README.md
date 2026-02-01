# Getting Started - Project Tanuki

## Architecture Overview

This project is organized as a multi-module Maven project:

- **`services/`**: Independent Spring Boot microservices.
    - `auth-service`: Handles authentication, user management, and roles. (Port: 8081)
    - `profile-service`: Manages user profiles. (Port: 8082)
- **`libs/`**: Shared infrastructure libraries.
    - `security`: Common security utilities, JWT handling, and shared security configuration.
  - `common`: Generic utilities, shared DTOs, and global exception handling.
  - `observability`: Logging, tracing, and metrics configuration (Actuator).
- **`pom.xml`**: Parent POM managing shared dependencies and versions.

---

## Shared Observability Library (`libs/observability`)

The `observability` library provides pre-configured Spring Boot Actuator settings for monitoring and health checks.

### How to use:

Add the dependency to your `pom.xml`:

```xml

<dependency>
    <groupId>io.github.peterberghuis</groupId>
    <artifactId>observability</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Shared Security Library (`libs/security`)

The `security` library provides a standardized way to handle authentication across microservices using JWT.

### Key Components:

- **`JwtUtils`**: Handles token generation, validation, and claim extraction.
- **`JwtAuthenticationFilter`**: A per-request filter that extracts JWT from the `Authorization: Bearer <token>` header
  and populates the `SecurityContext`.
- **`SharedSecurityConfig`**: Pre-configured `SecurityFilterChain` that enables stateless sessions and JWT
  authentication.

### How to use:

1. Add the dependency to your `pom.xml`:
   ```xml
   <dependency>
       <groupId>io.github.peterberghuis</groupId>
       <artifactId>security</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```
2. Enable component scanning for the shared package in your Application class:
   ```java
   @SpringBootApplication(scanBasePackages = "io.github.peterberghuis")
   ```
3. Provide the required properties in `application.yml`:
   ```yaml
   jwt:
     secret: your-very-long-and-secure-secret-key-that-is-at-least-32-characters
     expiration: 86400000 # 1 day in ms
   ```

---

## Shared Common Library (`libs/common`)

The `common` library provides shared DTOs and global exception handling logic to maintain consistency across services.

### Key Components:

- **`GlobalExceptionHandler`**: A central exception handler that ensures a consistent `ErrorResponse` format across all
  services.
- **`ErrorResponse`**: Standardized error DTO used by the global exception handler.

### How to use:

Add the dependency to your `pom.xml`:

```xml

<dependency>
    <groupId>io.github.peterberghuis</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Prerequisites

- **Java 25**: The project uses the latest Java features.
- **Spring Boot 4.0.1**: Utilizing the next generation of Spring.
- **Docker**: Required for running the database and other infrastructure.
- **Maven**: (Optional) You can use the included `mvnw` wrapper.

---

## Startup Guide

If you are opening this repository for the first time, follow these steps:

### 1. Start the Infrastructure

The database is managed via Docker Compose. Run this from the root directory:

```powershell
docker compose up -d
```

### 2. Build and Install Shared Components

Since services depend on shared libraries, you must build the entire project once to install them into your local Maven
repository:

```powershell
.\mvnw clean install -DskipTests
```

### 3. Run a Specific Service

You can run any service using the Maven wrapper. For example, to start the **Authentication Service**:

```powershell
.\mvnw spring-boot:run -pl services/auth-service
```

To start the **Profile Service**:

```powershell
.\mvnw spring-boot:run -pl services/profile-service
```

---

## Development Details

### Configuration Profiles

- **`dev` (Default)**: Uses local PostgreSQL via Docker.
- **`prod`**: Configured for environment-based settings (e.g., `${DATABASE_URL}`).

### Database Versioning & Schema Management

Database changes are managed by **Liquibase**.

#### Schema Strategy

- **Liquibase Metadata**: The `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK` tables are stored in the `public` schema.
- **Application Schemas**: Each service creates and manages its own schema (e.g., `auth_schema`, `profile_schema`). This
  allows Liquibase to bootstrap the schemas on the first run without circular dependencies.

#### Changelogs

- `auth-service`: `services/auth-service/src/main/resources/db/changelog/`
- `profile-service`: `services/profile-service/src/main/resources/db/changelog/`

#### Foreign Keys

When defining foreign keys across schemas in Liquibase, use `addForeignKeyConstraint` with explicit
`baseTableSchemaName` and `referencedTableSchemaName` to ensure correct resolution.

### IDE Setup (IntelliJ IDEA)

1. Open the root `pom.xml`.
2. Wait for Maven synchronization.
3. Run the applications directly from their respective `*Application.java` files.

---

### Reference Documentation

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.1/maven-plugin)
* [Liquibase Documentation](https://docs.liquibase.com/home.html)

