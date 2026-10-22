package com.dentalclinic.persistence;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@JdbcTest
@ActiveProfiles("development")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgreSql18SchemaContractIntegrationTest {

    private static final Instant APPOINTMENT_START = Instant.parse("2031-01-06T01:00:00Z");

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PostgreSql18SchemaContractIntegrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void shouldUsePostgreSql18WithRequiredExtensionsAndVectorSchema() {
        Integer serverVersionNumber = jdbcTemplate.queryForObject(
                "select current_setting('server_version_num')::integer",
                Integer.class
        );
        List<String> extensions = jdbcTemplate.queryForList(
                """
                        select extname
                        from pg_extension
                        where extname in ('vector', 'uuid-ossp')
                        order by extname
                        """,
                String.class
        );
        List<String> businessTables = jdbcTemplate.queryForList(
                """
                        select table_name
                        from information_schema.tables
                        where table_schema = 'public'
                          and table_type = 'BASE TABLE'
                          and table_name <> 'flyway_schema_history'
                        order by table_name
                        """,
                String.class
        );
        String embeddingType = jdbcTemplate.queryForObject(
                """
                        select format_type(attribute.atttypid, attribute.atttypmod)
                        from pg_attribute attribute
                        join pg_class relation on relation.oid = attribute.attrelid
                        join pg_namespace namespace on namespace.oid = relation.relnamespace
                        where namespace.nspname = 'public'
                          and relation.relname = 'vector_store'
                          and attribute.attname = 'embedding'
                          and not attribute.attisdropped
                        """,
                String.class
        );
        Map<String, Object> vectorIndex = jdbcTemplate.queryForMap(
                """
                        select access_method.amname as access_method,
                               index_metadata.indisvalid as valid,
                               pg_get_indexdef(index_relation.oid) as definition
                        from pg_class index_relation
                        join pg_index index_metadata on index_metadata.indexrelid = index_relation.oid
                        join pg_am access_method on access_method.oid = index_relation.relam
                        where index_relation.relname = 'ix_vector_store_embedding_hnsw'
                        """
        );
        Boolean migrationSucceeded = jdbcTemplate.queryForObject(
                """
                        select success
                        from flyway_schema_history
                        where version = '1'
                        """,
                Boolean.class
        );

        assertThat(serverVersionNumber).isBetween(180000, 189999);
        assertThat(extensions).containsExactly("uuid-ossp", "vector");
        assertThat(businessTables).containsExactly(
                "appointment_histories",
                "appointments",
                "auth_sessions",
                "chat_messages",
                "chat_sessions",
                "dental_services",
                "doctor_days_off",
                "doctor_schedules",
                "doctor_services",
                "doctors",
                "medical_record_drafts",
                "medical_records",
                "patients",
                "payments",
                "refresh_tokens",
                "users",
                "vector_store"
        );
        assertThat(embeddingType).isEqualTo("vector(768)");
        assertThat(vectorIndex.get("access_method")).isEqualTo("hnsw");
        assertThat(vectorIndex.get("valid")).isEqualTo(true);
        assertThat(vectorIndex.get("definition").toString()).contains("vector_cosine_ops");
        assertThat(migrationSucceeded).isTrue();
        Boolean dayOffMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '2'",
                Boolean.class
        );
        Integer dayOffUniqueConstraint = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_constraint
                        where conname = 'uq_doctor_days_off_doctor_date_session'
                          and contype = 'u'
                        """,
                Integer.class
        );
        String dayOffSessionType = jdbcTemplate.queryForObject(
                """
                        select format_type(attribute.atttypid, attribute.atttypmod)
                        from pg_attribute attribute
                        join pg_class relation on relation.oid = attribute.attrelid
                        join pg_namespace namespace on namespace.oid = relation.relnamespace
                        where namespace.nspname = 'public'
                          and relation.relname = 'doctor_days_off'
                          and attribute.attname = 'session'
                          and not attribute.attisdropped
                        """,
                String.class
        );
        assertThat(dayOffMigrationSucceeded).isTrue();
        assertThat(dayOffUniqueConstraint).isEqualTo(1);
        assertThat(dayOffSessionType).isEqualTo("session_enum");

        Boolean paymentMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '3'",
                Boolean.class
        );
        Integer paymentUniqueConstraint = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_constraint
                        where conname = 'uq_payments_appointment'
                          and contype = 'u'
                        """,
                Integer.class
        );
        String paymentStatusType = jdbcTemplate.queryForObject(
                """
                        select format_type(attribute.atttypid, attribute.atttypmod)
                        from pg_attribute attribute
                        join pg_class relation on relation.oid = attribute.attrelid
                        join pg_namespace namespace on namespace.oid = relation.relnamespace
                        where namespace.nspname = 'public'
                          and relation.relname = 'payments'
                          and attribute.attname = 'status'
                          and not attribute.attisdropped
                        """,
                String.class
        );
        Integer paymentUpdatedAtTrigger = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_trigger trigger_metadata
                        join pg_class relation on relation.oid = trigger_metadata.tgrelid
                        where relation.relname = 'payments'
                          and trigger_metadata.tgname = 'trg_payments_updated_at'
                          and not trigger_metadata.tgisinternal
                        """,
                Integer.class
        );
        assertThat(paymentMigrationSucceeded).isTrue();
        assertThat(paymentUniqueConstraint).isEqualTo(1);
        assertThat(paymentStatusType).isEqualTo("payment_status_enum");
        assertThat(paymentUpdatedAtTrigger).isEqualTo(1);

        Boolean phase263MigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '4'",
                Boolean.class
        );
        List<String> roles = jdbcTemplate.queryForList(
                "select unnest(enum_range(null::role_enum))::text",
                String.class
        );
        Integer activeFollowUpIndex = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_indexes
                        where schemaname = 'public'
                          and indexname = 'ux_appointments_active_follow_up_previous'
                        """,
                Integer.class
        );
        assertThat(phase263MigrationSucceeded).isTrue();
        assertThat(roles).containsExactly("ADMIN", "DOCTOR", "PATIENT", "STAFF");
        assertThat(activeFollowUpIndex).isEqualTo(1);

        Boolean phase264MigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '5'",
                Boolean.class
        );
        List<String> sessionStatuses = jdbcTemplate.queryForList(
                "select unnest(enum_range(null::auth_session_status_enum))::text",
                String.class
        );
        Integer currentRefreshTokenIndex = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_class index_relation
                        join pg_index index_metadata
                          on index_metadata.indexrelid = index_relation.oid
                        where index_relation.relname = 'ux_refresh_tokens_session_current'
                          and index_metadata.indisunique
                          and index_metadata.indpred is not null
                        """,
                Integer.class
        );
        Integer draftObjectConstraint = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_constraint
                        where conname = 'ck_medical_record_drafts_object'
                          and contype = 'c'
                        """,
                Integer.class
        );
        assertThat(phase264MigrationSucceeded).isTrue();
        assertThat(sessionStatuses).containsExactly("ACTIVE", "LOCKED", "REVOKED");
        assertThat(currentRefreshTokenIndex).isEqualTo(1);
        assertThat(draftObjectConstraint).isEqualTo(1);

        Boolean defaultScheduleBackfillSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '6'",
                Boolean.class
        );
        assertThat(defaultScheduleBackfillSucceeded).isTrue();
    }

    @Test
    void shouldRequirePaymentItemsToBeJsonArray() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "IN_PROGRESS"
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        """
                                insert into payments (
                                    appointment_id,
                                    items,
                                    total_amount,
                                    status
                                ) values (?, ?::jsonb, 0, 'DRAFT')
                                """,
                        appointmentId,
                        "{}"
                ),
                "23514",
                "ck_payments_items_array"
        );
    }

    @Test
    void shouldRequirePaidPaymentMetadata() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "COMPLETED"
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        """
                                insert into payments (
                                    appointment_id,
                                    items,
                                    total_amount,
                                    status
                                ) values (?, ?::jsonb, 250000, 'PAID')
                                """,
                        appointmentId,
                        "[{\"serviceId\":\"" + fixture.serviceId() + "\"}]"
                ),
                "23514",
                "ck_payments_paid_metadata"
        );
    }

    @Test
    void shouldAllowOnlyOnePaymentPerAppointment() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "IN_PROGRESS"
        );
        jdbcTemplate.update(
                "insert into payments (appointment_id) values (?)",
                appointmentId
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "insert into payments (appointment_id) values (?)",
                        appointmentId
                ),
                "23505",
                "uq_payments_appointment"
        );
    }

    @Test
    void shouldEnforceDoctorServiceCompositeForeignKey() {
        Fixture fixture = createFixture();
        UUID unmappedServiceId = insertService("Dịch vụ chưa gán " + fixture.marker());

        assertConstraintViolation(
                () -> insertAppointment(
                        fixture.patientId(),
                        fixture.doctorId(),
                        unmappedServiceId,
                        APPOINTMENT_START,
                        "BOOKED"
                ),
                "23503",
                "fk_appointments_doctor_service"
        );
    }

    @Test
    void shouldReleaseCancelledSlotAndRejectASecondActiveAppointment() {
        Fixture fixture = createFixture();
        insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "CANCELLED"
        );
        insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "BOOKED"
        );

        Integer appointmentsAtSlot = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from appointments
                        where doctor_id = ? and scheduled_start_at = ?
                        """,
                Integer.class,
                fixture.doctorId(),
                Timestamp.from(APPOINTMENT_START)
        );
        assertThat(appointmentsAtSlot).isEqualTo(2);

        assertConstraintViolation(
                () -> insertAppointment(
                        fixture.patientId(),
                        fixture.doctorId(),
                        fixture.serviceId(),
                        APPOINTMENT_START,
                        "CHECKED_IN"
                ),
                "23505",
                "ux_appointments_doctor_start_not_cancelled"
        );
    }

    @Test
    void shouldRejectAppointmentWhoseActualEndPrecedesActualStart() {
        Fixture fixture = createFixture();

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        """
                                insert into appointments (
                                    patient_id,
                                    doctor_id,
                                    service_id,
                                    scheduled_start_at,
                                    status,
                                    actual_start_at,
                                    actual_end_at
                                ) values (?, ?, ?, ?, 'COMPLETED', ?, ?)
                                """,
                        fixture.patientId(),
                        fixture.doctorId(),
                        fixture.serviceId(),
                        Timestamp.from(APPOINTMENT_START),
                        Timestamp.from(APPOINTMENT_START.plusSeconds(3_600)),
                        Timestamp.from(APPOINTMENT_START.plusSeconds(1_800))
                ),
                "23514",
                "ck_appointments_actual_time_order"
        );
    }

    @Test
    void shouldRejectSelfReferencedPreviousAppointment() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "BOOKED"
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "update appointments set previous_appointment_id = ? where id = ?",
                        appointmentId,
                        appointmentId
                ),
                "23514",
                "ck_appointments_previous_not_self"
        );
    }

    @Test
    void shouldRequireAppointmentHistoryDetailsToBeJsonObject() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "BOOKED"
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        """
                                insert into appointment_histories (
                                    appointment_id,
                                    action,
                                    changed_by,
                                    details
                                ) values (?, 'BOOK', ?, ?::jsonb)
                                """,
                        appointmentId,
                        fixture.patientUserId(),
                        "[]"
                ),
                "23514",
                "ck_appointment_histories_details_object"
        );
    }

    @Test
    void shouldRequirePrescriptionToBeJsonArray() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "IN_PROGRESS"
        );

        assertConstraintViolation(
                () -> insertMedicalRecord(appointmentId, false, null, null, "{}"),
                "23514",
                "ck_medical_records_prescription_array"
        );
    }

    @Test
    void shouldRequireFollowUpDateOrNoteWhenFollowUpIsRequired() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "IN_PROGRESS"
        );

        assertConstraintViolation(
                () -> insertMedicalRecord(appointmentId, true, null, null, "[]"),
                "23514",
                "ck_medical_records_follow_up"
        );
    }

    @Test
    void shouldRequireFollowUpDateEvenWhenFollowUpNoteExists() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "IN_PROGRESS"
        );

        assertConstraintViolation(
                () -> insertMedicalRecord(
                        appointmentId,
                        true,
                        null,
                        "Có ghi chú nhưng chưa có ngày",
                        "[]"
                ),
                "23514",
                "ck_medical_records_follow_up_date_required"
        );
    }

    @Test
    void shouldAllowOnlyOneMedicalRecordPerAppointment() {
        Fixture fixture = createFixture();
        UUID appointmentId = insertAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "IN_PROGRESS"
        );
        insertMedicalRecord(appointmentId, false, null, null, "[]");

        assertConstraintViolation(
                () -> insertMedicalRecord(appointmentId, false, null, null, "[]"),
                "23505",
                "uq_medical_records_appointment"
        );
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        UUID patientUserId = insertUser(
                "phase25-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 25",
                "PATIENT"
        );
        UUID patientId = jdbcTemplate.queryForObject(
                "insert into patients (user_id) values (?) returning id",
                UUID.class,
                patientUserId
        );
        UUID doctorUserId = insertUser(
                "phase25-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 25",
                "DOCTOR"
        );
        UUID doctorId = jdbcTemplate.queryForObject(
                """
                        insert into doctors (
                            user_id,
                            specialization,
                            license_number,
                            rating,
                            experience_years,
                            working_status
                        ) values (?, ?, ?, 4.5, 8, 'ACTIVE')
                        returning id
                        """,
                UUID.class,
                doctorUserId,
                "Nha khoa tổng quát",
                "PHASE25-" + marker
        );
        UUID serviceId = insertService("Dịch vụ Phase 25 " + marker);
        jdbcTemplate.update(
                "insert into doctor_services (doctor_id, service_id) values (?, ?)",
                doctorId,
                serviceId
        );
        return new Fixture(marker, patientUserId, patientId, doctorId, serviceId);
    }

    private UUID insertUser(String email, String fullName, String role) {
        return jdbcTemplate.queryForObject(
                """
                        insert into users (email, password_hash, full_name, role, status)
                        values (?, ?, ?, ?::role_enum, 'ACTIVE')
                        returning id
                        """,
                UUID.class,
                email,
                "$2a$10$phase25.schema.contract.hash",
                fullName,
                role
        );
    }

    private UUID insertService(String name) {
        return jdbcTemplate.queryForObject(
                """
                        insert into dental_services (name, description, price, is_active)
                        values (?, ?, 250000, true)
                        returning id
                        """,
                UUID.class,
                name,
                "Dịch vụ kiểm thử constraint PostgreSQL 18"
        );
    }

    private UUID insertAppointment(
            UUID patientId,
            UUID doctorId,
            UUID serviceId,
            Instant scheduledStartAt,
            String status
    ) {
        return jdbcTemplate.queryForObject(
                """
                        insert into appointments (
                            patient_id,
                            doctor_id,
                            service_id,
                            scheduled_start_at,
                            status
                        ) values (?, ?, ?, ?, ?::appointment_status_enum)
                        returning id
                        """,
                UUID.class,
                patientId,
                doctorId,
                serviceId,
                Timestamp.from(scheduledStartAt),
                status
        );
    }

    private void insertMedicalRecord(
            UUID appointmentId,
            boolean followUpRequired,
            Object recommendedFollowUpDate,
            String followUpNote,
            String prescription
    ) {
        jdbcTemplate.update(
                """
                        insert into medical_records (
                            appointment_id,
                            diagnosis,
                            treatment,
                            follow_up_required,
                            recommended_follow_up_date,
                            follow_up_note,
                            prescription
                        ) values (?, ?, ?, ?, ?, ?, ?::jsonb)
                        """,
                appointmentId,
                "Chẩn đoán Phase 25",
                "Điều trị Phase 25",
                followUpRequired,
                recommendedFollowUpDate,
                followUpNote,
                prescription
        );
    }

    private void assertConstraintViolation(
            ThrowingCallable operation,
            String expectedSqlState,
            String expectedConstraint
    ) {
        Throwable failure = catchThrowable(operation);

        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat(sqlException.getSQLState()).isEqualTo(expectedSqlState);
        assertThat(sqlException.getMessage()).contains(expectedConstraint);
    }

    private SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        throw new AssertionError("Không tìm thấy SQLException trong chuỗi nguyên nhân", failure);
    }

    private record Fixture(
            String marker,
            UUID patientUserId,
            UUID patientId,
            UUID doctorId,
            UUID serviceId
    ) {
    }
}
