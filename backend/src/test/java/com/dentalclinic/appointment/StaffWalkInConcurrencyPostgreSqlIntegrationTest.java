package com.dentalclinic.appointment;

import com.dentalclinic.auth.security.UserPrincipal;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Import(StaffWalkInConcurrencyPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class StaffWalkInConcurrencyPostgreSqlIntegrationTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:00:00Z");

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @AfterEach
    void cleanFixture() {
        if (fixture == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    delete from appointment_histories
                    where appointment_id in (
                        select id from appointments where doctor_id in (?, ?)
                    )
                    """, fixture.firstDoctorId(), fixture.secondDoctorId());
            jdbcTemplate.update(
                    "delete from appointments where doctor_id in (?, ?)",
                    fixture.firstDoctorId(),
                    fixture.secondDoctorId()
            );
            jdbcTemplate.update(
                    "delete from doctor_schedules where doctor_id in (?, ?)",
                    fixture.firstDoctorId(),
                    fixture.secondDoctorId()
            );
            jdbcTemplate.update(
                    "delete from doctor_services where doctor_id in (?, ?)",
                    fixture.firstDoctorId(),
                    fixture.secondDoctorId()
            );
            jdbcTemplate.update(
                    "delete from patients where id in (?, ?)",
                    fixture.firstPatientId(),
                    fixture.secondPatientId()
            );
            jdbcTemplate.update(
                    "delete from doctors where id in (?, ?)",
                    fixture.firstDoctorId(),
                    fixture.secondDoctorId()
            );
            jdbcTemplate.update(
                    "delete from dental_services where id = ?",
                    fixture.serviceId()
            );
            jdbcTemplate.update(
                    "delete from users where id in (?, ?, ?)",
                    fixture.staff().getId(),
                    fixture.firstDoctorUserId(),
                    fixture.secondDoctorUserId()
            );
        });
        fixture = null;
    }

    @Test
    void shouldAllowOnlyOneConcurrentWalkInForSameDoctorQueue() throws Exception {
        fixture = createFixture();
        byte[] firstRequest = requestBody(
                fixture.firstPatientId(),
                fixture.firstDoctorId(),
                2
        );
        byte[] secondRequest = requestBody(
                fixture.secondPatientId(),
                fixture.firstDoctorId(),
                2
        );

        List<ConcurrentResult> results = executeConcurrently(firstRequest, secondRequest);

        assertOneCreatedAndOneConflict(results);
        assertThat(countActiveDoctorQueue(
                fixture.firstDoctorId(),
                ScheduleSession.MORNING,
                2
        )).isEqualTo(1);
        assertThat(countWalkInBookHistories()).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyOneConcurrentWalkInForSamePatientAcrossDoctors() throws Exception {
        fixture = createFixture();
        byte[] firstRequest = requestBody(
                fixture.firstPatientId(),
                fixture.firstDoctorId(),
                1
        );
        byte[] secondRequest = requestBody(
                fixture.firstPatientId(),
                fixture.secondDoctorId(),
                2
        );

        List<ConcurrentResult> results = executeConcurrently(firstRequest, secondRequest);

        assertOneCreatedAndOneConflict(results);
        assertThat(countActivePatientSession(
                fixture.firstPatientId(),
                ScheduleSession.MORNING
        )).isEqualTo(1);
        assertThat(countWalkInBookHistories()).isEqualTo(1);
    }

    private List<ConcurrentResult> executeConcurrently(
            byte[] firstRequest,
            byte[] secondRequest
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        UserPrincipal staffPrincipal = UserPrincipal.from(fixture.staff());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ConcurrentResult>> futures = List.of(
                    executor.submit(() -> concurrentCreateStatus(
                            staffPrincipal,
                            firstRequest,
                            ready,
                            start
                    )),
                    executor.submit(() -> concurrentCreateStatus(
                            staffPrincipal,
                            secondRequest,
                            ready,
                            start
                    ))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ConcurrentResult> results = new ArrayList<>();
            for (Future<ConcurrentResult> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ConcurrentResult concurrentCreateStatus(
            UserPrincipal staffPrincipal,
            byte[] requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Không nhận được tín hiệu bắt đầu kiểm thử đồng thời");
        }
        MvcResult result = mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .with(user(staffPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new ConcurrentResult(
                result.getResponse().getStatus(),
                body.path("code").asText(),
                body.path("message").asText()
        );
    }

    private void assertOneCreatedAndOneConflict(List<ConcurrentResult> results) {
        List<Integer> statuses = results.stream()
                .map(ConcurrentResult::status)
                .sorted()
                .toList();
        assertThat(statuses).containsExactly(201, 409);

        ConcurrentResult conflict = results.stream()
                .filter(result -> result.status() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(conflict.code()).isEqualTo("RESOURCE_CONFLICT");
        assertThat(conflict.message()).isIn(
                "Ca khám vừa được đặt. Vui lòng chọn ca khác.",
                "Bệnh nhân đã có một lịch khám đang hoạt động trong buổi này"
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String marker = UUID.randomUUID().toString();
            User staff = saveUser(
                    "walk-in-concurrency-staff-" + marker + "@example.com",
                    "Nhân viên concurrency",
                    Role.STAFF
            );
            User firstDoctorUser = saveUser(
                    "walk-in-concurrency-doctor-a-" + marker + "@example.com",
                    "Bác sĩ concurrency A",
                    Role.DOCTOR
            );
            Doctor firstDoctor = saveDoctor(firstDoctorUser, "WALK-CON-A-" + marker);
            User secondDoctorUser = saveUser(
                    "walk-in-concurrency-doctor-b-" + marker + "@example.com",
                    "Bác sĩ concurrency B",
                    Role.DOCTOR
            );
            Doctor secondDoctor = saveDoctor(secondDoctorUser, "WALK-CON-B-" + marker);
            DentalService service = saveService("Dịch vụ concurrency " + marker);
            saveMapping(firstDoctor, service);
            saveMapping(secondDoctor, service);
            saveSchedule(firstDoctor);
            saveSchedule(secondDoctor);
            Patient firstPatient = savePatient("Bệnh nhân concurrency A " + marker);
            Patient secondPatient = savePatient("Bệnh nhân concurrency B " + marker);

            return new Fixture(
                    staff,
                    firstDoctorUser.getId(),
                    firstDoctor.getId(),
                    secondDoctorUser.getId(),
                    secondDoctor.getId(),
                    service.getId(),
                    firstPatient.getId(),
                    secondPatient.getId()
            );
        });
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-walk-in-concurrency-test");
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

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử concurrency WALK_IN");
        service.setPrice(new BigDecimal("250000"));
        service.setActive(true);
        return dentalServiceRepository.saveAndFlush(service);
    }

    private void saveMapping(Doctor doctor, DentalService service) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);
        doctorServiceRepository.saveAndFlush(mapping);
    }

    private void saveSchedule(Doctor doctor) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) TODAY.getDayOfWeek().getValue());
        schedule.setSession(ScheduleSession.MORNING);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(11, 0));
        doctorScheduleRepository.saveAndFlush(schedule);
    }

    private Patient savePatient(String fullName) {
        Patient patient = new Patient();
        patient.setFullName(fullName);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private byte[] requestBody(
            UUID patientId,
            UUID doctorId,
            int queueNumber
    ) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "patientId", patientId,
                "doctorId", doctorId,
                "serviceId", fixture.serviceId(),
                "session", ScheduleSession.MORNING,
                "queueNumber", queueNumber
        ));
    }

    private int countActiveDoctorQueue(
            UUID doctorId,
            ScheduleSession session,
            int queueNumber
    ) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointments
                where doctor_id = ?
                  and appointment_date = ?
                  and session = cast(? as session_enum)
                  and queue_number = ?
                  and status in ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
                """, Integer.class, doctorId, TODAY, session.name(), queueNumber);
        return count == null ? 0 : count;
    }

    private int countActivePatientSession(
            UUID patientId,
            ScheduleSession session
    ) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointments
                where patient_id = ?
                  and appointment_date = ?
                  and session = cast(? as session_enum)
                  and status in ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
                """, Integer.class, patientId, TODAY, session.name());
        return count == null ? 0 : count;
    }

    private int countWalkInBookHistories() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories history
                join appointments appointment on appointment.id = history.appointment_id
                where appointment.doctor_id in (?, ?)
                  and appointment.booking_source = 'WALK_IN'
                  and history.action = 'BOOK'
                """, Integer.class, fixture.firstDoctorId(), fixture.secondDoctorId());
        return count == null ? 0 : count;
    }

    private record ConcurrentResult(int status, String code, String message) {
    }

    private record Fixture(
            User staff,
            UUID firstDoctorUserId,
            UUID firstDoctorId,
            UUID secondDoctorUserId,
            UUID secondDoctorId,
            UUID serviceId,
            UUID firstPatientId,
            UUID secondPatientId
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
