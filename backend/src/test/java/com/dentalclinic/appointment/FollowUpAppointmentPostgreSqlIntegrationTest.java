package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CreateFollowUpAppointmentRequest;
import com.dentalclinic.appointment.dto.FollowUpAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.FollowUpAppointmentService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
@Transactional
@Import(FollowUpAppointmentPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class FollowUpAppointmentPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");
    private static final LocalDate FOLLOW_UP_DATE = LocalDate.of(2026, 10, 20);

    @Autowired
    private FollowUpAppointmentService followUpAppointmentService;

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
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "follow-up-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.slot-duration-minutes", () -> 60);
        registry.add("clinic.appointment.booking-min-advance-minutes", () -> 180);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldReturnAvailableFollowUpSlotsAndExcludeOccupiedSlot() {
        List<AvailableSlotResponse> slots = followUpAppointmentService.getFollowUpSlots(
                fixture.doctorUserId(),
                fixture.previousAppointmentId(),
                fixture.serviceId(),
                FOLLOW_UP_DATE
        );

        assertThat(slots).extracting(AvailableSlotResponse::startTime)
                .containsExactly(
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0)
                );
        assertThat(slots).extracting(slot -> slot.startAt().toString())
                .containsExactly(
                        "2026-10-20T08:00+07:00",
                        "2026-10-20T09:00+07:00",
                        "2026-10-20T11:00+07:00"
                );
    }

    @Test
    void shouldCreateBookedFollowUpWithPreviousAppointmentAndDoctorAudit() {
        OffsetDateTime scheduledStartAt = OffsetDateTime.parse(
                "2026-10-20T09:00:00+07:00"
        );

        FollowUpAppointmentResponse response = followUpAppointmentService.createFollowUp(
                fixture.doctorUserId(),
                fixture.previousAppointmentId(),
                new CreateFollowUpAppointmentRequest(
                        fixture.serviceId(),
                        scheduledStartAt
                )
        );

        assertThat(response.previousAppointmentId()).isEqualTo(fixture.previousAppointmentId());
        assertThat(response.patientId()).isEqualTo(fixture.patientId());
        assertThat(response.doctorId()).isEqualTo(fixture.doctorId());
        assertThat(response.serviceId()).isEqualTo(fixture.serviceId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.scheduledStartAt()).isEqualTo(scheduledStartAt);
        assertThat(response.scheduledEndAt()).isEqualTo(scheduledStartAt.plusHours(1));

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select patient_id,
                       doctor_id,
                       service_id,
                       previous_appointment_id,
                       scheduled_start_at,
                       status::text as status
                from appointments
                where id = ?
                """, response.id());
        assertThat(appointmentRow.get("patient_id")).isEqualTo(fixture.patientId());
        assertThat(appointmentRow.get("doctor_id")).isEqualTo(fixture.doctorId());
        assertThat(appointmentRow.get("service_id")).isEqualTo(fixture.serviceId());
        assertThat(appointmentRow.get("previous_appointment_id"))
                .isEqualTo(fixture.previousAppointmentId());
        assertThat(((Timestamp) appointmentRow.get("scheduled_start_at")).toInstant())
                .isEqualTo(scheduledStartAt.toInstant());
        assertThat(appointmentRow.get("status")).isEqualTo("BOOKED");

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       details ->> 'booking_type' as booking_type,
                       details ->> 'previous_appointment_id' as previous_appointment_id,
                       details ->> 'scheduled_start_at' as scheduled_start_at
                from appointment_histories
                where appointment_id = ?
                """, response.id());
        assertThat(historyRow.get("action")).isEqualTo("BOOK");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.doctorUserId());
        assertThat(historyRow.get("booking_type")).isEqualTo("FOLLOW_UP");
        assertThat(historyRow.get("previous_appointment_id"))
                .isEqualTo(fixture.previousAppointmentId().toString());
        assertThat(historyRow.get("scheduled_start_at"))
                .isEqualTo("2026-10-20T09:00+07:00");
    }

    @Test
    void shouldRejectOccupiedFollowUpSlotAndCreateNoAppointment() {
        long countBefore = appointmentCount();

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctorUserId(),
                fixture.previousAppointmentId(),
                new CreateFollowUpAppointmentRequest(
                        fixture.serviceId(),
                        OffsetDateTime.parse("2026-10-20T10:00:00+07:00")
                )
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác");

        assertThat(appointmentCount()).isEqualTo(countBefore);
        assertThat(followUpCount(fixture.previousAppointmentId())).isZero();
    }

    @Test
    void shouldRejectFollowUpWhenMedicalRecordDoesNotRequireIt() {
        MedicalRecord medicalRecord = medicalRecordRepository
                .findByAppointmentId(fixture.previousAppointmentId())
                .orElseThrow();
        medicalRecord.setFollowUpRequired(false);
        medicalRecord.setRecommendedFollowUpDate(null);
        medicalRecord.setFollowUpNote(null);
        medicalRecordRepository.saveAndFlush(medicalRecord);

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctorUserId(),
                fixture.previousAppointmentId(),
                new CreateFollowUpAppointmentRequest(
                        fixture.serviceId(),
                        OffsetDateTime.parse("2026-10-20T09:00:00+07:00")
                )
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Bệnh án không yêu cầu tái khám");

        assertThat(followUpCount(fixture.previousAppointmentId())).isZero();
    }

    @Test
    void shouldNotAllowAnotherDoctorToCreateFollowUp() {
        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.otherDoctorUserId(),
                fixture.previousAppointmentId(),
                new CreateFollowUpAppointmentRequest(
                        fixture.serviceId(),
                        OffsetDateTime.parse("2026-10-20T09:00:00+07:00")
                )
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");

        assertThat(followUpCount(fixture.previousAppointmentId())).isZero();
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "phase21-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 21",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE21-A-" + marker);
        User otherDoctorUser = saveUser(
                "phase21-other-doctor-" + marker + "@example.com",
                "Bác sĩ khác Phase 21",
                Role.DOCTOR
        );
        saveDoctor(otherDoctorUser, "PHASE21-B-" + marker);
        User patientUser = saveUser(
                "phase21-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 21",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        DentalService service = saveService("Điều trị tủy Phase 21 " + marker);
        DoctorService mapping = saveMapping(doctor, service);
        saveSchedule(doctor);

        Appointment previousAppointment = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-12T00:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        previousAppointment.setCheckedInAt(Instant.parse("2026-10-11T23:55:00Z"));
        previousAppointment.setActualStartAt(Instant.parse("2026-10-12T00:05:00Z"));
        previousAppointment.setActualEndAt(Instant.parse("2026-10-12T00:45:00Z"));
        appointmentRepository.saveAndFlush(previousAppointment);
        saveMedicalRecord(previousAppointment);

        saveAppointment(
                patient,
                mapping,
                toInstant(FOLLOW_UP_DATE, LocalTime.of(10, 0)),
                AppointmentStatus.BOOKED
        );

        return new Fixture(
                doctorUser.getId(),
                otherDoctorUser.getId(),
                doctor.getId(),
                patient.getId(),
                service.getId(),
                previousAppointment.getId()
        );
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash("integration-test-password-hash");
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
        service.setDescription("Dịch vụ kiểm thử lịch tái khám");
        service.setPrice(new BigDecimal("500000"));
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
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) FOLLOW_UP_DATE.getDayOfWeek().getValue());
        schedule.setSession(ScheduleSession.MORNING);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(12, 0));
        doctorScheduleRepository.saveAndFlush(schedule);
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

    private void saveMedicalRecord(Appointment appointment) {
        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setAppointment(appointment);
        medicalRecord.setDiagnosis("Viêm tủy");
        medicalRecord.setTreatment("Điều trị tủy lần 1");
        medicalRecord.setTreatmentPlan("Điều trị tủy lần 2");
        medicalRecord.setPrescription(JsonNodeFactory.instance.arrayNode());
        medicalRecord.setFollowUpRequired(true);
        medicalRecord.setRecommendedFollowUpDate(FOLLOW_UP_DATE);
        medicalRecord.setFollowUpNote("Tiếp tục điều trị tủy");
        medicalRecordRepository.saveAndFlush(medicalRecord);
    }

    private long appointmentCount() {
        Long count = jdbcTemplate.queryForObject("select count(*) from appointments", Long.class);
        return count == null ? 0 : count;
    }

    private int followUpCount(UUID previousAppointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointments
                where previous_appointment_id = ?
                """, Integer.class, previousAppointmentId);
        return count == null ? 0 : count;
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            UUID doctorUserId,
            UUID otherDoctorUserId,
            UUID doctorId,
            UUID patientId,
            UUID serviceId,
            UUID previousAppointmentId
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
