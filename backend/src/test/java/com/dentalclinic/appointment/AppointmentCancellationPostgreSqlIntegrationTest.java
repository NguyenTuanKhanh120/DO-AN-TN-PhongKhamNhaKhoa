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
@Import(AppointmentCancellationPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentCancellationPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");

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
        registry.add("security.jwt.issuer", () -> "appointment-cancellation-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.cancellation-cutoff-minutes", () -> 90);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldCancelAtExactlyNinetyMinutesWriteAuditAndReleaseSlot() throws Exception {
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/cancel",
                        fixture.cancellableAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Bận   việc gia đình  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hủy lịch hẹn thành công"))
                .andExpect(jsonPath("$.data.id")
                        .value(fixture.cancellableAppointmentId().toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.reason").value("Bận việc gia đình"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T11:30:00+07:00"))
                .andExpect(jsonPath("$.data.cancelledAt")
                        .value("2026-10-12T10:00:00+07:00"));

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select status::text as status
                from appointments
                where id = ?
                """, fixture.cancellableAppointmentId());
        assertThat(appointmentRow.get("status")).isEqualTo("CANCELLED");

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       reason,
                       details ->> 'old_status' as old_status,
                       details ->> 'new_status' as new_status,
                       details ->> 'cancelled_at' as cancelled_at
                from appointment_histories
                where appointment_id = ?
                """, fixture.cancellableAppointmentId());
        assertThat(historyRow.get("action")).isEqualTo("CANCEL");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.firstPatientUserId());
        assertThat(historyRow.get("reason")).isEqualTo("Bận việc gia đình");
        assertThat(historyRow.get("old_status")).isEqualTo("BOOKED");
        assertThat(historyRow.get("new_status")).isEqualTo("CANCELLED");
        assertThat(historyRow.get("cancelled_at")).isEqualTo("2026-10-12T10:00+07:00");

        Appointment replacement = new Appointment();
        replacement.setPatient(fixture.secondPatient());
        replacement.setDoctorService(fixture.doctorService());
        replacement.setScheduledStartAt(fixture.cancellableStartAt());
        replacement.setStatus(AppointmentStatus.BOOKED);
        appointmentRepository.saveAndFlush(replacement);

        Integer appointmentCount = jdbcTemplate.queryForObject("""
                select count(*)
                from appointments
                where doctor_id = ? and scheduled_start_at = ?
                """,
                Integer.class,
                fixture.doctorId(),
                Timestamp.from(fixture.cancellableStartAt())
        );
        assertThat(appointmentCount).isEqualTo(2);
    }

    @Test
    void shouldRejectCancellationBelowNinetyMinutesWithoutAudit() throws Exception {
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/cancel",
                        fixture.tooLateAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không thể đến\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Không thể hủy lịch vì lịch hẹn còn dưới 1 giờ 30 phút. "
                                + "Vui lòng liên hệ phòng khám nếu có trường hợp đặc biệt."
                ));

        assertThat(statusOf(fixture.tooLateAppointmentId())).isEqualTo("BOOKED");
        assertThat(cancelHistoryCount(fixture.tooLateAppointmentId())).isZero();
    }

    @Test
    void shouldNotAllowPatientToCancelAnotherPatientsAppointment() throws Exception {
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/cancel",
                        fixture.otherPatientAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không thể đến\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(statusOf(fixture.otherPatientAppointmentId())).isEqualTo("BOOKED");
        assertThat(cancelHistoryCount(fixture.otherPatientAppointmentId())).isZero();
    }

    @Test
    void shouldRejectCancellationWhenAppointmentIsNotBooked() throws Exception {
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/cancel",
                        fixture.completedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không thể đến\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Chỉ có thể hủy lịch hẹn đang ở trạng thái BOOKED"));

        assertThat(statusOf(fixture.completedAppointmentId())).isEqualTo("COMPLETED");
        assertThat(cancelHistoryCount(fixture.completedAppointmentId())).isZero();
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "phase14-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 14",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE14-" + marker);
        User firstPatientUser = saveUser(
                "phase14-patient-a-" + marker + "@example.com",
                "Bệnh nhân Phase 14 A",
                Role.PATIENT
        );
        Patient firstPatient = savePatient(firstPatientUser);
        User secondPatientUser = saveUser(
                "phase14-patient-b-" + marker + "@example.com",
                "Bệnh nhân Phase 14 B",
                Role.PATIENT
        );
        Patient secondPatient = savePatient(secondPatientUser);
        DentalService service = saveService("Dịch vụ Phase 14 " + marker);
        DoctorService doctorService = saveMapping(doctor, service);

        Instant cancellableStartAt = FIXED_NOW.plusSeconds(90 * 60L);
        Appointment cancellable = saveAppointment(
                firstPatient,
                doctorService,
                cancellableStartAt,
                AppointmentStatus.BOOKED
        );
        Appointment tooLate = saveAppointment(
                firstPatient,
                doctorService,
                FIXED_NOW.plusSeconds(90 * 60L - 1),
                AppointmentStatus.BOOKED
        );
        Appointment otherPatient = saveAppointment(
                secondPatient,
                doctorService,
                FIXED_NOW.plusSeconds(3 * 60 * 60L),
                AppointmentStatus.BOOKED
        );
        Appointment completed = saveAppointment(
                firstPatient,
                doctorService,
                FIXED_NOW.plusSeconds(4 * 60 * 60L),
                AppointmentStatus.COMPLETED
        );

        return new Fixture(
                doctor.getId(),
                doctorService,
                firstPatientUser.getId(),
                firstPatientUser.getEmail(),
                secondPatient,
                cancellable.getId(),
                cancellableStartAt,
                tooLate.getId(),
                otherPatient.getId(),
                completed.getId()
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
        service.setDescription("Dịch vụ kiểm thử hủy lịch");
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
            Instant scheduledStartAt,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
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

    private String statusOf(UUID appointmentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from appointments where id = ?",
                String.class,
                appointmentId
        );
    }

    private int cancelHistoryCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories
                where appointment_id = ? and action = 'CANCEL'
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
            UUID doctorId,
            DoctorService doctorService,
            UUID firstPatientUserId,
            String firstPatientEmail,
            Patient secondPatient,
            UUID cancellableAppointmentId,
            Instant cancellableStartAt,
            UUID tooLateAppointmentId,
            UUID otherPatientAppointmentId,
            UUID completedAppointmentId
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
