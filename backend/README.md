# Dental Clinic Backend

Spring Boot backend for the dental clinic management system. The repository currently includes **PHASE 26 — Swagger & Backend Review**.

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
- Role-owned patient and doctor appointment queries plus system-wide admin queries.
- Appointment search, status/date/entity filters, pagination and clinic-timezone responses.
- Transactional patient cancellation with a 90-minute cutoff and CANCEL audit history.
- Pessimistic appointment locking and PostgreSQL partial-index slot release verification.
- Transactional patient rescheduling with a 90-minute cutoff calculated from the old appointment.
- Same-appointment rescheduling with schedule validation, RESCHEDULE audit history and no 180-minute booking lead-time rule on the new slot.
- PostgreSQL partial-unique-index protection that keeps the losing appointment's old slot intact during a concurrent reschedule race.
- Doctor-owned patient check-in with a pessimistic appointment lock, `BOOKED → CHECKED_IN`, actual check-in timestamp and CHECK_IN audit history.
- Doctor-owned examination start with `CHECKED_IN → IN_PROGRESS`, actual start timestamp and START_EXAM audit history.
- Per-doctor transactional locking prevents a second appointment from entering `IN_PROGRESS` before the current examination is completed.
- Doctor-owned NO_SHOW confirmation after the configured 15-minute grace period, with checked-in protection and NO_SHOW audit history.
- Doctor-owned medical-record creation and update while an appointment is `IN_PROGRESS`.
- Typed prescription DTOs persisted as a JSONB array, one medical record per appointment and follow-up information validation.
- Doctor-owned examination completion with MedicalRecord validation, `IN_PROGRESS → COMPLETED`, actual end timestamp and COMPLETE audit history.
- Doctor-created follow-up appointments reuse the slot engine, inherit patient and doctor ownership from the prior examination, and persist `previous_appointment_id` with BOOK audit history.
- Patients can view paginated medical history and individual completed examination results, including doctor, service, prescription and follow-up guidance.
- Medical-history queries derive ownership from the authenticated patient and hide unfinished or other patients' medical records.
- Admins can inspect each appointment's chronological audit timeline with action, actor, reason, JSONB details and timestamp.
- Admin dashboard summary reports patient and doctor totals plus today's appointment status counts using the clinic timezone.
- PostgreSQL integration tests cover the appointment lifecycle, transaction rollback and concurrent booking/rescheduling races.
- A PostgreSQL 18 schema-contract suite verifies the server major version, required extensions, Flyway V1, all 12 tables, `vector(768)`, the HNSW cosine index and critical native constraints.
- OpenAPI 3 documentation exposes typed request/response schemas, reusable error responses and JWT Bearer authorization.
- Swagger UI and OpenAPI JSON are public in development and disabled by default in production.
- React clients are supported through an environment-controlled CORS allowlist without cookie credentials.
- The current test suite contains 334 passing tests without H2, Docker or an alternate database.

Frontend and AI chatbot features are not implemented yet.

## Requirements

- JDK 21 LTS
- Maven 3.9+
- PostgreSQL Server 18.x
- `uuid-ossp` and pgvector 0.8.7 or a compatible PostgreSQL 18 build

## Environment variables

Copy the names from `.env.example` into the terminal or the deployment environment. Do not commit real credentials.

| Variable | Required | Description |
|---|---:|---|
| `DB_URL` | Yes | PostgreSQL JDBC URL, normally `jdbc:postgresql://localhost:5432/dental_clinic` |
| `DB_USERNAME` | Yes | PostgreSQL login role |
| `DB_PASSWORD` | Yes | PostgreSQL password; never store it in source control |
| `JWT_SECRET` | Yes | Base64 value containing at least 32 random bytes |
| `SPRING_PROFILES_ACTIVE` | No | `development` by default; use `production` when deploying |
| `CLINIC_TIMEZONE` | No | Business timezone, defaults to `Asia/Ho_Chi_Minh` |
| `CORS_ALLOWED_ORIGINS` | Frontend | Comma-separated exact origins, for example `http://localhost:5173` |
| `OPENAPI_ENABLED` | No | Enables `/v3/api-docs`; defaults to `false` in production |
| `SWAGGER_UI_ENABLED` | No | Enables Swagger UI; defaults to `false` in production |

The remaining pool, port and appointment-rule variables are listed in [.env.example](./.env.example).

## PostgreSQL 18 setup

The PostgreSQL server must already contain a database named `dental_clinic`, and the PostgreSQL 18 installation must provide both `uuid-ossp` and pgvector. Verify the server before startup:

```sql
SELECT version();
SHOW server_version;
SHOW server_version_num;

SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name IN ('vector', 'uuid-ossp');
```

Flyway applies [V1__init_schema.sql](./src/main/resources/db/migration/V1__init_schema.sql). Hibernate only validates the schema through `ddl-auto=validate`.

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

## OpenAPI and Swagger

With the development profile running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Use `POST /api/auth/login`, copy `data.accessToken`, select **Authorize** in Swagger UI and enter the token. Swagger adds the `Bearer` prefix through the configured HTTP security scheme.

Public endpoints do not require a token. Patient, doctor and admin endpoints document the JWT requirement and return `401` for missing/invalid authentication or `403` for insufficient role permissions.

## REST response contract

Every endpoint returns an `ApiResponse<T>` envelope. Successful response example:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {}
}
```

Validation error example:

```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "Dữ liệu đầu vào không hợp lệ",
  "errors": [
    {
      "field": "email",
      "message": "Email không đúng định dạng"
    }
  ],
  "path": "/api/auth/register"
}
```

Pagination is zero-based and returned inside `data` with `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`, `hasNext` and `hasPrevious`.

### Error codes

| HTTP | Codes |
|---:|---|
| 400 | `INVALID_REQUEST`, `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `PASSWORD_TOO_LONG` |
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| 403 | `ACCOUNT_INACTIVE`, `ACCESS_DENIED` |
| 404 | `RESOURCE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `RESOURCE_CONFLICT`, `EMAIL_ALREADY_EXISTS`, `LICENSE_NUMBER_ALREADY_EXISTS`, `SERVICE_NAME_ALREADY_EXISTS`, `SCHEDULE_ALREADY_EXISTS` |
| 500 | `INTERNAL_SERVER_ERROR` |

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
- `GET /api/patient/appointments`
- `GET /api/patient/appointments/{id}`
- `PATCH /api/patient/appointments/{id}/cancel`
- `PATCH /api/patient/appointments/{id}/reschedule`
- `GET /api/doctor/appointments`
- `GET /api/doctor/appointments/today`
- `PATCH /api/doctor/appointments/{id}/check-in`
- `PATCH /api/doctor/appointments/{id}/start`
- `PATCH /api/doctor/appointments/{id}/no-show`
- `POST /api/doctor/appointments/{id}/medical-record`
- `PUT /api/doctor/appointments/{id}/medical-record`
- `PATCH /api/doctor/appointments/{id}/complete`
- `GET /api/doctor/appointments/{id}/follow-up-slots?serviceId={serviceId}&date={yyyy-MM-dd}`
- `POST /api/doctor/appointments/{id}/follow-up`
- `GET /api/patient/medical-records`
- `GET /api/patient/medical-records/{appointmentId}`
- `GET /api/admin/appointments`
- `GET /api/admin/appointments/{id}/history`
- `GET /api/admin/dashboard/summary`
- `GET /api/services/{serviceId}/doctors`
- `PUT /api/admin/doctors/{doctorId}/services`
- `GET /api/doctor/schedules`
- `POST /api/doctor/schedules`
- `PUT /api/doctor/schedules/{id}`
- `DELETE /api/doctor/schedules/{id}`

## React integration notes

- Configure the frontend API base URL as `http://localhost:8080` in development.
- Send access tokens with `Authorization: Bearer <token>`; this demo intentionally has no refresh-token endpoint.
- Configure `CORS_ALLOWED_ORIGINS` with exact React origins. Wildcard origins are not used.
- Treat `401` as an expired/missing session, `403` as insufficient permission and `409` as a recoverable business conflict.
- Parse timestamps as ISO-8601 values. Appointment display and day-based queries follow `Asia/Ho_Chi_Minh`.
- Use the documented `ApiResponse` envelope and zero-based pagination contract instead of relying on JPA entity shapes.

## Production security

- Swagger UI and `/v3/api-docs` are disabled by default under the `production` profile. Enable them only with `SWAGGER_UI_ENABLED=true` and `OPENAPI_ENABLED=true` when operationally required.
- Set a new Base64 JWT secret with at least 256 bits and keep it outside source control.
- Set `CORS_ALLOWED_ORIGINS` to the exact HTTPS frontend origins; an empty production value denies cross-origin browser requests.
- Keep PostgreSQL SCRAM authentication enabled and provide database credentials through environment variables.

## Test

Provide the PostgreSQL and JWT environment variables, then run:

```text
mvn clean test
```

Tests that validate PostgreSQL-specific behavior use the real PostgreSQL database, not H2 or Docker.
