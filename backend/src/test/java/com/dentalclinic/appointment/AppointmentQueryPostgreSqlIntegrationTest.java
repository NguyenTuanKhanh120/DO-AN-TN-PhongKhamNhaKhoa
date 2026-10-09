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
import java.time.LocalDate;
import java.time.LocalTime;
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
@Import(AppointmentQueryPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentQueryPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
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

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-query-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.slot-duration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void patientShouldQueryOnlyOwnedAppointmentsAndReadOwnedDetail() throws Exception {
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(get("/api/patient/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("search", fixture.firstServiceSearchToken().toUpperCase())
                        .param("status", "BOOKED")
                        .param("doctorId", fixture.firstDoctorId().toString())
                        .param("serviceId", fixture.firstServiceId().toString())
                        .param("fromDate", TODAY.toString())
                        .param("toDate", TODAY.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(fixture.firstAppointmentId().toString()))
                .andExpect(jsonPath("$.data.content[0].patientId")
                        .value(fixture.firstPatientId().toString()))
                .andExpect(jsonPath("$.data.content[0].scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.content[0].scheduledEndAt")
                        .value("2026-10-12T10:00:00+07:00"));

        mockMvc.perform(get(
                        "/api/patient/appointments/{id}",
                        fixture.secondAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(fixture.secondAppointmentId().toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get(
                        "/api/patient/appointments/{id}",
                        fixture.otherPatientAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void doctorShouldQueryOnlyAssignedAppointmentsAndTodayInClinicTimezone() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(get("/api/doctor/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("status", "BOOKED")
                        .param("patientId", fixture.firstPatientId().toString())
                        .param("serviceId", fixture.firstServiceId().toString())
                        .param("fromDate", TODAY.toString())
                        .param("toDate", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(fixture.firstAppointmentId().toString()));

        mockMvc.perform(get("/api/doctor/appointments/today")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(fixture.firstAppointmentId().toString()))
                .andExpect(jsonPath("$.data.content[1].id")
                        .value(fixture.otherPatientAppointmentId().toString()))
                .andExpect(jsonPath("$.data.content[0].scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.content[1].scheduledStartAt")
                        .value("2026-10-12T11:00:00+07:00"));
    }

    @Test
    void adminShouldSearchFilterAndPageAllAppointments() throws Exception {
        String token = login(fixture.adminEmail());

        mockMvc.perform(get("/api/admin/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("doctorId", fixture.firstDoctorId().toString())
                        .param("fromDate", TODAY.toString())
                        .param("toDate", TODAY.toString())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(get("/api/admin/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("search", fixture.secondServiceSearchToken().toUpperCase())
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(fixture.secondAppointmentId().toString()))
                .andExpect(jsonPath("$.data.content[0].serviceName")
                        .value(fixture.secondServiceName()));

        mockMvc.perform(get("/api/admin/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("fromDate", "2026-10-13")
                        .param("toDate", "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        String firstServiceSearchToken = "phase13-service-a-" + marker;
        String secondServiceSearchToken = "phase13-service-b-" + marker;

        User admin = saveUser(
                "phase13-admin-" + marker + "@example.com",
                "Quản trị viên Phase 13",
                Role.ADMIN
        );
        User firstDoctorUser = saveUser(
                "phase13-doctor-a-" + marker + "@example.com",
                "Bác sĩ Nguyễn Minh Anh",
                Role.DOCTOR
        );
        Doctor firstDoctor = saveDoctor(firstDoctorUser, "PHASE13-A-" + marker);
        User secondDoctorUser = saveUser(
                "phase13-doctor-b-" + marker + "@example.com",
                "Bác sĩ Trần Minh Bình",
                Role.DOCTOR
        );
        Doctor secondDoctor = saveDoctor(secondDoctorUser, "PHASE13-B-" + marker);

        User firstPatientUser = saveUser(
                "phase13-patient-a-" + marker + "@example.com",
                "Bệnh nhân Một",
                Role.PATIENT
        );
        Patient firstPatient = savePatient(firstPatientUser, "0901000001");
        User secondPatientUser = saveUser(
                "phase13-patient-b-" + marker + "@example.com",
                "Bệnh nhân Hai",
                Role.PATIENT
        );
        Patient secondPatient = savePatient(secondPatientUser, "0901000002");

        DentalService firstService = saveService(
                "Trám răng " + firstServiceSearchToken
        );
        String secondServiceName = "Niềng răng " + secondServiceSearchToken;
        DentalService secondService = saveService(secondServiceName);
        DoctorService firstMapping = saveMapping(firstDoctor, firstService);
        DoctorService secondMapping = saveMapping(firstDoctor, secondService);
        DoctorService otherDoctorMapping = saveMapping(secondDoctor, firstService);

        Appointment firstAppointment = saveAppointment(
                firstPatient,
                firstMapping,
                toInstant(TODAY, LocalTime.of(9, 0)),
                AppointmentStatus.BOOKED
        );
        Appointment secondAppointment = saveAppointment(
                firstPatient,
                secondMapping,
                toInstant(TODAY.plusDays(1), LocalTime.of(10, 0)),
                AppointmentStatus.COMPLETED
        );
        Appointment otherPatientAppointment = saveAppointment(
                secondPatient,
                firstMapping,
                toInstant(TODAY, LocalTime.of(11, 0)),
                AppointmentStatus.CHECKED_IN
        );
        saveAppointment(
                secondPatient,
                otherDoctorMapping,
                toInstant(TODAY, LocalTime.of(12, 0)),
                AppointmentStatus.BOOKED
        );

        return new Fixture(
                admin.getEmail(),
                firstDoctorUser.getEmail(),
                firstDoctor.getId(),
                firstPatientUser.getEmail(),
                firstPatient.getId(),
                firstService.getId(),
                firstServiceSearchToken,
                secondServiceSearchToken,
                secondServiceName,
                firstAppointment.getId(),
                secondAppointment.getId(),
                otherPatientAppointment.getId()
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

    private Patient savePatient(User user, String phone) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        patient.setPhone(phone);
        return patientRepository.saveAndFlush(patient);
    }

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử PHASE 13");
        service.setPrice(new BigDecimal("300000"));
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

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
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
            String firstDoctorEmail,
            UUID firstDoctorId,
            String firstPatientEmail,
            UUID firstPatientId,
            UUID firstServiceId,
            String firstServiceSearchToken,
            String secondServiceSearchToken,
            String secondServiceName,
            UUID firstAppointmentId,
            UUID secondAppointmentId,
            UUID otherPatientAppointmentId
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
