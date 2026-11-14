package com.dentalclinic.persistence;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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
                "doctor_schedules",
                "doctor_services",
                "doctors",
                "knowledge_sources",
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
        Boolean dayOffRemovalMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '7'",
                Boolean.class
        );
        Integer dayOffTableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'public'
                          and table_name = 'doctor_days_off'
                        """,
                Integer.class
        );
        assertThat(dayOffMigrationSucceeded).isTrue();
        assertThat(dayOffRemovalMigrationSucceeded).isTrue();
        assertThat(dayOffTableCount).isZero();

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
        assertThat(phase264MigrationSucceeded).isTrue();
        assertThat(sessionStatuses).containsExactly("ACTIVE", "LOCKED", "REVOKED");
        assertThat(currentRefreshTokenIndex).isEqualTo(1);

        Boolean defaultScheduleBackfillSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '6'",
                Boolean.class
        );
        assertThat(defaultScheduleBackfillSucceeded).isTrue();

        Boolean draftRemovalMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '8'",
                Boolean.class
        );
        Integer draftTableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'public'
                          and table_name = 'medical_record_drafts'
                        """,
                Integer.class
        );
        assertThat(draftRemovalMigrationSucceeded).isTrue();
        assertThat(draftTableCount).isZero();

        Boolean legacyMorningScheduleMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '9'",
                Boolean.class
        );
        Integer legacyMorningScheduleCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from doctor_schedules
                        where session = 'MORNING'
                          and start_time = time '08:00'
                          and end_time = time '12:00'
                        """,
                Integer.class
        );
        assertThat(legacyMorningScheduleMigrationSucceeded).isTrue();
        assertThat(legacyMorningScheduleCount).isZero();

        Boolean scheduleLocalTimeRepairSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '10'",
                Boolean.class
        );
        Integer unsupportedScheduleCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from doctor_schedules
                        where day_of_week not between 1 and 6
                           or (session = 'MORNING'
                               and (start_time <> time '08:00' or end_time <> time '11:00'))
                           or (session = 'AFTERNOON'
                               and (start_time <> time '14:00' or end_time <> time '17:00'))
                        """,
                Integer.class
        );
        assertThat(scheduleLocalTimeRepairSucceeded).isTrue();
        assertThat(unsupportedScheduleCount).isZero();

        Boolean dentalServiceImageMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '11'",
                Boolean.class
        );
        Map<String, Object> imageUrlColumn = jdbcTemplate.queryForMap(
                """
                        select data_type, character_maximum_length, is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'dental_services'
                          and column_name = 'image_url'
                        """
        );
        assertThat(dentalServiceImageMigrationSucceeded).isTrue();
        assertThat(imageUrlColumn.get("data_type")).isEqualTo("character varying");
        assertThat(imageUrlColumn.get("character_maximum_length")).isEqualTo(1000);
        assertThat(imageUrlColumn.get("is_nullable")).isEqualTo("YES");

        Boolean knowledgeSourceMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '13'",
                Boolean.class
        );
        List<String> knowledgeSourceStatuses = jdbcTemplate.queryForList(
                "select unnest(enum_range(null::knowledge_source_status_enum))::text",
                String.class
        );
        Integer knowledgeSourceTrigger = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_trigger trigger_metadata
                        join pg_class relation on relation.oid = trigger_metadata.tgrelid
                        where relation.relname = 'knowledge_sources'
                          and trigger_metadata.tgname = 'trg_knowledge_sources_updated_at'
                          and not trigger_metadata.tgisinternal
                        """,
                Integer.class
        );
        Integer sourceKeyUniqueConstraint = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_constraint
                        where conname = 'uq_knowledge_sources_key'
                          and contype = 'u'
                        """,
                Integer.class
        );
        Boolean knowledgeSyncMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '14'",
                Boolean.class
        );
        Map<String, Object> syncTokenColumn = jdbcTemplate.queryForMap(
                """
                        select data_type, is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'knowledge_sources'
                          and column_name = 'sync_token'
                        """
        );
        assertThat(knowledgeSourceMigrationSucceeded).isTrue();
        assertThat(knowledgeSyncMigrationSucceeded).isTrue();
        assertThat(knowledgeSourceStatuses)
                .containsExactly("PENDING", "SYNCING", "SUCCESS", "FAILED");
        assertThat(knowledgeSourceTrigger).isEqualTo(1);
        assertThat(sourceKeyUniqueConstraint).isEqualTo(1);
        assertThat(syncTokenColumn.get("data_type")).isEqualTo("uuid");
        assertThat(syncTokenColumn.get("is_nullable")).isEqualTo("YES");

        Boolean appointmentQueueMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '15'",
                Boolean.class
        );
        Boolean appointmentQueueContractMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '18'",
                Boolean.class
        );
        List<Map<String, Object>> queueColumns = jdbcTemplate.queryForList(
                """
                        select column_name, data_type, udt_name, is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'appointments'
                          and column_name in ('appointment_date', 'session', 'queue_number')
                        order by column_name
                        """
        );
        List<String> queueConstraints = jdbcTemplate.queryForList(
                """
                        select conname
                        from pg_constraint
                        where conrelid = 'appointments'::regclass
                          and conname in (
                              'ck_appointments_queue_number',
                              'ck_appointments_queue_model_complete'
                          )
                        order by conname
                        """,
                String.class
        );
        List<String> queueIndexes = jdbcTemplate.queryForList(
                """
                        select indexname
                        from pg_indexes
                        where schemaname = 'public'
                          and indexname in (
                              'ux_appointments_doctor_start_active_legacy',
                              'ux_appointments_doctor_queue_active',
                              'ux_appointments_patient_session_active'
                          )
                        order by indexname
                        """,
                String.class
        );
        Integer oldDoctorStartIndexCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_indexes
                        where schemaname = 'public'
                          and indexname = 'ux_appointments_doctor_start_not_cancelled'
                        """,
                Integer.class
        );

        assertThat(appointmentQueueMigrationSucceeded).isTrue();
        assertThat(appointmentQueueContractMigrationSucceeded).isTrue();
        assertThat(queueColumns)
                .extracting(
                        row -> row.get("column_name"),
                        row -> row.get("data_type"),
                        row -> row.get("udt_name"),
                        row -> row.get("is_nullable")
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "appointment_date", "date", "date", "NO"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "queue_number", "smallint", "int2", "NO"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "session", "USER-DEFINED", "session_enum", "NO"
                        )
                );
        assertThat(queueConstraints).containsExactly("ck_appointments_queue_number");
        assertThat(queueIndexes).containsExactly(
                "ux_appointments_doctor_queue_active",
                "ux_appointments_patient_session_active"
        );
        assertThat(oldDoctorStartIndexCount).isZero();

        Boolean standardScheduleMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '16'",
                Boolean.class
        );
        String dayOfWeekConstraint = jdbcTemplate.queryForObject(
                """
                        select pg_get_constraintdef(oid)
                        from pg_constraint
                        where conrelid = 'doctor_schedules'::regclass
                          and conname = 'ck_doctor_schedules_day_of_week'
                        """,
                String.class
        );
        assertThat(standardScheduleMigrationSucceeded).isTrue();
        assertThat(dayOfWeekConstraint).contains("day_of_week >= 1", "day_of_week <= 6");

        Boolean resumableKnowledgeSyncMigrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '17'",
                Boolean.class
        );
        List<String> knowledgeProgressColumns = jdbcTemplate.queryForList(
                """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'knowledge_sources'
                          and column_name in (
                              'pending_content_hash',
                              'processed_chunk_count',
                              'total_chunk_count'
                          )
                        order by column_name
                        """,
                String.class
        );
        Integer resumableVectorIndexCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_indexes
                        where schemaname = 'public'
                          and indexname = 'ux_vector_store_source_version_record'
                        """,
                Integer.class
        );
        assertThat(resumableKnowledgeSyncMigrationSucceeded).isTrue();
        assertThat(knowledgeProgressColumns).containsExactly(
                "pending_content_hash",
                "processed_chunk_count",
                "total_chunk_count"
        );
        assertThat(resumableVectorIndexCount).isEqualTo(1);
    }

    @Test
    void shouldExposeWalkInPersistenceContractWithoutLosingExistingData() {
        Boolean migrationSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '19'",
                Boolean.class
        );
        Boolean patientNameContractSucceeded = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '20'",
                Boolean.class
        );
        Map<String, Object> patientUserColumn = jdbcTemplate.queryForMap(
                """
                        select is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'patients'
                          and column_name = 'user_id'
                        """
        );
        Map<String, Object> patientNameColumn = jdbcTemplate.queryForMap(
                """
                        select data_type, character_maximum_length, is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'patients'
                          and column_name = 'full_name'
                        """
        );
        String patientUserForeignKey = jdbcTemplate.queryForObject(
                """
                        select pg_get_constraintdef(oid)
                        from pg_constraint
                        where conrelid = 'patients'::regclass
                          and conname = 'fk_patients_user'
                        """,
                String.class
        );
        Integer patientPhoneIndexCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from pg_indexes
                        where schemaname = 'public'
                          and tablename = 'patients'
                          and indexname = 'ix_patients_phone'
                        """,
                Integer.class
        );
        List<String> bookingSources = jdbcTemplate.queryForList(
                "select unnest(enum_range(null::booking_source_enum))::text",
                String.class
        );
        Map<String, Object> bookingSourceColumn = jdbcTemplate.queryForMap(
                """
                        select udt_name, is_nullable, column_default
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'appointments'
                          and column_name = 'booking_source'
                        """
        );
        Integer patientsWithMissingBackfilledName = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from patients patient
                        join users clinic_user on clinic_user.id = patient.user_id
                        where patient.full_name is distinct from clinic_user.full_name
                        """,
                Integer.class
        );
        Integer appointmentsWithInvalidBookingSource = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from appointments
                        where booking_source is null
                           or booking_source::text not in ('ONLINE', 'WALK_IN')
                        """,
                Integer.class
        );

        assertThat(migrationSucceeded).isTrue();
        assertThat(patientNameContractSucceeded).isTrue();
        assertThat(patientUserColumn.get("is_nullable")).isEqualTo("YES");
        assertThat(patientNameColumn.get("data_type")).isEqualTo("character varying");
        assertThat(patientNameColumn.get("character_maximum_length")).isEqualTo(150);
        assertThat(patientNameColumn.get("is_nullable")).isEqualTo("NO");
        assertThat(patientUserForeignKey).contains("ON DELETE SET NULL");
        assertThat(patientPhoneIndexCount).isEqualTo(1);
        assertThat(bookingSources).containsExactly("ONLINE", "WALK_IN");
        assertThat(bookingSourceColumn.get("udt_name")).isEqualTo("booking_source_enum");
        assertThat(bookingSourceColumn.get("is_nullable")).isEqualTo("NO");
        assertThat(bookingSourceColumn.get("column_default").toString()).contains("ONLINE");
        assertThat(patientsWithMissingBackfilledName).isZero();
        assertThat(appointmentsWithInvalidBookingSource).isZero();
    }

    @Test
    void shouldPersistPatientWithoutUserAndPreservePatientWhenLinkedUserIsDeleted() {
        String marker = UUID.randomUUID().toString();
        UUID walkInPatientId = UUID.randomUUID();
        UUID linkedUserId = UUID.randomUUID();
        UUID linkedPatientId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        insert into patients (id, user_id, full_name, phone)
                        values (?, null, ?, ?)
                        """,
                walkInPatientId,
                "Bệnh nhân trực tiếp " + marker,
                "090" + marker.substring(0, 7)
        );
        jdbcTemplate.update(
                """
                        insert into users (id, email, password_hash, full_name, role)
                        values (?, ?, ?, ?, 'PATIENT')
                        """,
                linkedUserId,
                "walk-in-fk-" + marker + "@example.com",
                "not-a-real-password-hash",
                "Bệnh nhân liên kết " + marker
        );
        jdbcTemplate.update(
                """
                        insert into patients (id, user_id, full_name)
                        values (?, ?, ?)
                        """,
                linkedPatientId,
                linkedUserId,
                "Bệnh nhân liên kết " + marker
        );

        assertThat(jdbcTemplate.queryForObject(
                "select user_id from patients where id = ?",
                UUID.class,
                walkInPatientId
        )).isNull();

        jdbcTemplate.update("delete from users where id = ?", linkedUserId);

        Integer preservedPatientCount = jdbcTemplate.queryForObject(
                "select count(*) from patients where id = ? and user_id is null",
                Integer.class,
                linkedPatientId
        );
        assertThat(preservedPatientCount).isEqualTo(1);
    }

    @Test
    void shouldRefusePatientNameContractWhenARecordHasNoValidName() throws Exception {
        jdbcTemplate.execute("""
                create temporary table patients (
                    full_name varchar(150)
                ) on commit drop
                """);
        jdbcTemplate.update("insert into patients (full_name) values (null)");
        String migrationSql = new ClassPathResource(
                "db/migration/V20__require_patient_full_name.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> jdbcTemplate.execute(migrationSql));

        assertThat(failure).isInstanceOf(DataAccessException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat(sqlException.getSQLState()).isEqualTo("P0001");
        assertThat(sqlException.getMessage()).contains(
                "Refusing to require patients.full_name"
        );
    }

    @Test
    void shouldRefuseDraftRemovalWhenTableContainsUnfinalizedData() throws Exception {
        jdbcTemplate.execute("""
                create temporary table medical_record_drafts (
                    id integer primary key
                ) on commit drop
                """);
        jdbcTemplate.update("insert into medical_record_drafts (id) values (1)");
        String migrationSql = new ClassPathResource(
                "db/migration/V8__remove_medical_record_drafts.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> jdbcTemplate.execute(migrationSql));

        assertThat(failure).isInstanceOf(DataAccessException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat(sqlException.getSQLState()).isEqualTo("P0001");
        assertThat(sqlException.getMessage()).contains(
                "Refusing to drop medical_record_drafts because it contains unfinalized patient data"
        );
    }

    @Test
    void shouldRefuseUniqueChatSessionMigrationWhenDuplicateUsersExist() throws Exception {
        UUID duplicatedUserId = UUID.randomUUID();
        jdbcTemplate.execute("""
                create temporary table chat_sessions (
                    id uuid primary key default uuid_generate_v4(),
                    user_id uuid not null
                ) on commit drop
                """);
        jdbcTemplate.update(
                "insert into chat_sessions (user_id) values (?), (?)",
                duplicatedUserId,
                duplicatedUserId
        );
        String migrationSql = new ClassPathResource(
                "db/migration/V12__enforce_one_chat_session_per_user.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> jdbcTemplate.execute(migrationSql));

        assertThat(failure).isInstanceOf(DataAccessException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat(sqlException.getSQLState()).isEqualTo("P0001");
        assertThat(sqlException.getMessage()).contains(
                "Refusing to enforce one chat session per user"
        );
    }

    @Test
    void shouldRefuseAppointmentQueueBackfillWhenDoctorSessionHasMoreThanTwoActiveAppointments()
            throws Exception {
        createTemporaryLegacyAppointmentsTable();
        UUID doctorId = UUID.randomUUID();
        insertTemporaryLegacyAppointment(UUID.randomUUID(), doctorId, "2031-01-06 08:00:00+07");
        insertTemporaryLegacyAppointment(UUID.randomUUID(), doctorId, "2031-01-06 09:00:00+07");
        insertTemporaryLegacyAppointment(UUID.randomUUID(), doctorId, "2031-01-06 10:00:00+07");

        Throwable failure = executeAppointmentQueueMigration();

        assertMigrationRefusal(
                failure,
                "doctor/date/session group(s) contain more than two active appointments"
        );
    }

    @Test
    void shouldRefuseAppointmentQueueBackfillWhenPatientHasTwoActiveAppointmentsInOneSession()
            throws Exception {
        createTemporaryLegacyAppointmentsTable();
        UUID patientId = UUID.randomUUID();
        insertTemporaryLegacyAppointment(patientId, UUID.randomUUID(), "2031-01-06 08:00:00+07");
        insertTemporaryLegacyAppointment(patientId, UUID.randomUUID(), "2031-01-06 10:00:00+07");

        Throwable failure = executeAppointmentQueueMigration();

        assertMigrationRefusal(
                failure,
                "patient/date/session group(s) contain more than one active appointment"
        );
    }

    @Test
    void shouldBackfillSafeLegacyAppointmentsWithoutChangingScheduledStart() throws Exception {
        createTemporaryLegacyAppointmentsTable();
        UUID doctorId = UUID.randomUUID();
        insertTemporaryLegacyAppointment(
                UUID.randomUUID(),
                doctorId,
                "2031-01-06 08:00:00+07",
                "BOOKED"
        );
        insertTemporaryLegacyAppointment(
                UUID.randomUUID(),
                doctorId,
                "2031-01-06 09:00:00+07",
                "CANCELLED"
        );
        insertTemporaryLegacyAppointment(
                UUID.randomUUID(),
                doctorId,
                "2031-01-06 10:00:00+07",
                "BOOKED"
        );

        Throwable failure = executeAppointmentQueueMigration();

        assertThat(failure).isNull();
        List<Map<String, Object>> appointments = jdbcTemplate.queryForList(
                """
                        select
                            to_char(
                                scheduled_start_at at time zone 'Asia/Ho_Chi_Minh',
                                'HH24:MI'
                            ) as start_time,
                            appointment_date::text as appointment_date,
                            session::text as session,
                            queue_number::integer as queue_number,
                            status::text as status
                        from appointments
                        order by scheduled_start_at
                        """
        );
        assertThat(appointments)
                .extracting(
                        row -> row.get("start_time"),
                        row -> row.get("appointment_date"),
                        row -> row.get("session"),
                        row -> row.get("queue_number"),
                        row -> row.get("status")
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "08:00", "2031-01-06", "MORNING", 1, "BOOKED"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "09:00", "2031-01-06", "MORNING", 1, "CANCELLED"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "10:00", "2031-01-06", "MORNING", 2, "BOOKED"
                        )
                );
    }

    @Test
    void shouldRefuseQueueContractWhenAnAppointmentIsIncomplete() throws Exception {
        createTemporaryLegacyAppointmentsTable();
        assertThat(executeAppointmentQueueMigration()).isNull();
        insertTemporaryLegacyAppointment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "2031-01-06 08:00:00+07"
        );

        Throwable failure = executeAppointmentQueueContractMigration();

        assertMigrationRefusal(
                failure,
                "appointment(s) have an incomplete queue model"
        );
    }

    @Test
    void shouldRefuseScheduleStandardizationWhenActiveFutureAppointmentIsOutsidePolicy()
            throws Exception {
        createTemporaryLegacyAppointmentsTable();
        insertTemporaryLegacyAppointment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "2031-01-06 13:00:00+07"
        );

        Throwable failure = executeStandardScheduleMigration();

        assertMigrationRefusal(
                failure,
                "active future appointment(s) fall outside the new Monday-Saturday"
        );
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
    void shouldReleaseCancelledQueueAndRejectASecondActiveAppointment() {
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
                "ux_appointments_doctor_queue_active"
        );
    }

    @Test
    void shouldRestrictQueueNumberToOneOrTwo() {
        Fixture fixture = createFixture();

        assertConstraintViolation(
                () -> insertQueueAppointment(
                        fixture.patientId(),
                        fixture.doctorId(),
                        fixture.serviceId(),
                        APPOINTMENT_START,
                        "BOOKED",
                        LocalDate.of(2031, 1, 6),
                        "MORNING",
                        3
                ),
                "23514",
                "ck_appointments_queue_number"
        );
    }

    @Test
    void shouldRequireEveryQueueColumn() {
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
                                    appointment_date
                                ) values (?, ?, ?, ?, 'BOOKED', ?)
                                """,
                        fixture.patientId(),
                        fixture.doctorId(),
                        fixture.serviceId(),
                        Timestamp.from(APPOINTMENT_START),
                        LocalDate.of(2031, 1, 6)
                ),
                "23502",
                "session"
        );
    }

    @Test
    void shouldRejectTwoActivePatientsInTheSameDoctorQueue() {
        Fixture fixture = createFixture();
        UUID secondPatientId = insertPatient("doctor-queue-" + fixture.marker());
        LocalDate appointmentDate = LocalDate.of(2031, 1, 6);
        insertQueueAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "BOOKED",
                appointmentDate,
                "MORNING",
                1
        );

        assertConstraintViolation(
                () -> insertQueueAppointment(
                        secondPatientId,
                        fixture.doctorId(),
                        fixture.serviceId(),
                        APPOINTMENT_START.plusSeconds(1_800),
                        "CHECKED_IN",
                        appointmentDate,
                        "MORNING",
                        1
                ),
                "23505",
                "ux_appointments_doctor_queue_active"
        );
    }

    @Test
    void shouldRejectOnePatientBookingTwoDoctorsInTheSameSession() {
        Fixture fixture = createFixture();
        UUID secondDoctorId = insertDoctor(
                "patient-session-doctor-" + fixture.marker(),
                fixture.serviceId()
        );
        LocalDate appointmentDate = LocalDate.of(2031, 1, 6);
        insertQueueAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "BOOKED",
                appointmentDate,
                "MORNING",
                1
        );

        assertConstraintViolation(
                () -> insertQueueAppointment(
                        fixture.patientId(),
                        secondDoctorId,
                        fixture.serviceId(),
                        APPOINTMENT_START.plusSeconds(5_400),
                        "BOOKED",
                        appointmentDate,
                        "MORNING",
                        2
                ),
                "23505",
                "ux_appointments_patient_session_active"
        );
    }

    @Test
    void shouldReleaseDoctorQueueAndPatientSessionWhenAppointmentIsCancelled() {
        Fixture fixture = createFixture();
        LocalDate appointmentDate = LocalDate.of(2031, 1, 6);
        insertQueueAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "CANCELLED",
                appointmentDate,
                "MORNING",
                1
        );

        insertQueueAppointment(
                fixture.patientId(),
                fixture.doctorId(),
                fixture.serviceId(),
                APPOINTMENT_START,
                "BOOKED",
                appointmentDate,
                "MORNING",
                1
        );

        Integer appointmentCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from appointments
                        where patient_id = ?
                          and doctor_id = ?
                          and appointment_date = ?
                          and session = 'MORNING'
                          and queue_number = 1
                        """,
                Integer.class,
                fixture.patientId(),
                fixture.doctorId(),
                appointmentDate
        );
        assertThat(appointmentCount).isEqualTo(2);
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
                                    appointment_date,
                                    session,
                                    queue_number,
                                    status,
                                    actual_start_at,
                                    actual_end_at
                                ) values (
                                    ?, ?, ?, ?, date '2031-01-06', 'MORNING', 1,
                                    'COMPLETED', ?, ?
                                )
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
                "insert into patients (user_id, full_name) values (?, ?) returning id",
                UUID.class,
                patientUserId,
                "Bệnh nhân Phase 25"
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

    private UUID insertPatient(String marker) {
        UUID userId = insertUser(
                "phase-slot01-patient-" + marker + "@example.com",
                "Bệnh nhân Slot 01",
                "PATIENT"
        );
        return jdbcTemplate.queryForObject(
                "insert into patients (user_id, full_name) values (?, ?) returning id",
                UUID.class,
                userId,
                "Bệnh nhân Slot 01"
        );
    }

    private UUID insertDoctor(String marker, UUID serviceId) {
        UUID userId = insertUser(
                "phase-slot01-doctor-" + marker + "@example.com",
                "Bác sĩ Slot 01",
                "DOCTOR"
        );
        UUID doctorId = jdbcTemplate.queryForObject(
                """
                        insert into doctors (
                            user_id,
                            specialization,
                            license_number,
                            working_status
                        ) values (?, 'Nha khoa tổng quát', ?, 'ACTIVE')
                        returning id
                        """,
                UUID.class,
                userId,
                "SLOT01-" + marker
        );
        jdbcTemplate.update(
                "insert into doctor_services (doctor_id, service_id) values (?, ?)",
                doctorId,
                serviceId
        );
        return doctorId;
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
                            appointment_date,
                            session,
                            queue_number,
                            status
                        ) values (
                            ?, ?, ?, ?, date '2031-01-06', 'MORNING', 1,
                            ?::appointment_status_enum
                        )
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

    private UUID insertQueueAppointment(
            UUID patientId,
            UUID doctorId,
            UUID serviceId,
            Instant scheduledStartAt,
            String status,
            LocalDate appointmentDate,
            String session,
            int queueNumber
    ) {
        return jdbcTemplate.queryForObject(
                """
                        insert into appointments (
                            patient_id,
                            doctor_id,
                            service_id,
                            scheduled_start_at,
                            status,
                            appointment_date,
                            session,
                            queue_number
                        ) values (?, ?, ?, ?, ?::appointment_status_enum, ?, ?::session_enum, ?)
                        returning id
                        """,
                UUID.class,
                patientId,
                doctorId,
                serviceId,
                Timestamp.from(scheduledStartAt),
                status,
                appointmentDate,
                session,
                queueNumber
        );
    }

    private void createTemporaryLegacyAppointmentsTable() {
        jdbcTemplate.execute("""
                create temporary table appointments (
                    id uuid primary key default uuid_generate_v4(),
                    patient_id uuid not null,
                    doctor_id uuid not null,
                    scheduled_start_at timestamptz not null,
                    status appointment_status_enum not null
                ) on commit drop
                """);
        jdbcTemplate.execute("""
                create unique index ux_appointments_doctor_start_not_cancelled
                    on appointments (doctor_id, scheduled_start_at)
                    where status <> 'CANCELLED'
                """);
    }

    private void insertTemporaryLegacyAppointment(
            UUID patientId,
            UUID doctorId,
            String scheduledStartAt
    ) {
        insertTemporaryLegacyAppointment(patientId, doctorId, scheduledStartAt, "BOOKED");
    }

    private void insertTemporaryLegacyAppointment(
            UUID patientId,
            UUID doctorId,
            String scheduledStartAt,
            String status
    ) {
        jdbcTemplate.update(
                """
                        insert into appointments (
                            patient_id,
                            doctor_id,
                            scheduled_start_at,
                            status
                        ) values (?, ?, ?::timestamptz, ?::appointment_status_enum)
                        """,
                patientId,
                doctorId,
                scheduledStartAt,
                status
        );
    }

    private Throwable executeAppointmentQueueMigration() throws Exception {
        String migrationSql = new ClassPathResource(
                "db/migration/V15__add_appointment_queue_model.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        return catchThrowable(() -> jdbcTemplate.execute(migrationSql));
    }

    private Throwable executeStandardScheduleMigration() throws Exception {
        String migrationSql = new ClassPathResource(
                "db/migration/V16__standardize_doctor_working_sessions.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        return catchThrowable(() -> jdbcTemplate.execute(migrationSql));
    }

    private Throwable executeAppointmentQueueContractMigration() throws Exception {
        String migrationSql = new ClassPathResource(
                "db/migration/V18__enforce_appointment_queue_contract.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        return catchThrowable(() -> jdbcTemplate.execute(migrationSql));
    }

    private void assertMigrationRefusal(Throwable failure, String expectedMessage) {
        assertThat(failure).isInstanceOf(DataAccessException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat(sqlException.getSQLState()).isEqualTo("P0001");
        assertThat(sqlException.getMessage()).contains(expectedMessage);
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
