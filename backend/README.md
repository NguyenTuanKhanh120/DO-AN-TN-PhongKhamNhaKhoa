# Dental Clinic Backend

Spring Boot backend for the dental clinic management system, including the patient-facing Gemini/RAG dental assistant completed through **PHASE AI-15**.

## Implemented

- Java 21, Spring Boot 3.5, Maven and PostgreSQL 18.
- Flyway V1–V18 migrations for the 16-table active schema.
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
- Admin-only JPEG/PNG/WebP service-image upload (maximum 5 MB), local UUID-based storage and public cached image delivery.
- Public doctor-to-service and service-to-doctor lookup APIs.
- Transactional admin replacement of doctor-service mappings with composite-key and foreign-key protection.
- Public weekly doctor-schedule lookup.
- Admin-owned weekly doctor-schedule CRUD for Monday-Saturday with exact sessions `MORNING 08:00-11:00` and `AFTERNOON 14:00-17:00`; doctors have read-only access to their own schedules.
- Weekly schedule updates/deletes take the same Doctor lock as booking and reject changes that would invalidate future active appointments.
- Doctor availability is derived only from weekly schedules, active doctor/service mappings, current time and existing appointments.
- Booking, rescheduling and patient follow-up creation share a pessimistic Doctor lock and recheck weekly schedule availability before writing.
- Public available-case calculation from weekly schedules and active appointments. The legacy `/available-slots` route name is retained, but each item represents a queue case rather than a fixed-duration slot.
- Public rolling 30-day doctor calendar calculated with one schedule query and one occupied-queue query for the requested range.
- Two cases per working session: morning estimates at `08:00` and `09:30`, afternoon estimates at `14:00` and `15:30`. Estimated starts are not guaranteed examination intervals.
- Patient online booking, self-cancellation and self-rescheduling use a single `05:00` clinic-time cutoff for the appointment date.
- Transactional patient appointment booking with BOOK audit history.
- PostgreSQL partial-unique-index protection for both concurrent doctor/date/session/queue conflicts and concurrent patient/date/session conflicts.
- Role-owned patient and doctor appointment queries plus system-wide admin queries.
- Appointment search, status/date/entity filters, pagination and clinic-timezone responses.
- Transactional patient cancellation before the `05:00` daily cutoff with CANCEL audit history; the cancelled record remains for audit while its doctor queue and patient-session reservation are released.
- Pessimistic appointment locking and PostgreSQL partial-index queue release verification.
- Transactional patient rescheduling before the old appointment date's `05:00` cutoff, with the target date cutoff also enforced.
- Same-appointment rescheduling with schedule validation and RESCHEDULE audit history.
- PostgreSQL partial-unique-index protection that keeps the losing appointment's old case intact during a concurrent reschedule race.
- New examinations start directly with doctor-owned `BOOKED → IN_PROGRESS`; no check-in endpoint is exposed.
- Historical `CHECKED_IN` appointments remain readable and may transition to `IN_PROGRESS` for backward compatibility.
- Per-doctor transactional locking prevents a second appointment from entering `IN_PROGRESS`; Ca 2 also waits until Ca 1 is terminal or absent.
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
- Admin dashboard summary reports unique patients who arrived today, doctor totals and today's appointment status counts using the clinic timezone.
- PostgreSQL integration tests cover the appointment lifecycle, transaction rollback and concurrent booking/rescheduling races.
- A PostgreSQL 18 schema-contract suite verifies the server major version, required extensions, Flyway V1–V18, all 16 active tables, queue/session/token constraints, Payment JSONB/state constraints, `vector(768)`, the HNSW cosine index and critical native constraints.
- OpenAPI 3 documentation exposes typed request/response schemas, reusable error responses and JWT Bearer authorization.
- Swagger UI and OpenAPI JSON are public in development and disabled by default in production.
- React clients are supported through an exact-origin CORS allowlist with credentialed requests for auth cookies.
- Unit, security and PostgreSQL integration tests run without H2, Docker or an alternate database.
- One persistent chatbot session per patient, paginated conversation history, history clearing and patient-only authorization.
- Gemini 3.5 Flash chat and `gemini-embedding-2` integration through Spring AI, disabled safely until explicit environment configuration is supplied.
- JSON dental knowledge ingestion, SHA-256 change detection, staged vector activation and scheduled/manual synchronization without an upload API.
- PostgreSQL pgvector cosine retrieval with top-K, similarity threshold, source/version and clinical-review metadata filters.
- Multi-intent Vietnamese conversation understanding for symptoms, service/doctor recommendations and available-case lookup in any order.
- Server-verified service, doctor and appointment-case cards; the model cannot create or book an appointment and never receives authority over database writes.
- SSE chatbot responses with deterministic lifecycle events, timeout/error handling and persistence only after a complete validated answer.
- Prompt-injection boundaries, source traceability, insufficient-knowledge fallback and output rejection for UUIDs, secret markers, JWTs and Google API-key shapes.

The React frontend includes a lazy-loaded chatbot widget for authenticated PATIENT accounts. ADMIN, DOCTOR and STAFF do not receive the widget or chatbot API access.

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
| `SERVICE_IMAGE_STORAGE_DIRECTORY` | No | Local service-image directory; defaults to `uploads/service-images` relative to the backend process |
| `SERVICE_IMAGE_MAX_FILE_SIZE_BYTES` | No | Application validation limit in bytes; defaults to `5242880` (5 MB) |
| `SERVICE_IMAGE_MAX_FILE_SIZE` | No | Spring multipart file limit; defaults to `5MB` |
| `SERVICE_IMAGE_MAX_REQUEST_SIZE` | No | Spring multipart request limit; defaults to `6MB` |
| `AUTH_COOKIE_SECURE` | No | `false` for local HTTP development; must be `true` on production HTTPS |
| `AUTH_COOKIE_SAME_SITE` | No | Refresh/CSRF cookie SameSite policy; defaults to `Lax` |
| `OPENAPI_ENABLED` | No | Enables `/v3/api-docs`; defaults to `false` in production |
| `SWAGGER_UI_ENABLED` | No | Enables Swagger UI; defaults to `false` in production |
| `AI_ENABLED` | No | Enables Gemini chat, embeddings, RAG synchronization and chatbot generation; defaults to `false` |
| `GEMINI_API_KEY` | When AI is enabled | Gemini credential supplied only through the runtime environment; never commit or log it |
| `GEMINI_CHAT_MODEL` | No | Chat model, defaults to `gemini-3.5-flash` |
| `GEMINI_EMBEDDING_MODEL` | No | Embedding model, defaults to `gemini-embedding-2` |
| `GEMINI_EMBEDDING_DIMENSION` | No | Must remain `768` to match `vector_store.embedding` |
| `GEMINI_MAX_OUTPUT_TOKENS` | No | Output budget, defaults to `4096`; truncated responses are rejected instead of persisted |
| `GEMINI_THINKING_BUDGET` | No | Reasoning budget, defaults to `256` so concise chatbot answers retain enough output capacity |
| `GEMINI_REQUEST_TIMEOUT` | No | Gemini request timeout, defaults to `60s` |
| `AI_STREAMING_TIMEOUT` | No | Maximum SSE response duration, defaults to `75s` |
| `GEMINI_RETRY_MAX_ATTEMPTS` | No | Total Gemini attempts, defaults to `2` (one initial request and at most one retry) |
| `KNOWLEDGE_BASE_DIRECTORY` | No | Directory containing the curated primary JSON dataset; defaults to `../knowledge-base` |
| `AI_RAG_SYNC_CRON` | No | Dataset change-check schedule, defaults to every 15 minutes |
| `AI_RAG_SYNC_BATCH_SIZE` | No | Number of chunks committed per embedding batch, defaults to `30` and must not exceed `30` |
| `AI_RAG_SYNC_BATCH_DELAY` | No | Delay between embedding batches, defaults to `21s` |
| `AI_RAG_DEFAULT_TOP_K` | No | Default retrieval result limit, defaults to `3` |
| `AI_RAG_SIMILARITY_THRESHOLD` | No | Minimum cosine similarity, defaults to `0.55` |

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

Flyway applies [V1__init_schema.sql](./src/main/resources/db/migration/V1__init_schema.sql) through [V18__enforce_appointment_queue_contract.sql](./src/main/resources/db/migration/V18__enforce_appointment_queue_contract.sql). V6 backfills doctors with no weekly schedule. V7 removes the obsolete doctor-day-off table only when it is empty. V8 similarly removes `medical_record_drafts` only when it is empty. V9 and V10 normalize/repair legacy schedule times. V11 adds `dental_services.image_url`. V12 enforces one chatbot session per user. V13 and V14 add knowledge-source synchronization state. V15 expands Appointment with `appointment_date`, `session` and `queue_number`, safely backfills compatible legacy rows and adds partial unique indexes for the doctor queue and patient session. V16 standardizes all weekly schedules to Monday-Saturday `08:00-11:00` and `14:00-17:00`. V17 allows interrupted knowledge embedding synchronization to resume safely. V18 refuses to run while any Appointment has an incomplete queue model, then makes all three queue columns mandatory and removes the legacy active-slot index based on `scheduled_start_at`. The `scheduled_start_at` column remains the compatibility timestamp containing the estimated start. Hibernate only validates the schema through `ddl-auto=validate`.

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
| 400 | `INVALID_REQUEST`, `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `PASSWORD_TOO_LONG`, `INVALID_IMAGE_FILE` |
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED`, `SESSION_REVOKED`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REUSED` |
| 403 | `ACCOUNT_INACTIVE`, `ACCESS_DENIED`, `CSRF_VALIDATION_FAILED` |
| 423 | `SESSION_LOCKED` |
| 404 | `RESOURCE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `RESOURCE_CONFLICT`, `EMAIL_ALREADY_EXISTS`, `LICENSE_NUMBER_ALREADY_EXISTS`, `SERVICE_NAME_ALREADY_EXISTS`, `SCHEDULE_ALREADY_EXISTS` |
| 413 | `IMAGE_FILE_TOO_LARGE` |
| 500 | `INTERNAL_SERVER_ERROR`, `IMAGE_STORAGE_FAILED` |

## Current APIs

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/activity`
- `POST /api/auth/unlock`
- `GET /api/auth/session`
- `GET /api/chat/session` (PATIENT; the current user's single session)
- `GET /api/chat/messages?page=&size=` (PATIENT)
- `POST /api/chat/messages` (PATIENT; non-streaming fallback)
- `POST /api/chat/messages/stream` (PATIENT; `text/event-stream`)
- `DELETE /api/chat/messages` (PATIENT; clears only the current user's history)
- `GET /api/admin/ai/knowledge/status` (ADMIN)
- `POST /api/admin/ai/knowledge/sync` (ADMIN)
- `POST /api/admin/ai/knowledge/search-test` (ADMIN)
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
- `GET /api/services/images/{filename}`
- `POST /api/admin/services`
- `POST /api/admin/services/images` (`multipart/form-data`, field `file`)
- `PUT /api/admin/services/{id}`
- `PATCH /api/admin/services/{id}/status`
- `GET /api/doctors/{doctorId}/services`
- `GET /api/doctor/services` (DOCTOR; active services assigned to the authenticated doctor; no fixed `durationMinutes` is returned because examination duration is flexible)
- `GET /api/doctors/{doctorId}/schedule`
- `GET /api/doctors/{doctorId}/available-slots?serviceId={serviceId}&date={yyyy-MM-dd}` (route name retained for compatibility; returns available queue cases)
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

### Two-case scheduling contract

- `MORNING` covers `08:00-11:00`: Ca 1 is estimated at `08:00`, Ca 2 at `09:30`.
- `AFTERNOON` covers `14:00-17:00`: Ca 1 is estimated at `14:00`, Ca 2 at `15:30`.
- `estimatedStartTime`/`estimatedStartAt` tell the patient when to arrive; they are not a guaranteed start or fixed examination duration.
- The booking and reschedule request identifies a case with `appointmentDate`, `session` and `queueNumber` (`1` or `2`).
- For a given day/session, one doctor queue position can have only one active Appointment and one Patient can have only one active Appointment, regardless of doctor or service.
- `CANCELLED`, `COMPLETED` and `NO_SHOW` are terminal and do not reserve the active doctor queue or patient session. A cancelled Appointment remains in PostgreSQL for audit.
- Online booking, self-cancellation and self-rescheduling must occur before `05:00` on the relevant appointment date in `Asia/Ho_Chi_Minh`.

Example booking request:

```json
{
  "doctorId": "<DOCTOR_ID>",
  "serviceId": "<SERVICE_ID>",
  "appointmentDate": "2026-11-10",
  "session": "MORNING",
  "queueNumber": 2,
  "previousAppointmentId": null
}
```

### Doctor calendar response example

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "doctorId": "<DOCTOR_ID>",
    "serviceId": "<SERVICE_ID>",
    "fromDate": "2026-11-10",
    "toDate": "2026-12-05",
    "days": [
      {
        "date": "2026-11-10",
        "dayOfWeek": 2,
        "status": "AVAILABLE",
        "sessions": [
          {
            "session": "MORNING",
            "status": "AVAILABLE",
            "availableSlotCount": 2,
            "availableSlots": [
              {
                "queueNumber": 1,
                "estimatedStartTime": "08:00:00",
                "startAt": "2026-11-10T08:00:00+07:00",
                "endAt": "2026-11-10T11:00:00+07:00"
              },
              {
                "queueNumber": 2,
                "estimatedStartTime": "09:30:00",
                "startAt": "2026-11-10T09:30:00+07:00",
                "endAt": "2026-11-10T11:00:00+07:00"
              }
            ]
          },
          {
            "session": "AFTERNOON",
            "status": "AVAILABLE",
            "availableSlotCount": 2,
            "availableSlots": [
              {
                "queueNumber": 1,
                "estimatedStartTime": "14:00:00",
                "startAt": "2026-11-10T14:00:00+07:00",
                "endAt": "2026-11-10T17:00:00+07:00"
              },
              {
                "queueNumber": 2,
                "estimatedStartTime": "15:30:00",
                "startAt": "2026-11-10T15:30:00+07:00",
                "endAt": "2026-11-10T17:00:00+07:00"
              }
            ]
          }
        ]
      }
    ]
  }
}
```

The calendar keeps the existing `availableSlots`, `startAt` and `endAt` field names for API compatibility. Each array item is a queue case: `startAt` is its estimated start and `endAt` is the enclosing session end, not a guaranteed examination interval.

## AI chatbot and RAG operation

The chatbot is off by default. Normal backend, Flyway and non-AI APIs remain available with `AI_ENABLED=false`; chatbot generation and embedding operations return the standard AI-unavailable contract instead of silently using fabricated data.

To enable it in a local shell, configure a valid key outside source control and start the backend from `backend`:

```powershell
$env:AI_ENABLED = 'true'
# Set GEMINI_API_KEY through the terminal/deployment secret store without committing it.
mvn spring-boot:run
```

The managed RAG datasets are `knowledge-base/nidcr_dental_knowledge_vi.json` and `knowledge-base/vietnamese_dental_qa_rag.json`. The first contains 16 curated Vietnamese dental records with NIDCR source URLs and `PENDING_CLINICAL_REVIEW` metadata. The second contains 639 deduplicated Vietnamese dental Q&A records filtered from public medical QA datasets and is marked `AUTOMATED_FILTERED_DEMO_NOT_CLINICALLY_REVIEWED`; it is suitable for the graduation-project demo, not as clinically reviewed guidance. The accompanying CSV, raw Parquet files and processing report are research/reference artifacts and are not promoted automatically. Synchronization:

1. discovers only the configured, supported JSON dataset file names directly under `KNOWLEDGE_BASE_DIRECTORY`;
2. validates UTF-8, size, duplicate JSON keys, the profile-specific dataset/record identity, schema and required source metadata;
3. skips unchanged content using SHA-256;
4. chunks each source deterministically and embeds at most 30 missing records per provider request;
5. commits every completed batch independently and records resumable progress without exposing an incomplete source to retrieval;
6. resumes from stored record IDs on the next scheduled or manual sync after quota/rate-limit failure;
7. atomically activates the new source version only after every expected chunk exists, then removes older vector versions;
8. preserves the previous active version when parsing, embedding or persistence fails.

The Free Tier counts each embedded text against both per-minute and per-day quotas, even when several texts share one HTTP request. Synchronization therefore commits at most 30 texts every 21 seconds. If the daily quota ends partway through the 639-record dataset, completed batches remain staged and the next run continues with only the missing records. Staged chunks are not searchable until all 639 records are present and the source version is activated.

Use the ADMIN status and search-test APIs to verify active source/version, similarity score and provenance before enabling the feature for patients. There is intentionally no file upload endpoint: curated source files are deployed and reviewed through the repository/deployment process. Do not treat `PENDING_CLINICAL_REVIEW` content as a substitute for professional diagnosis.

Successful SSE event order is `started` → one or more `token` → `metadata` → `completed`. `metadata` carries source links and only backend-verified service, doctor and appointment-case cards. Each available case contains `session`, `queueNumber`, the session window and `estimatedStartTime`/`estimatedStartAt`; it does not expose a fixed slot end time. The estimated time is not guaranteed because the actual examination can start earlier or later depending on the preceding case. An `error` event terminates a failed or timed-out stream. The chatbot never creates an Appointment; selection and final confirmation remain in the existing patient booking flow.

## React integration notes

- Configure the frontend API base URL as `http://localhost:8081` in development.
- Keep the Access Token in React memory and send it with `Authorization: Bearer <token>`; do not place either token in `localStorage`.
- Send auth-cookie requests with credentials. Read `XSRF-TOKEN` and mirror it in `X-XSRF-TOKEN` for refresh, logout and unlock.
- If React and the API use different subdomains, configure `AUTH_COOKIE_DOMAIN` for their shared parent domain; prefer a same-origin reverse proxy in production.
- Only explicit user interaction should call `/api/auth/activity`; background refresh and other background requests must not keep an idle ADMIN/DOCTOR/STAFF session active.
- Configure `CORS_ALLOWED_ORIGINS` with exact React origins. Wildcard origins are not used.
- To upload a service image, send an ADMIN-authenticated multipart request to `/api/admin/services/images`, then use the returned `imageUrl` in the service create/update payload.
- Treat `401` as an expired/missing session, `403` as insufficient permission and `409` as a recoverable business conflict.
- A cancelled appointment may include `cancellationReason`, `cancellationSource` and `cancelledAt`. Treat an existing `cancellationSource` as immutable historical audit data; a terminal Appointment no longer reserves its doctor queue or patient session.
- Parse timestamps as ISO-8601 values. Appointment display and day-based queries follow `Asia/Ho_Chi_Minh`.
- Load the booking month with `/api/doctors/{doctorId}/calendar`. Render the returned `days` directly, then submit the selected case's date, `session` and `queueNumber`; do not derive a fixed duration from `startAt`/`endAt`. The API returns at most the rolling 30-day window; refresh it when the clinic date changes.
- The calendar session status is `AVAILABLE`, `FULL`, `NOT_WORKING` or `UNAVAILABLE`. `NOT_WORKING` means no matching weekly schedule (including Sunday); `FULL` means both queue cases are occupied; `UNAVAILABLE` means no case remains bookable because the `05:00` cutoff or another booking rule applies.
- Show the patient that the estimated start may move with the preceding examination and recommend arrival about 15 minutes early.
- Use the documented `ApiResponse` envelope and zero-based pagination contract instead of relying on JPA entity shapes.
- Send only `serviceId` and `quantity` when a doctor updates Payment items. Never send or calculate prices as trusted frontend data.
- A completed examination exposes an UNPAID payment until Staff, or Admin as backup, confirms actual receipt at the counter. `BANK_TRANSFER` is manual confirmation, not an online gateway.
- Normal booking omits `previousAppointmentId`. A patient books a recommended follow-up through the same endpoint with the completed appointment ID in `previousAppointmentId`.
- Revenue belongs to the clinic date containing `paidAt`, which may differ from the examination date.
- Render the chatbot only for an authenticated PATIENT. Fetch the current session/history from `/api/chat`, consume SSE events in order and keep the Access Token in memory.
- Treat service, doctor and appointment-case cards from SSE `metadata` as the authoritative navigation data. Do not parse IDs, prices or queue cases from generated prose.
- An appointment-case card may prefill the existing booking page, but the patient must still confirm the booking and the Backend revalidates availability and locking.

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

The full frontend gates run from `frontend`:

```text
npm test
npm run build
npm run test:e2e
```

The browser E2E suite uses mocked HTTP/SSE responses to verify UI integration deterministically; it does not call Gemini. Unit and PostgreSQL integration tests cover RAG ranking/filtering, source traceability, one-session ownership, role security, prompt-injection boundaries, secret-shaped output rejection and streaming completion/error behavior. A live Gemini smoke test is intentionally separate because it requires `AI_ENABLED=true`, a valid external key and network availability.
