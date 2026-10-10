package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CompleteExaminationResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentCompleteExaminationService;
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
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
@Transactional
@Import(AppointmentCompleteExaminationPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AppointmentCompleteExaminationPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:45:00Z");
    private static final Instant ACTUAL_START_AT = Instant.parse("2026-10-12T02:15:00Z");

    @Autowired
    private AppointmentCompleteExaminationService completeExaminationService;

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
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-complete-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldCompleteAppointmentPersistActualEndAndWriteAudit() {
        CompleteExaminationResponse response = completeExaminationService.complete(
                fixture.doctorUserId(),
                fixture.inProgressAppointmentId()
        );

        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(response.actualStartAt().toString())
                .isEqualTo("2026-10-12T09:15+07:00");
        assertThat(response.actualEndAt().toString())
                .isEqualTo("2026-10-12T09:45+07:00");

        Map<String, Object> appointmentRow = jdbcTemplate.queryForMap("""
                select status::text as status, actual_start_at, actual_end_at
                from appointments
                where id = ?
                """, fixture.inProgressAppointmentId());
        assertThat(appointmentRow.get("status")).isEqualTo("COMPLETED");
        assertThat(((Timestamp) appointmentRow.get("actual_start_at")).toInstant())
                .isEqualTo(ACTUAL_START_AT);
        assertThat(((Timestamp) appointmentRow.get("actual_end_at")).toInstant())
                .isEqualTo(FIXED_NOW);

        Map<String, Object> historyRow = jdbcTemplate.queryForMap("""
                select action::text as action,
                       changed_by,
                       details ->> 'old_status' as old_status,
                       details ->> 'new_status' as new_status,
                       details ->> 'actual_start_at' as actual_start_at,
                       details ->> 'actual_end_at' as actual_end_at,
                       details ->> 'medical_record_id' as medical_record_id,
                       details ->> 'follow_up_required' as follow_up_required
                from appointment_histories
                where appointment_id = ? and action = 'COMPLETE'
                """, fixture.inProgressAppointmentId());
        assertThat(historyRow.get("action")).isEqualTo("COMPLETE");
        assertThat(historyRow.get("changed_by")).isEqualTo(fixture.doctorUserId());
        assertThat(historyRow.get("old_status")).isEqualTo("IN_PROGRESS");
        assertThat(historyRow.get("new_status")).isEqualTo("COMPLETED");
        assertThat(historyRow.get("actual_start_at"))
                .isEqualTo("2026-10-12T09:15+07:00");
        assertThat(historyRow.get("actual_end_at"))
                .isEqualTo("2026-10-12T09:45+07:00");
        assertThat(historyRow.get("medical_record_id"))
                .isEqualTo(fixture.medicalRecordId().toString());
        assertThat(historyRow.get("follow_up_required")).isEqualTo("true");
    }

    @Test
    void shouldRejectCompletionWithoutMedicalRecordAndKeepAppointmentInProgress() {
        Appointment appointment = appointmentRepository.findById(
                fixture.inProgressAppointmentId()
        ).orElseThrow();
        medicalRecordRepository.deleteById(fixture.medicalRecordId());
        medicalRecordRepository.flush();

        assertThatThrownBy(() -> completeExaminationService.complete(
                fixture.doctorUserId(),
                fixture.inProgressAppointmentId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Phải có bệnh án hợp lệ trước khi hoàn tất khám");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        assertThat(appointment.getActualEndAt()).isNull();
        assertThat(completeHistoryCount(fixture.inProgressAppointmentId())).isZero();
    }

    @Test
    void shouldRejectCompletionForAppointmentOutsideDoctorOwnership() {
        assertThatThrownBy(() -> completeExaminationService.complete(
                fixture.otherDoctorUserId(),
                fixture.inProgressAppointmentId()
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");

        assertThat(statusOf(fixture.inProgressAppointmentId()))
                .isEqualTo("IN_PROGRESS");
        assertThat(completeHistoryCount(fixture.inProgressAppointmentId())).isZero();
    }

    @Test
    void shouldRejectRepeatedCompletionWithoutCreatingDuplicateAudit() {
        completeExaminationService.complete(
                fixture.doctorUserId(),
                fixture.inProgressAppointmentId()
        );

        assertThatThrownBy(() -> completeExaminationService.complete(
                fixture.doctorUserId(),
                fixture.inProgressAppointmentId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ có thể hoàn tất lịch hẹn đang ở trạng thái IN_PROGRESS");

        assertThat(statusOf(fixture.inProgressAppointmentId())).isEqualTo("COMPLETED");
        assertThat(completeHistoryCount(fixture.inProgressAppointmentId())).isEqualTo(1);
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "phase20-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 20",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE20-A-" + marker);
        User otherDoctorUser = saveUser(
                "phase20-other-doctor-" + marker + "@example.com",
                "Bác sĩ khác Phase 20",
                Role.DOCTOR
        );
        Doctor otherDoctor = saveDoctor(otherDoctorUser, "PHASE20-B-" + marker);
        User patientUser = saveUser(
                "phase20-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 20",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        DentalService service = saveService("Dịch vụ Phase 20 " + marker);
        DoctorService mapping = saveMapping(doctor, service);
        saveMapping(otherDoctor, service);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(Instant.parse("2026-10-12T02:00:00Z"));
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        appointment.setCheckedInAt(Instant.parse("2026-10-12T01:55:00Z"));
        appointment.setActualStartAt(ACTUAL_START_AT);
        appointment = appointmentRepository.saveAndFlush(appointment);

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setAppointment(appointment);
        medicalRecord.setDiagnosis("Viêm tủy");
        medicalRecord.setTreatment("Điều trị tủy lần 1");
        medicalRecord.setTreatmentPlan("Điều trị tủy lần 2");
        medicalRecord.setPrescription(JsonNodeFactory.instance.arrayNode());
        medicalRecord.setFollowUpRequired(true);
        medicalRecord.setRecommendedFollowUpDate(LocalDate.of(2026, 10, 20));
        medicalRecord.setFollowUpNote("Tiếp tục điều trị tủy");
        medicalRecord = medicalRecordRepository.saveAndFlush(medicalRecord);

        return new Fixture(
                doctorUser.getId(),
                otherDoctorUser.getId(),
                appointment.getId(),
                medicalRecord.getId()
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
        service.setDescription("Dịch vụ kiểm thử hoàn tất khám");
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

    private String statusOf(UUID appointmentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from appointments where id = ?",
                String.class,
                appointmentId
        );
    }

    private int completeHistoryCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment_histories
                where appointment_id = ? and action = 'COMPLETE'
                """, Integer.class, appointmentId);
        return count == null ? 0 : count;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            UUID doctorUserId,
            UUID otherDoctorUserId,
            UUID inProgressAppointmentId,
            UUID medicalRecordId
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
