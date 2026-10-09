package com.dentalclinic.appointment;

import com.dentalclinic.appointment.entity.Appointment;
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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
@Import(AppointmentStartExaminationPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentStartExaminationPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:15:00Z");
    private static final Instant CHECKED_IN_AT = Instant.parse("2026-10-12T01:55:00Z");

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
        registry.add("security.jwt.issuer", () -> "appointment-start-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldStartOwnedCheckedInAppointmentPersistTimestampAndWriteAudit() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        fixture.checkedInAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bắt đầu khám thành công"))
                .andExpect(jsonPath("$.data.id")
                        .value(fixture.checkedInAppointmentId().toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.checkedInAt")
                        .value("2026-10-12T08:55:00+07:00"))
                .andExpect(jsonPath("$.data.actualStartAt")
                        .value("2026-10-12T09:15:00+07:00"));

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select status::text as status,
                       scheduled_start_at,
                       checked_in_at,
                       actual_start_at
                from appointments
                where id = ?
                """, fixture.checkedInAppointmentId());
        assertThat(appointmentRow.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(((Timestamp) appointmentRow.get("scheduled_start_at")).toInstant())
                .isEqualTo(fixture.checkedInStartAt());
        assertThat(((Timestamp) appointmentRow.get("checked_in_at")).toInstant())
                .isEqualTo(CHECKED_IN_AT);
        assertThat(((Timestamp) appointmentRow.get("actual_start_at")).toInstant())
                .isEqualTo(FIXED_NOW);

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       reason,
                       details ->> 'old_status' as old_status,
                       details ->> 'new_status' as new_status,
                       details ->> 'scheduled_start_at' as scheduled_start_at,
                       details ->> 'checked_in_at' as checked_in_at,
                       details ->> 'actual_start_at' as actual_start_at
                from appointment_histories
                where appointment_id = ? and action = 'START_EXAM'
                """, fixture.checkedInAppointmentId());
        assertThat(historyRow.get("action")).isEqualTo("START_EXAM");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.firstDoctorUserId());
        assertThat(historyRow.get("reason")).isNull();
        assertThat(historyRow.get("old_status")).isEqualTo("CHECKED_IN");
        assertThat(historyRow.get("new_status")).isEqualTo("IN_PROGRESS");
        assertThat(historyRow.get("scheduled_start_at"))
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(historyRow.get("checked_in_at"))
                .isEqualTo("2026-10-12T08:55+07:00");
        assertThat(historyRow.get("actual_start_at"))
                .isEqualTo("2026-10-12T09:15+07:00");

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        fixture.checkedInAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Chỉ có thể bắt đầu khám cho lịch hẹn đang ở trạng thái CHECKED_IN"
                ));
        assertThat(startHistoryCount(fixture.checkedInAppointmentId())).isEqualTo(1);
    }

    @Test
    void shouldRejectStartWhenAppointmentIsNotCheckedIn() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        fixture.bookedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Chỉ có thể bắt đầu khám cho lịch hẹn đang ở trạng thái CHECKED_IN"
                ));

        assertThat(statusOf(fixture.bookedAppointmentId())).isEqualTo("BOOKED");
        assertThat(startHistoryCount(fixture.bookedAppointmentId())).isZero();
    }

    @Test
    void shouldNotAllowDoctorToStartAnotherDoctorsAppointment() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        fixture.otherDoctorAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(statusOf(fixture.otherDoctorAppointmentId())).isEqualTo("CHECKED_IN");
        assertThat(startHistoryCount(fixture.otherDoctorAppointmentId())).isZero();
    }

    @Test
    void shouldKeepNextPatientWaitingWhileDoctorHasExaminationInProgress() throws Exception {
        Appointment checkedInAppointment = appointmentRepository.findById(
                fixture.checkedInAppointmentId()
        ).orElseThrow();
        Appointment inProgress = saveAppointment(
                checkedInAppointment.getPatient(),
                checkedInAppointment.getDoctorService(),
                Instant.parse("2026-10-12T00:00:00Z"),
                AppointmentStatus.IN_PROGRESS,
                Instant.parse("2026-10-11T23:55:00Z"),
                Instant.parse("2026-10-12T00:05:00Z")
        );
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        fixture.checkedInAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Bác sĩ đang có một ca khám khác chưa hoàn thành"));

        assertThat(statusOf(inProgress.getId())).isEqualTo("IN_PROGRESS");
        assertThat(statusOf(fixture.checkedInAppointmentId())).isEqualTo("CHECKED_IN");
        assertThat(actualStartAtOf(fixture.checkedInAppointmentId())).isNull();
        assertThat(startHistoryCount(fixture.checkedInAppointmentId())).isZero();
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User firstDoctorUser = saveUser(
                "phase17-doctor-a-" + marker + "@example.com",
                "Bác sĩ Phase 17 A",
                Role.DOCTOR
        );
        Doctor firstDoctor = saveDoctor(firstDoctorUser, "PHASE17-A-" + marker);
        User secondDoctorUser = saveUser(
                "phase17-doctor-b-" + marker + "@example.com",
                "Bác sĩ Phase 17 B",
                Role.DOCTOR
        );
        Doctor secondDoctor = saveDoctor(secondDoctorUser, "PHASE17-B-" + marker);
        User patientUser = saveUser(
                "phase17-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 17",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        DentalService service = saveService("Dịch vụ Phase 17 " + marker);
        DoctorService firstMapping = saveMapping(firstDoctor, service);
        DoctorService secondMapping = saveMapping(secondDoctor, service);

        Instant checkedInStart = Instant.parse("2026-10-12T02:00:00Z");
        Appointment checkedIn = saveAppointment(
                patient,
                firstMapping,
                checkedInStart,
                AppointmentStatus.CHECKED_IN,
                CHECKED_IN_AT,
                null
        );
        Appointment booked = saveAppointment(
                patient,
                firstMapping,
                Instant.parse("2026-10-12T04:00:00Z"),
                AppointmentStatus.BOOKED,
                null,
                null
        );
        Appointment otherDoctor = saveAppointment(
                patient,
                secondMapping,
                Instant.parse("2026-10-12T03:00:00Z"),
                AppointmentStatus.CHECKED_IN,
                CHECKED_IN_AT,
                null
        );

        return new Fixture(
                firstDoctorUser.getId(),
                firstDoctorUser.getEmail(),
                checkedIn.getId(),
                checkedInStart,
                booked.getId(),
                otherDoctor.getId()
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

    private Doctor saveDoctor(User user, String licenseNumber) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber(licenseNumber);
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử bắt đầu khám");
        service.setPrice(new BigDecimal("250000"));
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
            DoctorService doctorService,
            Instant startAt,
            AppointmentStatus status,
            Instant checkedInAt,
            Instant actualStartAt
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(startAt);
        appointment.setStatus(status);
        appointment.setCheckedInAt(checkedInAt);
        appointment.setActualStartAt(actualStartAt);
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

    private String statusOf(UUID appointmentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from appointments where id = ?",
                String.class,
                appointmentId
        );
    }

    private Instant actualStartAtOf(UUID appointmentId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "select actual_start_at from appointments where id = ?",
                Timestamp.class,
                appointmentId
        );
        return timestamp == null ? null : timestamp.toInstant();
    }

    private int startHistoryCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories
                where appointment_id = ? and action = 'START_EXAM'
                """, Integer.class, appointmentId);
        return count == null ? 0 : count;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            UUID firstDoctorUserId,
            String firstDoctorEmail,
            UUID checkedInAppointmentId,
            Instant checkedInStartAt,
            UUID bookedAppointmentId,
            UUID otherDoctorAppointmentId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClinicClock() {
            return Clock.fixed(FIXED_NOW, CLINIC_ZONE);
        }
    }
}
