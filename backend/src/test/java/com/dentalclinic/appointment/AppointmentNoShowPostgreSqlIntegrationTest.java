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
@Import(AppointmentNoShowPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentNoShowPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:15:00Z");

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
        registry.add("security.jwt.issuer", () -> "appointment-no-show-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.no-show-grace-minutes", () -> 15);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldMarkNoShowAtExactGracePersistStatusAndWriteAudit() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        fixture.eligibleAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Đánh dấu bệnh nhân không đến khám thành công"))
                .andExpect(jsonPath("$.data.id")
                        .value(fixture.eligibleAppointmentId().toString()))
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.noShowAt")
                        .value("2026-10-12T09:15:00+07:00"));

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select status::text as status, scheduled_start_at, checked_in_at
                from appointments
                where id = ?
                """, fixture.eligibleAppointmentId());
        assertThat(appointmentRow.get("status")).isEqualTo("NO_SHOW");
        assertThat(((Timestamp) appointmentRow.get("scheduled_start_at")).toInstant())
                .isEqualTo(fixture.eligibleStartAt());
        assertThat(appointmentRow.get("checked_in_at")).isNull();

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       reason,
                       details ->> 'old_status' as old_status,
                       details ->> 'new_status' as new_status,
                       details ->> 'scheduled_start_at' as scheduled_start_at,
                       details ->> 'no_show_at' as no_show_at
                from appointment_histories
                where appointment_id = ? and action = 'NO_SHOW'
                """, fixture.eligibleAppointmentId());
        assertThat(historyRow.get("action")).isEqualTo("NO_SHOW");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.firstDoctorUserId());
        assertThat(historyRow.get("reason")).isNull();
        assertThat(historyRow.get("old_status")).isEqualTo("BOOKED");
        assertThat(historyRow.get("new_status")).isEqualTo("NO_SHOW");
        assertThat(historyRow.get("scheduled_start_at"))
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(historyRow.get("no_show_at"))
                .isEqualTo("2026-10-12T09:15+07:00");

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        fixture.eligibleAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Chỉ có thể đánh dấu NO_SHOW cho lịch hẹn đang ở trạng thái BOOKED"
                ));
        assertThat(noShowHistoryCount(fixture.eligibleAppointmentId())).isEqualTo(1);
    }

    @Test
    void shouldRejectNoShowBeforeGraceExpiresWithoutAudit() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        fixture.tooEarlyAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Chỉ có thể xác nhận NO_SHOW sau giờ hẹn ít nhất 15 phút"));

        assertThat(statusOf(fixture.tooEarlyAppointmentId())).isEqualTo("BOOKED");
        assertThat(noShowHistoryCount(fixture.tooEarlyAppointmentId())).isZero();
    }

    @Test
    void shouldRejectNoShowAfterPatientCheckedIn() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        fixture.checkedInAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Không thể đánh dấu NO_SHOW vì bệnh nhân đã check-in"));

        assertThat(statusOf(fixture.checkedInAppointmentId())).isEqualTo("CHECKED_IN");
        assertThat(noShowHistoryCount(fixture.checkedInAppointmentId())).isZero();
    }

    @Test
    void shouldNotAllowDoctorToMarkAnotherDoctorsAppointmentNoShow() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        fixture.otherDoctorAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(statusOf(fixture.otherDoctorAppointmentId())).isEqualTo("BOOKED");
        assertThat(noShowHistoryCount(fixture.otherDoctorAppointmentId())).isZero();
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User firstDoctorUser = saveUser(
                "phase18-doctor-a-" + marker + "@example.com",
                "Bác sĩ Phase 18 A",
                Role.DOCTOR
        );
        Doctor firstDoctor = saveDoctor(firstDoctorUser, "PHASE18-A-" + marker);
        User secondDoctorUser = saveUser(
                "phase18-doctor-b-" + marker + "@example.com",
                "Bác sĩ Phase 18 B",
                Role.DOCTOR
        );
        Doctor secondDoctor = saveDoctor(secondDoctorUser, "PHASE18-B-" + marker);
        User patientUser = saveUser(
                "phase18-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 18",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        DentalService service = saveService("Dịch vụ Phase 18 " + marker);
        DoctorService firstMapping = saveMapping(firstDoctor, service);
        DoctorService secondMapping = saveMapping(secondDoctor, service);

        Instant eligibleStart = FIXED_NOW.minusSeconds(15 * 60L);
        Appointment eligible = saveAppointment(
                patient,
                firstMapping,
                eligibleStart,
                AppointmentStatus.BOOKED,
                null
        );
        Appointment tooEarly = saveAppointment(
                patient,
                firstMapping,
                FIXED_NOW.minusSeconds(15 * 60L - 1),
                AppointmentStatus.BOOKED,
                null
        );
        Appointment checkedIn = saveAppointment(
                patient,
                firstMapping,
                FIXED_NOW.minusSeconds(45 * 60L),
                AppointmentStatus.CHECKED_IN,
                FIXED_NOW.minusSeconds(30 * 60L)
        );
        Appointment otherDoctorAppointment = saveAppointment(
                patient,
                secondMapping,
                FIXED_NOW.minusSeconds(60 * 60L),
                AppointmentStatus.BOOKED,
                null
        );

        return new Fixture(
                firstDoctorUser.getId(),
                firstDoctorUser.getEmail(),
                eligible.getId(),
                eligibleStart,
                tooEarly.getId(),
                checkedIn.getId(),
                otherDoctorAppointment.getId()
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
        service.setDescription("Dịch vụ kiểm thử NO_SHOW");
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
            Instant checkedInAt
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(startAt);
        appointment.setStatus(status);
        appointment.setCheckedInAt(checkedInAt);
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

    private int noShowHistoryCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories
                where appointment_id = ? and action = 'NO_SHOW'
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
            UUID eligibleAppointmentId,
            Instant eligibleStartAt,
            UUID tooEarlyAppointmentId,
            UUID checkedInAppointmentId,
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
