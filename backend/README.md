# Dental Clinic Backend

Spring Boot backend for the dental clinic management system. The repository is currently implemented through **PHASE 06 — Admin User Management**.

## Implemented

- Java 21, Spring Boot 3.5, Maven and PostgreSQL 18.
- Flyway V1 migration for the 12-table schema.
- JPA entities and repositories for the first nine business tables.
- Common API response, pagination and exception handling.
- Patient registration and login with BCrypt and JWT access tokens.
- Role-based authorization for ADMIN, DOCTOR and PATIENT.
- Patient self-service profile APIs.
- Admin user search, filtering, pagination, detail and status management.
- PostgreSQL integration tests; the current test suite contains 54 passing tests.

Frontend and AI chatbot features are not implemented yet.

## Requirements

- JDK 21 LTS
- Maven 3.9+
- PostgreSQL Server 18.x
- `uuid-ossp` and pgvector 0.8.7 or a compatible PostgreSQL 18 build

## Run

Set the variables from `.env.example` in the shell. `JWT_SECRET` must be a Base64-encoded value containing at least 32 random bytes. Then run:

```text
mvn spring-boot:run
```

The default database is `dental_clinic` on `localhost:5432`. Flyway owns the schema and Hibernate uses `ddl-auto=validate`.

For this Windows environment, use the installed JDK explicitly if the `java` PATH shim is unavailable:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
mvn spring-boot:run
```

## Current APIs

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/patients/me`
- `PUT /api/patients/me`
- `GET /api/admin/users`
- `GET /api/admin/users/{id}`
- `PATCH /api/admin/users/{id}/status`

## Test

Provide the PostgreSQL and JWT environment variables, then run:

```text
mvn clean test
```

Tests that validate PostgreSQL-specific behavior use the real PostgreSQL database, not H2 or Docker.
