package com.dentalclinic.dashboard;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentDailySummary;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
@Import(AdminDashboardPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AdminDashboardPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");
    private static final Instant TODAY_START = Instant.parse("2026-10-11T17:00:00Z");
    private static final Instant TOMORROW_START = Instant.parse("2026-10-12T17:00:00Z");

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

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "admin-dashboard-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        long baselineDoctors = doctorRepository.count();
        AppointmentDailySummary baselineDaily = appointmentRepository
                .summarizeScheduledRange(
                        TODAY_START,
                        TOMORROW_START,
                        AppointmentStatus.CHECKED_IN,
                        AppointmentStatus.IN_PROGRESS,
                        AppointmentStatus.COMPLETED,
                        AppointmentStatus.CANCELLED,
                        AppointmentStatus.NO_SHOW
                );
        fixture = createFixture(
                baselineDoctors,
                baselineDaily.getPatientsVisitedToday(),
                baselineDaily.getTodayAppointments(),
                baselineDaily.getCompletedToday(),
                baselineDaily.getCancelledToday(),
                baselineDaily.getNoShowToday()
        );
    }

    @Test
    void shouldAggregateDashboardUsingClinicDateBoundaries() throws Exception {
        String token = login(fixture.adminEmail());

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientsVisitedToday")
                        .value(fixture.expectedPatientsVisitedToday()))
                .andExpect(jsonPath("$.data.totalDoctors")
                        .value(fixture.expectedDoctors()))
                .andExpect(jsonPath("$.data.todayAppointments")
                        .value(fixture.expectedTodayAppointments()))
                .andExpect(jsonPath("$.data.completedToday")
                        .value(fixture.expectedCompletedToday()))
                .andExpect(jsonPath("$.data.cancelledToday")
                        .value(fixture.expectedCancelledToday()))
                .andExpect(jsonPath("$.data.noShowToday")
                        .value(fixture.expectedNoShowToday()));
    }

    private Fixture createFixture(
            long baselineDoctors,
            long baselinePatientsVisitedToday,
            long baselineTodayAppointments,
            long baselineCompletedToday,
            long baselineCancelledToday,
            long baselineNoShowToday
    ) {
        String marker = UUID.randomUUID().toString();
        User admin = saveUser(
                "phase24-admin-" + marker + "@example.com",
                "Quản trị viên Phase 24",
                Role.ADMIN
        );
        User patientUser = saveUser(
                "phase24-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 24",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        User secondPatientUser = saveUser(
                "phase24-second-patient-" + marker + "@example.com",
                "Bệnh nhân thứ hai Phase 24",
                Role.PATIENT
        );
        Patient secondPatient = savePatient(secondPatientUser);
        User doctorUser = saveUser(
                "phase24-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 24",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE24-" + marker);
        DentalService service = saveService("Dịch vụ Phase 24 " + marker);
        DoctorService mapping = saveMapping(doctor, service);

        saveAppointment(patient, mapping, TODAY_START, AppointmentStatus.BOOKED);
        saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-11T19:00:00Z"),
                AppointmentStatus.CHECKED_IN
        );
        saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-11T21:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        saveAppointment(
                secondPatient,
                mapping,
                Instant.parse("2026-10-11T23:00:00Z"),
                AppointmentStatus.IN_PROGRESS
        );
        saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-12T01:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-12T03:00:00Z"),
                AppointmentStatus.CANCELLED
        );
        saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-12T16:59:59Z"),
                AppointmentStatus.NO_SHOW
        );

        saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-11T16:59:59Z"),
                AppointmentStatus.COMPLETED
        );
        saveAppointment(
                patient,
                mapping,
                TOMORROW_START,
                AppointmentStatus.NO_SHOW
        );

        return new Fixture(
                admin.getEmail(),
                baselinePatientsVisitedToday + 2,
                baselineDoctors + 1,
                baselineTodayAppointments + 7,
                baselineCompletedToday + 2,
                baselineCancelledToday + 1,
                baselineNoShowToday + 1
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
        doctor.setRating(new BigDecimal("4.8"));
        doctor.setExperienceYears(12);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử dashboard");
        service.setPrice(new BigDecimal("450000"));
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
            long expectedPatientsVisitedToday,
            long expectedDoctors,
            long expectedTodayAppointments,
            long expectedCompletedToday,
            long expectedCancelledToday,
            long expectedNoShowToday
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, CLINIC_ZONE);
        }
    }
}
