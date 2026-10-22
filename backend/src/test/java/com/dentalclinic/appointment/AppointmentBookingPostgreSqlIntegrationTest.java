package com.dentalclinic.appointment;

import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
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
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Import(AppointmentBookingPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentBookingPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
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
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-booking-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.slot-duration-minutes", () -> 60);
        registry.add("clinic.appointment.booking-min-advance-minutes", () -> 120);
    }

    @AfterEach
    void cleanFixture() {
        if (fixture == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    delete from appointment_histories
                    where appointment_id in (
                        select id from appointments where doctor_id = ?
                    )
                    """, fixture.doctorId());
            jdbcTemplate.update(
                    "delete from appointments where doctor_id = ?",
                    fixture.doctorId()
            );
            jdbcTemplate.update(
                    "delete from doctor_schedules where doctor_id = ?",
                    fixture.doctorId()
            );
            jdbcTemplate.update(
                    "delete from doctor_services where doctor_id = ? and service_id = ?",
                    fixture.doctorId(),
                    fixture.serviceId()
            );
            jdbcTemplate.update(
                    "delete from patients where id in (?, ?)",
                    fixture.firstPatientId(),
                    fixture.secondPatientId()
            );
            jdbcTemplate.update("delete from doctors where id = ?", fixture.doctorId());
            jdbcTemplate.update("delete from dental_services where id = ?", fixture.serviceId());
            jdbcTemplate.update(
                    "delete from users where id in (?, ?, ?)",
                    fixture.doctorUserId(),
                    fixture.firstPatientUserId(),
                    fixture.secondPatientUserId()
            );
        });
        fixture = null;
    }

    @Test
    void shouldBookAppointmentAndPersistBookAuditOnPostgreSql() throws Exception {
        fixture = createFixture();
        String firstPatientToken = login(fixture.firstPatientEmail());
        String secondPatientToken = login(fixture.secondPatientEmail());
        Instant scheduledStart = toInstant(MONDAY, LocalTime.of(13, 0));
        byte[] requestBody = bookingBody(scheduledStart);

        MvcResult created = mockMvc.perform(post("/api/patient/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstPatientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.patientId")
                        .value(fixture.firstPatientId().toString()))
                .andExpect(jsonPath("$.data.doctorId").value(fixture.doctorId().toString()))
                .andExpect(jsonPath("$.data.serviceId").value(fixture.serviceId().toString()))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T13:00:00+07:00"))
                .andExpect(jsonPath("$.data.scheduledEndAt")
                        .value("2026-10-12T14:00:00+07:00"))
                .andReturn();
        UUID appointmentId = responseAppointmentId(created);

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select patient_id, doctor_id, service_id, scheduled_start_at, status::text as status
                from appointments
                where id = ?
                """, appointmentId);
        assertThat(appointmentRow.get("patient_id")).isEqualTo(fixture.firstPatientId());
        assertThat(appointmentRow.get("doctor_id")).isEqualTo(fixture.doctorId());
        assertThat(appointmentRow.get("service_id")).isEqualTo(fixture.serviceId());
        assertThat(((Timestamp) appointmentRow.get("scheduled_start_at")).toInstant())
                .isEqualTo(scheduledStart);
        assertThat(appointmentRow.get("status")).isEqualTo("BOOKED");

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       details ->> 'status' as detail_status,
                       details ->> 'scheduled_start_at' as detail_start
                from appointment_histories
                where appointment_id = ?
                """, appointmentId);
        assertThat(historyRow.get("action")).isEqualTo("BOOK");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.firstPatientUserId());
        assertThat(historyRow.get("detail_status")).isEqualTo("BOOKED");
        assertThat(historyRow.get("detail_start")).isEqualTo("2026-10-12T13:00+07:00");

        mockMvc.perform(post("/api/patient/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondPatientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        assertThat(countAppointmentsAt(scheduledStart)).isEqualTo(1);
        assertThat(countBookHistories()).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyOnePatientWhenTwoPatientsBookSameSlotConcurrently() throws Exception {
        fixture = createFixture();
        String firstPatientToken = login(fixture.firstPatientEmail());
        String secondPatientToken = login(fixture.secondPatientEmail());
        Instant scheduledStart = toInstant(MONDAY, LocalTime.of(14, 0));
        byte[] requestBody = bookingBody(scheduledStart);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> concurrentBookingStatus(
                            firstPatientToken,
                            requestBody,
                            ready,
                            start
                    )),
                    executor.submit(() -> concurrentBookingStatus(
                            secondPatientToken,
                            requestBody,
                            ready,
                            start
                    ))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(20, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);

            assertThat(statuses).containsExactly(201, 409);
            assertThat(countAppointmentsAt(scheduledStart)).isEqualTo(1);
            assertThat(countBookHistories()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int concurrentBookingStatus(
            String accessToken,
            byte[] requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Không nhận được tín hiệu bắt đầu kiểm thử đồng thời");
        }
        return mockMvc.perform(post("/api/patient/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String marker = UUID.randomUUID().toString();
            User doctorUser = saveUser(
                    "phase12-doctor-" + marker + "@example.com",
                    "Bác sĩ Booking",
                    Role.DOCTOR
            );
            Doctor doctor = saveDoctor(doctorUser, "PHASE12-" + marker);
            DentalService dentalService = saveDentalService("Dịch vụ Phase 12 " + marker);
            saveMapping(doctor, dentalService);
            saveSchedule(doctor);

            User firstPatientUser = saveUser(
                    "phase12-patient-a-" + marker + "@example.com",
                    "Bệnh nhân A",
                    Role.PATIENT
            );
            Patient firstPatient = savePatient(firstPatientUser);
            User secondPatientUser = saveUser(
                    "phase12-patient-b-" + marker + "@example.com",
                    "Bệnh nhân B",
                    Role.PATIENT
            );
            Patient secondPatient = savePatient(secondPatientUser);

            return new Fixture(
                    doctorUser.getId(),
                    doctor.getId(),
                    dentalService.getId(),
                    firstPatientUser.getId(),
                    firstPatient.getId(),
                    firstPatientUser.getEmail(),
                    secondPatientUser.getId(),
                    secondPatient.getId(),
                    secondPatientUser.getEmail()
            );
        });
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
        doctor.setBio("Bác sĩ kiểm thử PHASE 12");
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private DentalService saveDentalService(String name) {
        DentalService dentalService = new DentalService();
        dentalService.setName(name);
        dentalService.setDescription("Dịch vụ kiểm thử đặt lịch");
        dentalService.setPrice(new BigDecimal("300000"));
        dentalService.setActive(true);
        return dentalServiceRepository.saveAndFlush(dentalService);
    }

    private void saveMapping(Doctor doctor, DentalService dentalService) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        doctorServiceRepository.saveAndFlush(mapping);
    }

    private void saveSchedule(Doctor doctor) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) 1);
        schedule.setSession(ScheduleSession.AFTERNOON);
        schedule.setStartTime(LocalTime.of(13, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        doctorScheduleRepository.saveAndFlush(schedule);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private byte[] bookingBody(Instant scheduledStart) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "doctorId", fixture.doctorId(),
                "serviceId", fixture.serviceId(),
                "scheduledStartAt", scheduledStart.atZone(CLINIC_ZONE).toOffsetDateTime()
        ));
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

    private UUID responseAppointmentId(MvcResult result) throws Exception {
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsByteArray())
                        .path("data")
                        .path("id")
                        .asText()
        );
    }

    private int countAppointmentsAt(Instant scheduledStart) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointments
                where doctor_id = ? and scheduled_start_at = ? and status <> 'CANCELLED'
                """, Integer.class, fixture.doctorId(), Timestamp.from(scheduledStart));
        return count == null ? 0 : count;
    }

    private int countBookHistories() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories history
                join appointments appointment on appointment.id = history.appointment_id
                where appointment.doctor_id = ? and history.action = 'BOOK'
                """, Integer.class, fixture.doctorId());
        return count == null ? 0 : count;
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            UUID doctorUserId,
            UUID doctorId,
            UUID serviceId,
            UUID firstPatientUserId,
            UUID firstPatientId,
            String firstPatientEmail,
            UUID secondPatientUserId,
            UUID secondPatientId,
            String secondPatientEmail
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
