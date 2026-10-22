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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Import(AppointmentReschedulePostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentReschedulePostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:30:00Z");
    private static final String SLOT_CONFLICT_MESSAGE = "Khung giờ mới vừa được người khác đặt. "
            + "Lịch hiện tại của bạn vẫn được giữ nguyên. Vui lòng chọn khung giờ khác.";

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
    private AppointmentRepository appointmentRepository;

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
        registry.add("security.jwt.issuer", () -> "appointment-reschedule-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.reschedule-cutoff-minutes", () -> 90);
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
    void shouldRescheduleAtExactCutoffWriteAuditAndReleaseOldSlot() throws Exception {
        fixture = createFixture();
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        fixture.firstAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(fixture.targetStartAt(), "  Bận   việc gia đình  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi lịch hẹn thành công"))
                .andExpect(jsonPath("$.data.id")
                        .value(fixture.firstAppointmentId().toString()))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.oldScheduledStartAt")
                        .value("2026-10-12T11:00:00+07:00"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T10:00:00+07:00"))
                .andExpect(jsonPath("$.data.scheduledEndAt")
                        .value("2026-10-12T11:00:00+07:00"))
                .andExpect(jsonPath("$.data.reason").value("Bận việc gia đình"));

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select scheduled_start_at, status::text as status
                from appointments
                where id = ?
                """, fixture.firstAppointmentId());
        assertThat(((Timestamp) appointmentRow.get("scheduled_start_at")).toInstant())
                .isEqualTo(fixture.targetStartAt());
        assertThat(appointmentRow.get("status")).isEqualTo("BOOKED");

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       reason,
                       details ->> 'old_date' as old_date,
                       details ->> 'old_start_time' as old_start_time,
                       details ->> 'new_date' as new_date,
                       details ->> 'new_start_time' as new_start_time,
                       details ->> 'changed_at' as changed_at
                from appointment_histories
                where appointment_id = ? and action = 'RESCHEDULE'
                """, fixture.firstAppointmentId());
        assertThat(historyRow.get("action")).isEqualTo("RESCHEDULE");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.firstPatientUserId());
        assertThat(historyRow.get("reason")).isEqualTo("Bận việc gia đình");
        assertThat(historyRow.get("old_date")).isEqualTo("2026-10-12");
        assertThat(historyRow.get("old_start_time")).isEqualTo("11:00");
        assertThat(historyRow.get("new_date")).isEqualTo("2026-10-12");
        assertThat(historyRow.get("new_start_time")).isEqualTo("10:00");
        assertThat(historyRow.get("changed_at")).isEqualTo("2026-10-12T09:30+07:00");

        transactionTemplate.executeWithoutResult(status -> saveAppointment(
                patientRepository.findById(fixture.secondPatientId()).orElseThrow(),
                doctorServiceRepository.findById(new DoctorServiceId(
                        fixture.doctorId(),
                        fixture.serviceId()
                )).orElseThrow(),
                fixture.firstOldStartAt(),
                AppointmentStatus.BOOKED
        ));
        assertThat(countAppointmentsAt(fixture.firstOldStartAt())).isEqualTo(1);
    }

    @Test
    void shouldRejectBelowCutoffWithoutChangingAppointmentOrAudit() throws Exception {
        fixture = createFixture();
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        fixture.tooLateAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(fixture.targetStartAt(), "Không thể đến đúng giờ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Không thể đổi lịch vì lịch hẹn còn dưới 1 giờ 30 phút. "
                                + "Vui lòng giữ lịch hiện tại hoặc liên hệ phòng khám."
                ));

        assertThat(startOf(fixture.tooLateAppointmentId()))
                .isEqualTo(fixture.tooLateStartAt());
        assertThat(rescheduleHistoryCount(fixture.tooLateAppointmentId())).isZero();
    }

    @Test
    void shouldNotAllowPatientToRescheduleAnotherPatientsAppointment() throws Exception {
        fixture = createFixture();
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        fixture.secondAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(fixture.targetStartAt(), "Yêu cầu đổi lịch")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(startOf(fixture.secondAppointmentId()))
                .isEqualTo(fixture.secondOldStartAt());
        assertThat(rescheduleHistoryCount(fixture.secondAppointmentId())).isZero();
    }

    @Test
    void shouldRejectRescheduleWhenAppointmentIsNotBooked() throws Exception {
        fixture = createFixture();
        String token = login(fixture.firstPatientEmail());

        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        fixture.completedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(fixture.targetStartAt(), "Yêu cầu đổi lịch")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Chỉ có thể đổi lịch hẹn đang ở trạng thái BOOKED"));

        assertThat(statusOf(fixture.completedAppointmentId())).isEqualTo("COMPLETED");
        assertThat(rescheduleHistoryCount(fixture.completedAppointmentId())).isZero();
    }

    @Test
    void shouldAllowOnlyOneRescheduleAndRollbackLosersOldSlotDuringRace() throws Exception {
        fixture = createFixture();
        String firstToken = login(fixture.firstPatientEmail());
        String secondToken = login(fixture.secondPatientEmail());
        byte[] firstBody = rescheduleBody(fixture.targetStartAt(), "Bệnh nhân A đổi lịch");
        byte[] secondBody = rescheduleBody(fixture.targetStartAt(), "Bệnh nhân B đổi lịch");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> concurrentRescheduleStatus(
                            firstToken,
                            fixture.firstAppointmentId(),
                            firstBody,
                            ready,
                            start
                    )),
                    executor.submit(() -> concurrentRescheduleStatus(
                            secondToken,
                            fixture.secondAppointmentId(),
                            secondBody,
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

            assertThat(statuses).containsExactly(200, 409);
            assertThat(countAppointmentsAt(fixture.targetStartAt())).isEqualTo(1);

            Instant firstActual = startOf(fixture.firstAppointmentId());
            Instant secondActual = startOf(fixture.secondAppointmentId());
            boolean firstWon = firstActual.equals(fixture.targetStartAt())
                    && secondActual.equals(fixture.secondOldStartAt());
            boolean secondWon = secondActual.equals(fixture.targetStartAt())
                    && firstActual.equals(fixture.firstOldStartAt());
            assertThat(firstWon || secondWon).isTrue();
            assertThat(totalRescheduleHistoryCount()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int concurrentRescheduleStatus(
            String accessToken,
            UUID appointmentId,
            byte[] requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Không nhận được tín hiệu bắt đầu kiểm thử đồng thời");
        }
        return mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        appointmentId
                ).header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
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
                    "phase15-doctor-" + marker + "@example.com",
                    "Bác sĩ Phase 15",
                    Role.DOCTOR
            );
            Doctor doctor = saveDoctor(doctorUser, "PHASE15-" + marker);
            DentalService service = saveService("Dịch vụ Phase 15 " + marker);
            DoctorService mapping = saveMapping(doctor, service);
            saveSchedule(doctor);

            User firstPatientUser = saveUser(
                    "phase15-patient-a-" + marker + "@example.com",
                    "Bệnh nhân Phase 15 A",
                    Role.PATIENT
            );
            Patient firstPatient = savePatient(firstPatientUser);
            User secondPatientUser = saveUser(
                    "phase15-patient-b-" + marker + "@example.com",
                    "Bệnh nhân Phase 15 B",
                    Role.PATIENT
            );
            Patient secondPatient = savePatient(secondPatientUser);

            Instant firstOldStart = toInstant(LocalTime.of(11, 0));
            Instant secondOldStart = toInstant(LocalTime.of(13, 0));
            Instant tooLateStart = FIXED_NOW.plusSeconds(90 * 60L - 1);
            Appointment firstAppointment = saveAppointment(
                    firstPatient,
                    mapping,
                    firstOldStart,
                    AppointmentStatus.BOOKED
            );
            Appointment secondAppointment = saveAppointment(
                    secondPatient,
                    mapping,
                    secondOldStart,
                    AppointmentStatus.BOOKED
            );
            Appointment tooLateAppointment = saveAppointment(
                    firstPatient,
                    mapping,
                    tooLateStart,
                    AppointmentStatus.BOOKED
            );
            Appointment completedAppointment = saveAppointment(
                    firstPatient,
                    mapping,
                    toInstant(LocalTime.of(14, 0)),
                    AppointmentStatus.COMPLETED
            );

            return new Fixture(
                    doctorUser.getId(),
                    doctor.getId(),
                    service.getId(),
                    firstPatientUser.getId(),
                    firstPatient.getId(),
                    firstPatientUser.getEmail(),
                    secondPatientUser.getId(),
                    secondPatient.getId(),
                    secondPatientUser.getEmail(),
                    firstAppointment.getId(),
                    firstOldStart,
                    secondAppointment.getId(),
                    secondOldStart,
                    tooLateAppointment.getId(),
                    tooLateStart,
                    completedAppointment.getId(),
                    toInstant(LocalTime.of(10, 0))
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
        service.setDescription("Dịch vụ kiểm thử đổi lịch");
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

    private void saveSchedule(Doctor doctor) {
        DoctorSchedule morning = new DoctorSchedule();
        morning.setDoctor(doctor);
        morning.setDayOfWeek((short) 1);
        morning.setSession(ScheduleSession.MORNING);
        morning.setStartTime(LocalTime.of(8, 0));
        morning.setEndTime(LocalTime.of(11, 0));

        DoctorSchedule afternoon = new DoctorSchedule();
        afternoon.setDoctor(doctor);
        afternoon.setDayOfWeek((short) 1);
        afternoon.setSession(ScheduleSession.AFTERNOON);
        afternoon.setStartTime(LocalTime.of(13, 0));
        afternoon.setEndTime(LocalTime.of(17, 0));
        doctorScheduleRepository.saveAllAndFlush(List.of(morning, afternoon));
    }

    private Appointment saveAppointment(
            Patient patient,
            DoctorService doctorService,
            Instant startAt,
            AppointmentStatus appointmentStatus
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(startAt);
        appointment.setStatus(appointmentStatus);
        return appointmentRepository.saveAndFlush(appointment);
    }

    private byte[] rescheduleBody(Instant startAt, String reason) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "scheduledStartAt", startAt.atZone(CLINIC_ZONE).toOffsetDateTime(),
                "reason", reason
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

    private Instant startOf(UUID appointmentId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "select scheduled_start_at from appointments where id = ?",
                Timestamp.class,
                appointmentId
        );
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String statusOf(UUID appointmentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from appointments where id = ?",
                String.class,
                appointmentId
        );
    }

    private int countAppointmentsAt(Instant startAt) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointments
                where doctor_id = ? and scheduled_start_at = ? and status <> 'CANCELLED'
                """, Integer.class, fixture.doctorId(), Timestamp.from(startAt));
        return count == null ? 0 : count;
    }

    private int rescheduleHistoryCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories
                where appointment_id = ? and action = 'RESCHEDULE'
                """, Integer.class, appointmentId);
        return count == null ? 0 : count;
    }

    private int totalRescheduleHistoryCount() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories history
                join appointments appointment on appointment.id = history.appointment_id
                where appointment.doctor_id = ? and history.action = 'RESCHEDULE'
                """, Integer.class, fixture.doctorId());
        return count == null ? 0 : count;
    }

    private Instant toInstant(LocalTime time) {
        return MONDAY.atTime(time).atZone(CLINIC_ZONE).toInstant();
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
            String secondPatientEmail,
            UUID firstAppointmentId,
            Instant firstOldStartAt,
            UUID secondAppointmentId,
            Instant secondOldStartAt,
            UUID tooLateAppointmentId,
            Instant tooLateStartAt,
            UUID completedAppointmentId,
            Instant targetStartAt
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
