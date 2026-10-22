# Dental Clinic Backend

Spring Boot backend for the dental clinic management system. The repository currently includes **PHASE 26.4 — Refresh Token and Server-side Sessions**.

## Implemented

- Java 21, Spring Boot 3.5, Maven and PostgreSQL 18.
- Flyway V1–V8 migrations for the 15-table active schema.
- JPA entities and repositories for the first nine business tables.
- Common API response, pagination and exception handling.
- Patient registration and login with BCrypt, 15-minute JWT access tokens and rotating Refresh Tokens.
- PostgreSQL-backed login sessions for ADMIN, DOCTOR, STAFF and PATIENT; Refresh Tokens are stored only as SHA-256 hashes.
- Fixed absolute session limits: ADMIN 8 hours, DOCTOR/STAFF 12 hours and PATIENT 7 days; refresh never extends the original deadline.
- Idle lock applies only to ADMIN (15 minutes) and DOCTOR/STAFF (20 minutes), with password unlock inside the original absolute lifetime.
- Cookie-based refresh/logout/unlock use HttpOnly Refresh Token cookies and double-submit CSRF validation.
- Role-based authorization for ADMIN, STAFF, DOCTOR and PATIENT.
- Patient self-service profile APIs.
- Admin user search, filtering, pagination, detail and status management.
- Admin-only staff account creation, listing, detail and activation/deactivation.
- Admin status updates reject self-deactivation and preserve at least one active admin with a PostgreSQL transaction-level advisory lock.
- Public doctor listing and detail APIs.
- Admin doctor account creation, profile update and working-status management. Creating a doctor atomically creates 12 default weekly schedules; a schedule failure rolls back the User and Doctor.
- Public dental-service listing and detail APIs.
- Admin dental-service creation, update and soft status management.
- Public doctor-to-service and service-to-doctor lookup APIs.
- Transactional admin replacement of doctor-service mappings with composite-key and foreign-key protection.
- Public weekly doctor-schedule lookup.
- Admin-owned weekly doctor-schedule CRUD for Monday-Saturday, hourly boundaries inside `08:00-11:00` and `13:00-17:00`; doctors have read-only access to their own schedules.
- Weekly schedule updates/deletes take the same Doctor lock as booking and reject changes that would invalidate future active appointments.
- Doctor availability is derived only from weekly schedules, active doctor/service mappings, current time and existing appointments.
- Booking, rescheduling and patient follow-up creation share a pessimistic Doctor lock and recheck weekly schedule availability before writing.
- Public available-slot calculation from weekly schedules and appointments.
- Public rolling 30-day doctor calendar calculated with one schedule query and one occupied-slot query for the requested range.
- Fixed 60-minute slots with active doctor/service/mapping checks and a 120-minute booking lead time.
- Transactional patient appointment booking with BOOK audit history.
- PostgreSQL partial-unique-index protection for concurrent slot booking conflicts.
- Role-owned patient and doctor appointment queries plus system-wide admin queries.
- Appointment search, status/date/entity filters, pagination and clinic-timezone responses.
- Transactional patient cancellation with a 90-minute cutoff and CANCEL audit history.
- Pessimistic appointment locking and PostgreSQL partial-index slot release verification.
- Transactional patient rescheduling with a 90-minute cutoff calculated from the old appointment.
- Same-appointment rescheduling with schedule validation, RESCHEDULE audit history and no 120-minute booking lead-time rule on the new slot.
- PostgreSQL partial-unique-index protection that keeps the losing appointment's old slot intact during a concurrent reschedule race.
- New examinations start directly with doctor-owned `BOOKED → IN_PROGRESS`; no check-in endpoint is exposed.
- Historical `CHECKED_IN` appointments remain readable and may transition to `IN_PROGRESS` for backward compatibility.
- Per-doctor transactional locking prevents a second appointment from entering `IN_PROGRESS` before the current examination is completed.
- Doctor-owned NO_SHOW confirmation after the configured 15-minute grace period, with checked-in protection and NO_SHOW audit history.
- Doctor-owned medical-record creation and update while an appointment is `IN_PROGRESS`.
- The responsible doctor can read the official MedicalRecord after saving it and continue editing it with `PUT` while the Appointment remains `IN_PROGRESS`; no draft or autosave workflow is exposed.
- Typed prescription DTOs persisted as a JSONB array, one medical record per appointment and mandatory recommended date when follow-up is required.
- Doctors record only services actually performed while an appointment is `IN_PROGRESS`; names and catalog prices are persisted as immutable JSONB snapshots in a DRAFT payment.
- Payment item prices are always loaded from PostgreSQL, quantities are validated, duplicate services are rejected and additional services must be active and assigned to the responsible doctor.
- Doctor-owned examination completion validates both MedicalRecord and Payment, atomically transitions `DRAFT → UNPAID` with `IN_PROGRESS → COMPLETED`, and writes COMPLETE audit history.
- Staff collect payment at the clinic counter and atomically confirm `UNPAID → PAID` using `CASH` or manually verified `BANK_TRANSFER`; Admin retains a backup confirmation API.
- A pessimistic Payment row lock ensures two concurrent confirmations cannot record the same revenue twice.
- Patients can view only their own finalized payment records; DRAFT data remains internal to the examination workflow.
- Staff payment search, filtering, pagination and sorting expose only finalized `UNPAID`/`PAID` records; Admin retains the full management view.
- Staff can search and filter all clinic appointments to guide patients to the responsible doctor, without access to medical-record APIs.
- Dashboard revenue uses `payments.paid_at` in `Asia/Ho_Chi_Minh` and counts only PAID payments; UNPAID totals are reported separately.
- Patients self-book recommended follow-up appointments through the normal booking API by supplying `previousAppointmentId`; ownership, completed status, recommendation and active-follow-up uniqueness are rechecked transactionally.
- Patients can view paginated medical history and individual completed examination results, including doctor, service, prescription and follow-up guidance.
- Medical-history queries derive ownership from the authenticated patient and hide unfinished or other patients' medical records.
- Admins can inspect each appointment's chronological audit timeline with action, actor, reason, JSONB details and timestamp.
- Admin dashboard summary reports patient and doctor totals plus today's appointment status counts using the clinic timezone.
- PostgreSQL integration tests cover the appointment lifecycle, transaction rollback and concurrent booking/rescheduling races.
- A PostgreSQL 18 schema-contract suite verifies the server major version, required extensions, Flyway V1–V8, all 15 active tables, session/token constraints, Payment JSONB/state constraints, `vector(768)`, the HNSW cosine index and critical native constraints.
- OpenAPI 3 documentation exposes typed request/response schemas, reusable error responses and JWT Bearer authorization.
- Swagger UI and OpenAPI JSON are public in development and disabled by default in production.
- React clients are supported through an exact-origin CORS allowlist with credentialed requests for auth cookies.
- Unit, security and PostgreSQL integration tests run without H2, Docker or an alternate database.

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
| `AUTH_COOKIE_SECURE` | No | `false` for local HTTP development; must be `true` on production HTTPS |
| `AUTH_COOKIE_SAME_SITE` | No | Refresh/CSRF cookie SameSite policy; defaults to `Lax` |
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

Flyway applies [V1__init_schema.sql](./src/main/resources/db/migration/V1__init_schema.sql) through [V8__remove_medical_record_drafts.sql](./src/main/resources/db/migration/V8__remove_medical_record_drafts.sql). V6 only backfills doctors with no weekly schedule and never overwrites partial/customized schedules. V7 removes the obsolete doctor-day-off table only when it is empty. V8 similarly removes `medical_record_drafts` only when it is empty; if historical rows exist, migration stops so the unfinalized patient data can be reviewed or archived explicitly. Hibernate only validates the schema through `ddl-auto=validate`.

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

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

Use `POST /api/auth/login`, copy `data.accessToken`, select **Authorize** in Swagger UI and enter the token. Swagger adds the `Bearer` prefix through the configured HTTP security scheme. The browser receives `refresh_token` (HttpOnly) and `XSRF-TOKEN` cookies; refresh/logout/unlock additionally require the `X-XSRF-TOKEN` header.

Public endpoints do not require a token. Patient, doctor, staff and admin endpoints document the JWT requirement and return `401` for missing/invalid authentication or `403` for insufficient role permissions.

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
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED`, `SESSION_REVOKED`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REUSED` |
| 403 | `ACCOUNT_INACTIVE`, `ACCESS_DENIED`, `CSRF_VALIDATION_FAILED` |
| 423 | `SESSION_LOCKED` |
| 404 | `RESOURCE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `RESOURCE_CONFLICT`, `EMAIL_ALREADY_EXISTS`, `LICENSE_NUMBER_ALREADY_EXISTS`, `SERVICE_NAME_ALREADY_EXISTS`, `SCHEDULE_ALREADY_EXISTS` |
| 500 | `INTERNAL_SERVER_ERROR` |

## Current APIs

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/activity`
- `POST /api/auth/unlock`
- `GET /api/auth/session`
- `GET /api/patients/me`
- `PUT /api/patients/me`
- `GET /api/admin/users`
- `GET /api/admin/users/{id}`
- `PATCH /api/admin/users/{id}/status`
- `POST /api/admin/staff`
- `GET /api/admin/staff?search=&status=&page=&size=`
- `GET /api/admin/staff/{id}`
- `PATCH /api/admin/staff/{id}/status`
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
- `GET /api/doctors/{doctorId}/calendar?serviceId={serviceId}&fromDate={yyyy-MM-dd}&toDate={yyyy-MM-dd}` (dates optional; defaults to today through today + 29 days)
- `POST /api/patient/appointments`
- `GET /api/patient/appointments`
- `GET /api/patient/appointments/{id}`
- `PATCH /api/patient/appointments/{id}/cancel`
- `PATCH /api/patient/appointments/{id}/reschedule`
- `GET /api/doctor/appointments`
- `GET /api/doctor/appointments/today`
- `PATCH /api/doctor/appointments/{id}/start`
- `PATCH /api/doctor/appointments/{id}/no-show`
- `POST /api/doctor/appointments/{id}/medical-record`
- `GET /api/doctor/appointments/{id}/medical-record`
- `PUT /api/doctor/appointments/{id}/medical-record`
- `PATCH /api/doctor/appointments/{id}/complete`
- `GET /api/doctor/appointments/{id}/payment`
- `PUT /api/doctor/appointments/{id}/payment/items`
- `GET /api/staff/appointments?search=&status=&doctorId=&serviceId=&fromDate=&toDate=&page=&size=&sortBy=&direction=`
- `GET /api/staff/payments?search=&status=&fromDate=&toDate=&page=&size=&sortBy=&direction=`
- `GET /api/staff/payments/{id}`
- `PATCH /api/staff/payments/{id}/confirm`
- `GET /api/patient/medical-records`
- `GET /api/patient/medical-records/{appointmentId}`
- `GET /api/admin/appointments`
- `GET /api/admin/appointments/{id}/history`
- `GET /api/admin/dashboard/summary`
- `GET /api/admin/payments?search=&status=&fromDate=&toDate=&page=&size=&sortBy=&direction=`
- `GET /api/admin/payments/{id}`
- `PATCH /api/admin/payments/{id}/confirm`
- `GET /api/patient/payments`
- `GET /api/patient/payments/{id}`
- `GET /api/admin/dashboard/revenue?fromDate={yyyy-MM-dd}&toDate={yyyy-MM-dd}&month={yyyy-MM}`
- `GET /api/services/{serviceId}/doctors`
- `PUT /api/admin/doctors/{doctorId}/services`
- `GET /api/doctor/schedules`
- `GET /api/admin/doctors/{doctorId}/schedules`
- `POST /api/admin/doctors/{doctorId}/schedules`
- `PUT /api/admin/doctors/{doctorId}/schedules/{scheduleId}`
- `DELETE /api/admin/doctors/{doctorId}/schedules/{scheduleId}`

### Doctor calendar response example

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "doctorId": "<DOCTOR_ID>",
    "serviceId": "<SERVICE_ID>",
    "fromDate": "2026-10-22",
    "toDate": "2026-11-20",
    "days": [
      {
        "date": "2026-10-22",
        "dayOfWeek": 4,
        "status": "AVAILABLE",
        "sessions": [
          {
            "session": "MORNING",
            "status": "AVAILABLE",
            "availableSlotCount": 3,
            "availableSlots": [
              {"startAt": "2026-10-22T08:00:00+07:00", "endAt": "2026-10-22T09:00:00+07:00"},
              {"startAt": "2026-10-22T09:00:00+07:00", "endAt": "2026-10-22T10:00:00+07:00"},
              {"startAt": "2026-10-22T10:00:00+07:00", "endAt": "2026-10-22T11:00:00+07:00"}
            ]
          },
          {
            "session": "AFTERNOON",
            "status": "AVAILABLE",
            "availableSlotCount": 4,
            "availableSlots": [
              {"startAt": "2026-10-22T13:00:00+07:00", "endAt": "2026-10-22T14:00:00+07:00"},
              {"startAt": "2026-10-22T14:00:00+07:00", "endAt": "2026-10-22T15:00:00+07:00"},
              {"startAt": "2026-10-22T15:00:00+07:00", "endAt": "2026-10-22T16:00:00+07:00"},
              {"startAt": "2026-10-22T16:00:00+07:00", "endAt": "2026-10-22T17:00:00+07:00"}
            ]
          }
        ]
      }
    ]
  }
}
```

## React integration notes

- Configure the frontend API base URL as `http://localhost:8081` in development.
- Keep the Access Token in React memory and send it with `Authorization: Bearer <token>`; do not place either token in `localStorage`.
- Send auth-cookie requests with credentials. Read `XSRF-TOKEN` and mirror it in `X-XSRF-TOKEN` for refresh, logout and unlock.
- If React and the API use different subdomains, configure `AUTH_COOKIE_DOMAIN` for their shared parent domain; prefer a same-origin reverse proxy in production.
- Only explicit user interaction should call `/api/auth/activity`; background refresh and other background requests must not keep an idle ADMIN/DOCTOR/STAFF session active.
- Configure `CORS_ALLOWED_ORIGINS` with exact React origins. Wildcard origins are not used.
- Treat `401` as an expired/missing session, `403` as insufficient permission and `409` as a recoverable business conflict.
- A cancelled appointment may include `cancellationReason`, `cancellationSource` and `cancelledAt`. Treat an existing `cancellationSource` as immutable historical audit data; it does not affect current slot availability.
- Parse timestamps as ISO-8601 values. Appointment display and day-based queries follow `Asia/Ho_Chi_Minh`.
- Load the booking month with `/api/doctors/{doctorId}/calendar`. Render the returned `days` directly and use each slot's `startAt` as the booking value. The API returns at most the rolling 30-day window; refresh it when the clinic date changes.
- The calendar session status is `AVAILABLE`, `FULL`, `NOT_WORKING` or `UNAVAILABLE`. `NOT_WORKING` means no matching weekly schedule (including Sunday); `FULL` means scheduled slots are occupied; `UNAVAILABLE` means no slot remains bookable because of time or another booking rule.
- Use the documented `ApiResponse` envelope and zero-based pagination contract instead of relying on JPA entity shapes.
- Send only `serviceId` and `quantity` when a doctor updates Payment items. Never send or calculate prices as trusted frontend data.
- A completed examination exposes an UNPAID payment until Staff, or Admin as backup, confirms actual receipt at the counter. `BANK_TRANSFER` is manual confirmation, not an online gateway.
- Normal booking omits `previousAppointmentId`. A patient books a recommended follow-up through the same endpoint with the completed appointment ID in `previousAppointmentId`.
- Revenue belongs to the clinic date containing `paidAt`, which may differ from the examination date.

## Direct payment workflow

1. The doctor starts a booked appointment directly, changing `BOOKED` to `IN_PROGRESS`. Starting the examination is the operational evidence that the patient arrived.
2. While the Appointment is `IN_PROGRESS`, the responsible doctor explicitly saves the official MedicalRecord with `POST /api/doctor/appointments/{id}/medical-record`. After saving, the doctor can reload it with `GET` and edit it with `PUT`; there is no draft or autosave endpoint.
3. The doctor calls `PUT /api/doctor/appointments/{id}/payment/items` with the services actually performed.
4. The backend validates doctor-service assignments, loads current catalog prices and stores name/price snapshots in a DRAFT Payment.
5. `PATCH /api/doctor/appointments/{id}/complete` requires a valid MedicalRecord and a non-empty valid DRAFT Payment; both Appointment and Payment are finalized atomically as `COMPLETED` and `UNPAID`.
6. After receiving money at the counter, Staff calls `PATCH /api/staff/payments/{id}/confirm` with `CASH` or `BANK_TRANSFER`. Admin may use the equivalent Admin endpoint as backup.
7. Patients can then read their own UNPAID/PAID records. No payment gateway, webhook, installment, refund or accounting invoice is implemented.

Doctor item request example:

```json
{
  "items": [
    {"serviceId": "00000000-0000-0000-0000-000000000001", "quantity": 1},
    {"serviceId": "00000000-0000-0000-0000-000000000002", "quantity": 1}
  ]
}
```

Staff/Admin confirmation example:

```json
{
  "paymentMethod": "CASH"
}
```

## Production security

- Swagger UI and `/v3/api-docs` are disabled by default under the `production` profile. Enable them only with `SWAGGER_UI_ENABLED=true` and `OPENAPI_ENABLED=true` when operationally required.
- Set a new Base64 JWT secret with at least 256 bits and keep it outside source control.
- Set `CORS_ALLOWED_ORIGINS` to the exact HTTPS frontend origins; an empty production value denies cross-origin browser requests.
- Keep `AUTH_COOKIE_SECURE=true` in production and terminate traffic over HTTPS.
- Keep PostgreSQL SCRAM authentication enabled and provide database credentials through environment variables.

## Test

Provide the PostgreSQL and JWT environment variables, then run:

```text
mvn clean test
```

Tests that validate PostgreSQL-specific behavior use the real PostgreSQL database, not H2 or Docker.
