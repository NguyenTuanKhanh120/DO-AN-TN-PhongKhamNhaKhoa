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
@Import(AppointmentCheckInPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentCheckInPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T01:55:00Z");

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
        registry.add("security.jwt.issuer", () -> "appointment-check-in-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldCheckInOwnedAppointmentPersistTimestampAndWriteAudit() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/check-in",
                        fixture.bookedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Check-in bệnh nhân thành công"))
                .andExpect(jsonPath("$.data.id")
                        .value(fixture.bookedAppointmentId().toString()))
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.checkedInAt")
                        .value("2026-10-12T08:55:00+07:00"));

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select status::text as status, scheduled_start_at, checked_in_at
                from appointments
                where id = ?
                """, fixture.bookedAppointmentId());
        assertThat(appointmentRow.get("status")).isEqualTo("CHECKED_IN");
        assertThat(((Timestamp) appointmentRow.get("scheduled_start_at")).toInstant())
                .isEqualTo(fixture.bookedStartAt());
        assertThat(((Timestamp) appointmentRow.get("checked_in_at")).toInstant())
                .isEqualTo(FIXED_NOW);

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       reason,
                       details ->> 'old_status' as old_status,
                       details ->> 'new_status' as new_status,
                       details ->> 'scheduled_start_at' as scheduled_start_at,
                       details ->> 'checked_in_at' as checked_in_at
                from appointment_histories
                where appointment_id = ? and action = 'CHECK_IN'
                """, fixture.bookedAppointmentId());
        assertThat(historyRow.get("action")).isEqualTo("CHECK_IN");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.firstDoctorUserId());
        assertThat(historyRow.get("reason")).isNull();
        assertThat(historyRow.get("old_status")).isEqualTo("BOOKED");
        assertThat(historyRow.get("new_status")).isEqualTo("CHECKED_IN");
        assertThat(historyRow.get("scheduled_start_at"))
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(historyRow.get("checked_in_at"))
                .isEqualTo("2026-10-12T08:55+07:00");

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/check-in",
                        fixture.bookedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Chỉ có thể check-in lịch hẹn đang ở trạng thái BOOKED"));
        assertThat(checkInHistoryCount(fixture.bookedAppointmentId())).isEqualTo(1);
    }

    @Test
    void shouldNotAllowDoctorToCheckInAnotherDoctorsAppointment() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/check-in",
                        fixture.otherDoctorAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(statusOf(fixture.otherDoctorAppointmentId())).isEqualTo("BOOKED");
        assertThat(checkInHistoryCount(fixture.otherDoctorAppointmentId())).isZero();
    }

    @Test
    void shouldRejectCheckInWhenAppointmentIsNotBooked() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/check-in",
                        fixture.completedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Chỉ có thể check-in lịch hẹn đang ở trạng thái BOOKED"));

        assertThat(statusOf(fixture.completedAppointmentId())).isEqualTo("COMPLETED");
        assertThat(checkInHistoryCount(fixture.completedAppointmentId())).isZero();
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User firstDoctorUser = saveUser(
                "phase16-doctor-a-" + marker + "@example.com",
                "Bác sĩ Phase 16 A",
                Role.DOCTOR
        );
        Doctor firstDoctor = saveDoctor(firstDoctorUser, "PHASE16-A-" + marker);
        User secondDoctorUser = saveUser(
                "phase16-doctor-b-" + marker + "@example.com",
                "Bác sĩ Phase 16 B",
                Role.DOCTOR
        );
        Doctor secondDoctor = saveDoctor(secondDoctorUser, "PHASE16-B-" + marker);
        User patientUser = saveUser(
                "phase16-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 16",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        DentalService service = saveService("Dịch vụ Phase 16 " + marker);
        DoctorService firstMapping = saveMapping(firstDoctor, service);
        DoctorService secondMapping = saveMapping(secondDoctor, service);

        Instant bookedStart = Instant.parse("2026-10-12T02:00:00Z");
        Appointment booked = saveAppointment(
                patient,
                firstMapping,
                bookedStart,
                AppointmentStatus.BOOKED
        );
        Appointment completed = saveAppointment(
                patient,
                firstMapping,
                Instant.parse("2026-10-12T04:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        Appointment otherDoctorAppointment = saveAppointment(
                patient,
                secondMapping,
                Instant.parse("2026-10-12T03:00:00Z"),
                AppointmentStatus.BOOKED
        );

        return new Fixture(
                firstDoctorUser.getId(),
                firstDoctorUser.getEmail(),
                booked.getId(),
                bookedStart,
                completed.getId(),
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
        service.setDescription("Dịch vụ kiểm thử check-in");
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
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(startAt);
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

    private String statusOf(UUID appointmentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from appointments where id = ?",
                String.class,
                appointmentId
        );
    }

    private int checkInHistoryCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories
                where appointment_id = ? and action = 'CHECK_IN'
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
            UUID bookedAppointmentId,
            Instant bookedStartAt,
            UUID completedAppointmentId,
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
