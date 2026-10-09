# Dental Clinic Backend

Spring Boot backend for the dental clinic management system. The repository is currently implemented through **PHASE 12 — Book Appointment**.

## Implemented

- Java 21, Spring Boot 3.5, Maven and PostgreSQL 18.
- Flyway V1 migration for the 12-table schema.
- JPA entities and repositories for the first nine business tables.
- Common API response, pagination and exception handling.
- Patient registration and login with BCrypt and JWT access tokens.
- Role-based authorization for ADMIN, DOCTOR and PATIENT.
- Patient self-service profile APIs.
- Admin user search, filtering, pagination, detail and status management.
- Public doctor listing and detail APIs.
- Admin doctor account creation, profile update and working-status management.
- Public dental-service listing and detail APIs.
- Admin dental-service creation, update and soft status management.
- Public doctor-to-service and service-to-doctor lookup APIs.
- Transactional admin replacement of doctor-service mappings with composite-key and foreign-key protection.
- Public weekly doctor-schedule lookup.
- Doctor-owned schedule CRUD with weekday, session and time-range validation.
- Public available-slot calculation from weekly schedules and appointments.
- Fixed 60-minute slots with active doctor/service/mapping checks and a 180-minute booking lead time.
- Transactional patient appointment booking with BOOK audit history.
- PostgreSQL partial-unique-index protection for concurrent slot booking conflicts.
- PostgreSQL integration tests; the current test suite contains 160 passing tests.

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
- `GET /api/doctors`
- `GET /api/doctors/{id}`
- `POST /api/admin/doctors`
- `PUT /api/admin/doctors/{id}`
- `PATCH /api/admin/doctors/{id}/status`
- `GET /api/services`
- `GET /api/services/{id}`
- `POST /api/admin/services`
- `PUT /api/admin/services/{id}`
- `PATCH /api/admin/services/{id}/status`
- `GET /api/doctors/{doctorId}/services`
- `GET /api/doctors/{doctorId}/schedule`
- `GET /api/doctors/{doctorId}/available-slots?serviceId={serviceId}&date={yyyy-MM-dd}`
- `POST /api/patient/appointments`
- `GET /api/services/{serviceId}/doctors`
- `PUT /api/admin/doctors/{doctorId}/services`
- `GET /api/doctor/schedules`
- `POST /api/doctor/schedules`
- `PUT /api/doctor/schedules/{id}`
- `DELETE /api/doctor/schedules/{id}`

## Test

Provide the PostgreSQL and JWT environment variables, then run:

```text
mvn clean test
```

Tests that validate PostgreSQL-specific behavior use the real PostgreSQL database, not H2 or Docker.
