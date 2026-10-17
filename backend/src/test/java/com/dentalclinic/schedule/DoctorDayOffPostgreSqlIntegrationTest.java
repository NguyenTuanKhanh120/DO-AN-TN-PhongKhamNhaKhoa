package com.dentalclinic.schedule;

import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.dto.BookAppointmentRequest;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentBookingService;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffRequest;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.DoctorDayOffPeriod;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorDayOffRepository;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.schedule.service.DoctorDayOffService;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("development")
@Import(DoctorDayOffPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class DoctorDayOffPostgreSqlIntegrationTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-17T00:00:00Z");
    private static final LocalDate OFF_DATE = LocalDate.of(2026, 10, 19);
    private static final String TEST_JWT_SECRET = generateSecret();

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
    private DoctorDayOffRepository doctorDayOffRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentHistoryRepository appointmentHistoryRepository;
    @Autowired
    private DoctorDayOffService doctorDayOffService;
    @Autowired
    private AppointmentBookingService appointmentBookingService;
    @Autowired
    private AppointmentQueryService appointmentQueryService;
    @Autowired
    private SlotService slotService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-day-off-integration-test");
    }

    @AfterEach
    void cleanUp() {
        if (fixture == null) {
            return;
        }
        UUID doctorId = fixture.doctorId();
        jdbcTemplate.update("delete from doctor_days_off where doctor_id = ?", doctorId);
        jdbcTemplate.update("""
                delete from appointment_histories
                where appointment_id in (select id from appointments where doctor_id = ?)
                """, doctorId);
        jdbcTemplate.update("delete from medical_records where appointment_id in "
                + "(select id from appointments where doctor_id = ?)", doctorId);
        jdbcTemplate.update("delete from appointments where doctor_id = ?", doctorId);
        jdbcTemplate.update("delete from doctor_schedules where doctor_id = ?", doctorId);
        jdbcTemplate.update("delete from doctor_services where doctor_id = ?", doctorId);
        jdbcTemplate.update("delete from doctors where id = ?", doctorId);
        jdbcTemplate.update("delete from patients where id = ?", fixture.patientId());
        jdbcTemplate.update("delete from dental_services where id = ?", fixture.serviceId());
        jdbcTemplate.update(
                "delete from users where id in (?, ?, ?)",
                fixture.adminUserId(), fixture.doctorUserId(), fixture.patientUserId()
        );
    }

    @Test
    void shouldPersistDayOffCancelBookedAuditPatientViewAndNeverRestoreOnDelete() {
        fixture = createFixture();
        Instant bookedStart = toInstant(OFF_DATE, LocalTime.of(9, 0));
        Instant completedStart = toInstant(OFF_DATE, LocalTime.of(10, 0));
        UUID bookedId = insertAppointment(bookedStart, AppointmentStatus.BOOKED);
        UUID completedId = insertAppointment(completedStart, AppointmentStatus.COMPLETED);

        CreateDoctorDayOffResponse response = doctorDayOffService.create(
                fixture.adminUserId(),
                fixture.doctorId(),
                new CreateDoctorDayOffRequest(
                        OFF_DATE,
                        DoctorDayOffPeriod.MORNING,
                        "Lý do nội bộ không công khai"
                )
        );

        assertThat(response.cancelledAppointmentCount()).isEqualTo(1);
        assertThat(appointmentRepository.findById(bookedId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointmentRepository.findById(completedId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(doctorScheduleRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(doctorDayOffRepository.findSessionsByDoctorIdAndOffDate(
                fixture.doctorId(), OFF_DATE
        )).containsExactly(ScheduleSession.MORNING);

        var histories = appointmentHistoryRepository.findByAppointmentIdsAndAction(
                List.of(bookedId), AppointmentAction.CANCEL
        );
        assertThat(histories).singleElement().satisfies(history -> {
            assertThat(history.getChangedBy().getId()).isEqualTo(fixture.adminUserId());
            assertThat(history.getReason()).isEqualTo(DoctorDayOffService.PUBLIC_CANCELLATION_REASON);
            assertThat(history.getDetails().path("source").asText()).isEqualTo("DOCTOR_DAY_OFF");
            assertThat(history.getDetails().path("off_date").asText())
                    .isEqualTo(OFF_DATE.toString());
        });

        AppointmentResponse patientView = appointmentQueryService.getPatientAppointment(
                fixture.patientUserId(), bookedId
        );
        assertThat(patientView.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(patientView.cancellationSource()).isEqualTo("DOCTOR_DAY_OFF");
        assertThat(patientView.cancellationReason())
                .isEqualTo(DoctorDayOffService.PUBLIC_CANCELLATION_REASON)
                .doesNotContain("Lý do nội bộ");

        assertThat(slotService.getAvailableSlots(
                fixture.doctorId(), fixture.serviceId(), OFF_DATE
        )).isEmpty();

        UUID dayOffId = response.createdDaysOff().getFirst().id();
        doctorDayOffService.delete(fixture.adminUserId(), fixture.doctorId(), dayOffId);

        assertThat(doctorDayOffRepository.findSessionsByDoctorIdAndOffDate(
                fixture.doctorId(), OFF_DATE
        )).isEmpty();
        assertThat(appointmentRepository.findById(bookedId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        List<AvailableSlotResponse> reopened = slotService.getAvailableSlots(
                fixture.doctorId(), fixture.serviceId(), OFF_DATE
        );
        assertThat(reopened).extracting(AvailableSlotResponse::startTime)
                .contains(LocalTime.of(9, 0))
                .doesNotContain(LocalTime.of(10, 0));
    }

    @Test
    void concurrentBookingAndDayOffMustNeverLeaveBookedAppointmentInsideDayOff() throws Exception {
        fixture = createFixture();
        Instant scheduledStart = toInstant(OFF_DATE, LocalTime.of(9, 0));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> booking = executor.submit(() -> runConcurrently(start, () ->
                    appointmentBookingService.book(
                            fixture.patientUserId(),
                            new BookAppointmentRequest(
                                    fixture.doctorId(),
                                    fixture.serviceId(),
                                    OffsetDateTime.ofInstant(scheduledStart, CLINIC_ZONE)
                            )
                    )
            ));
            Future<Throwable> dayOff = executor.submit(() -> runConcurrently(start, () ->
                    doctorDayOffService.create(
                            fixture.adminUserId(),
                            fixture.doctorId(),
                            new CreateDoctorDayOffRequest(
                                    OFF_DATE,
                                    DoctorDayOffPeriod.MORNING,
                                    "Kiểm thử cạnh tranh giao dịch"
                            )
                    )
            ));
            start.countDown();

            Throwable bookingFailure = booking.get();
            Throwable dayOffFailure = dayOff.get();
            assertThat(dayOffFailure).isNull();
            if (bookingFailure != null) {
                assertThat(bookingFailure).isInstanceOf(com.dentalclinic.common.exception.ConflictException.class);
            }
        }

        Integer dayOffCount = jdbcTemplate.queryForObject(
                "select count(*) from doctor_days_off where doctor_id = ? and off_date = ?",
                Integer.class,
                fixture.doctorId(), OFF_DATE
        );
        Integer bookedAtBlockedSlot = jdbcTemplate.queryForObject(
                """
                        select count(*) from appointments
                        where doctor_id = ? and scheduled_start_at = ? and status = 'BOOKED'
                        """,
                Integer.class,
                fixture.doctorId(), Timestamp.from(scheduledStart)
        );
        Integer totalAppointments = jdbcTemplate.queryForObject(
                "select count(*) from appointments where doctor_id = ? and scheduled_start_at = ?",
                Integer.class,
                fixture.doctorId(), Timestamp.from(scheduledStart)
        );

        assertThat(dayOffCount).isEqualTo(1);
        assertThat(bookedAtBlockedSlot).isZero();
        assertThat(totalAppointments).isIn(0, 1);
        if (totalAppointments == 1) {
            Integer cancellationHistory = jdbcTemplate.queryForObject(
                    """
                            select count(*) from appointment_histories history
                            join appointments appointment on appointment.id = history.appointment_id
                            where appointment.doctor_id = ?
                              and appointment.scheduled_start_at = ?
                              and history.action = 'CANCEL'
                              and history.details ->> 'source' = 'DOCTOR_DAY_OFF'
                            """,
                    Integer.class,
                    fixture.doctorId(), Timestamp.from(scheduledStart)
            );
            assertThat(cancellationHistory).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from doctor_schedules where doctor_id = ?",
                Integer.class,
                fixture.doctorId()
        )).isEqualTo(1);
    }

    private Throwable runConcurrently(CountDownLatch start, ThrowingOperation operation) {
        try {
            start.await();
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String marker = UUID.randomUUID().toString();
            User admin = saveUser("phase261-admin-" + marker + "@example.com", Role.ADMIN);
            User doctorUser = saveUser("phase261-doctor-" + marker + "@example.com", Role.DOCTOR);
            User patientUser = saveUser("phase261-patient-" + marker + "@example.com", Role.PATIENT);

            Doctor doctor = new Doctor();
            doctor.setUser(doctorUser);
            doctor.setSpecialization("Nha khoa tổng quát");
            doctor.setLicenseNumber("PHASE261-" + marker);
            doctor.setRating(new BigDecimal("4.5"));
            doctor.setExperienceYears(8);
            doctor.setWorkingStatus(DoctorStatus.ACTIVE);
            doctorRepository.saveAndFlush(doctor);

            Patient patient = new Patient();
            patient.setUser(patientUser);
            patient.setPhone("0901000001");
            patientRepository.saveAndFlush(patient);

            DentalService dentalService = new DentalService();
            dentalService.setName("Dịch vụ Phase 26.1 " + marker);
            dentalService.setDescription("Kiểm thử ngày nghỉ bác sĩ");
            dentalService.setPrice(new BigDecimal("300000"));
            dentalService.setActive(true);
            dentalServiceRepository.saveAndFlush(dentalService);

            DoctorService mapping = new DoctorService();
            mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
            mapping.setDoctor(doctor);
            mapping.setService(dentalService);
            doctorServiceRepository.saveAndFlush(mapping);

            DoctorSchedule schedule = new DoctorSchedule();
            schedule.setDoctor(doctor);
            schedule.setDayOfWeek((short) 1);
            schedule.setSession(ScheduleSession.MORNING);
            schedule.setStartTime(LocalTime.of(7, 0));
            schedule.setEndTime(LocalTime.of(12, 0));
            doctorScheduleRepository.saveAndFlush(schedule);

            return new Fixture(
                    admin.getId(),
                    doctorUser.getId(),
                    patientUser.getId(),
                    doctor.getId(),
                    patient.getId(),
                    dentalService.getId()
            );
        });
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$phase261.integration.test.hash");
        user.setFullName(role.name() + " Phase 26.1");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private UUID insertAppointment(Instant start, AppointmentStatus status) {
        return transactionTemplate.execute(transactionStatus -> {
            Appointment appointment = new Appointment();
            appointment.setPatient(patientRepository.findById(fixture.patientId()).orElseThrow());
            appointment.setDoctorService(doctorServiceRepository.findById(
                    new DoctorServiceId(fixture.doctorId(), fixture.serviceId())
            ).orElseThrow());
            appointment.setScheduledStartAt(start);
            appointment.setStatus(status);
            return appointmentRepository.saveAndFlush(appointment).getId();
        });
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private record Fixture(
            UUID adminUserId,
            UUID doctorUserId,
            UUID patientUserId,
            UUID doctorId,
            UUID patientId,
            UUID serviceId
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
