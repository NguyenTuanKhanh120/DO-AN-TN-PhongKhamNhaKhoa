package com.dentalclinic.appointment;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class AppointmentHistoryPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DentalServiceRepository dentalServiceRepository;

    @Autowired
    private DoctorServiceRepository doctorServiceRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-history-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldReturnCompletedAppointmentTimelineInChronologicalOrder() throws Exception {
        String token = login(fixture.adminEmail());

        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        fixture.completedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].action").value("BOOK"))
                .andExpect(jsonPath("$.data[0].changedByUserId")
                        .value(fixture.patientUserId().toString()))
                .andExpect(jsonPath("$.data[0].changedByRole").value("PATIENT"))
                .andExpect(jsonPath("$.data[1].action").value("CHECK_IN"))
                .andExpect(jsonPath("$.data[1].changedByUserId")
                        .value(fixture.doctorUserId().toString()))
                .andExpect(jsonPath("$.data[1].changedByRole").value("DOCTOR"))
                .andExpect(jsonPath("$.data[1].details.old_status").value("BOOKED"))
                .andExpect(jsonPath("$.data[1].details.new_status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data[2].action").value("START_EXAM"))
                .andExpect(jsonPath("$.data[3].action").value("COMPLETE"))
                .andExpect(jsonPath("$.data[3].details.medical_record_id")
                        .value(fixture.medicalRecordId().toString()))
                .andExpect(jsonPath("$.data[3].createdAt")
                        .value("2026-10-12T02:50:00Z"));
    }

    @Test
    void shouldReturnRescheduleCancelAndNoShowAuditDetails() throws Exception {
        String token = login(fixture.adminEmail());

        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        fixture.cancelledAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].action").value("BOOK"))
                .andExpect(jsonPath("$.data[1].action").value("RESCHEDULE"))
                .andExpect(jsonPath("$.data[2].action").value("CANCEL"))
                .andExpect(jsonPath("$.data[1].reason").value("Bận việc gia đình"))
                .andExpect(jsonPath("$.data[1].details.old_scheduled_start_at")
                        .value("2026-10-13T09:00:00+07:00"))
                .andExpect(jsonPath("$.data[1].details.new_scheduled_start_at")
                        .value("2026-10-13T10:00:00+07:00"))
                .andExpect(jsonPath("$.data[2].reason").value("PATIENT_REQUEST"))
                .andExpect(jsonPath("$.data[2].details.new_status").value("CANCELLED"));

        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        fixture.noShowAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].action").value("BOOK"))
                .andExpect(jsonPath("$.data[1].action").value("NO_SHOW"))
                .andExpect(jsonPath("$.data[1].details.old_status").value("BOOKED"))
                .andExpect(jsonPath("$.data[1].details.new_status").value("NO_SHOW"));
    }

    @Test
    void shouldReturnEmptyHistoryForExistingAppointmentAndNotFoundForUnknownId()
            throws Exception {
        String token = login(fixture.adminEmail());

        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        fixture.emptyHistoryAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        UUID.randomUUID()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy lịch hẹn"));
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User admin = saveUser(
                "phase23-admin-" + marker + "@example.com",
                "Quản trị viên Phase 23",
                Role.ADMIN
        );
        User patientUser = saveUser(
                "phase23-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 23",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        User doctorUser = saveUser(
                "phase23-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 23",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE23-" + marker);
        DentalService service = saveService("Dịch vụ Phase 23 " + marker);
        DoctorService mapping = saveMapping(doctor, service);

        Appointment completed = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-12T02:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        Appointment cancelled = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-13T03:00:00Z"),
                AppointmentStatus.CANCELLED
        );
        Appointment noShow = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-14T02:00:00Z"),
                AppointmentStatus.NO_SHOW
        );
        Appointment emptyHistory = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-15T02:00:00Z"),
                AppointmentStatus.BOOKED
        );
        UUID medicalRecordId = UUID.randomUUID();

        insertCompletedTimeline(
                completed.getId(),
                patientUser.getId(),
                doctorUser.getId(),
                medicalRecordId
        );
        insertCancelledTimeline(
                cancelled.getId(),
                patientUser.getId()
        );
        insertNoShowTimeline(
                noShow.getId(),
                patientUser.getId(),
                doctorUser.getId()
        );

        return new Fixture(
                admin.getEmail(),
                patientUser.getId(),
                doctorUser.getId(),
                completed.getId(),
                cancelled.getId(),
                noShow.getId(),
                emptyHistory.getId(),
                medicalRecordId
        );
    }

    private void insertCompletedTimeline(
            UUID appointmentId,
            UUID patientUserId,
            UUID doctorUserId,
            UUID medicalRecordId
    ) {
        insertHistory(
                appointmentId,
                AppointmentAction.BOOK,
                patientUserId,
                null,
                JsonNodeFactory.instance.objectNode().put("status", "BOOKED"),
                "2026-10-12T01:00:00Z"
        );
        insertHistory(
                appointmentId,
                AppointmentAction.CHECK_IN,
                doctorUserId,
                null,
                statusChange("BOOKED", "CHECKED_IN")
                        .put("checked_in_at", "2026-10-12T08:55:00+07:00"),
                "2026-10-12T01:55:00Z"
        );
        insertHistory(
                appointmentId,
                AppointmentAction.START_EXAM,
                doctorUserId,
                null,
                statusChange("CHECKED_IN", "IN_PROGRESS")
                        .put("actual_start_at", "2026-10-12T09:05:00+07:00"),
                "2026-10-12T02:05:00Z"
        );
        insertHistory(
                appointmentId,
                AppointmentAction.COMPLETE,
                doctorUserId,
                null,
                statusChange("IN_PROGRESS", "COMPLETED")
                        .put("actual_end_at", "2026-10-12T09:50:00+07:00")
                        .put("medical_record_id", medicalRecordId.toString()),
                "2026-10-12T02:50:00Z"
        );
    }

    private void insertCancelledTimeline(UUID appointmentId, UUID patientUserId) {
        insertHistory(
                appointmentId,
                AppointmentAction.BOOK,
                patientUserId,
                null,
                JsonNodeFactory.instance.objectNode().put("status", "BOOKED"),
                "2026-10-11T01:00:00Z"
        );
        insertHistory(
                appointmentId,
                AppointmentAction.RESCHEDULE,
                patientUserId,
                "Bận việc gia đình",
                JsonNodeFactory.instance.objectNode()
                        .put("old_scheduled_start_at", "2026-10-13T09:00:00+07:00")
                        .put("new_scheduled_start_at", "2026-10-13T10:00:00+07:00"),
                "2026-10-11T02:00:00Z"
        );
        insertHistory(
                appointmentId,
                AppointmentAction.CANCEL,
                patientUserId,
                "PATIENT_REQUEST",
                statusChange("BOOKED", "CANCELLED"),
                "2026-10-11T03:00:00Z"
        );
    }

    private void insertNoShowTimeline(
            UUID appointmentId,
            UUID patientUserId,
            UUID doctorUserId
    ) {
        insertHistory(
                appointmentId,
                AppointmentAction.BOOK,
                patientUserId,
                null,
                JsonNodeFactory.instance.objectNode().put("status", "BOOKED"),
                "2026-10-13T01:00:00Z"
        );
        insertHistory(
                appointmentId,
                AppointmentAction.NO_SHOW,
                doctorUserId,
                null,
                statusChange("BOOKED", "NO_SHOW")
                        .put("no_show_at", "2026-10-14T09:15:00+07:00"),
                "2026-10-14T02:15:00Z"
        );
    }

    private ObjectNode statusChange(String oldStatus, String newStatus) {
        return JsonNodeFactory.instance.objectNode()
                .put("old_status", oldStatus)
                .put("new_status", newStatus);
    }

    private void insertHistory(
            UUID appointmentId,
            AppointmentAction action,
            UUID changedBy,
            String reason,
            JsonNode details,
            String createdAt
    ) {
        jdbcTemplate.update("""
                insert into appointment_histories (
                    id, appointment_id, action, changed_by, reason, details, created_at
                ) values (?, ?, cast(? as appointment_action_enum), ?, ?, cast(? as jsonb), ?)
                """,
                UUID.randomUUID(),
                appointmentId,
                action.name(),
                changedBy,
                reason,
                details.toString(),
                Timestamp.from(Instant.parse(createdAt))
        );
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private Doctor saveDoctor(User user, String licenseNumber) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber(licenseNumber);
        doctor.setRating(new BigDecimal("4.7"));
        doctor.setExperienceYears(9);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử audit");
        service.setPrice(new BigDecimal("350000"));
        service.setActive(true);
        return dentalServiceRepository.saveAndFlush(service);
    }

    private DoctorService saveMapping(Doctor doctor, DentalService service) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);
        return doctorServiceRepository.saveAndFlush(mapping);
    }

    private Appointment saveAppointment(
            Patient patient,
            DoctorService mapping,
            Instant scheduledStartAt,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(status);
        return appointmentRepository.saveAndFlush(appointment);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            String adminEmail,
            UUID patientUserId,
            UUID doctorUserId,
            UUID completedAppointmentId,
            UUID cancelledAppointmentId,
            UUID noShowAppointmentId,
            UUID emptyHistoryAppointmentId,
            UUID medicalRecordId
    ) {
    }
}
