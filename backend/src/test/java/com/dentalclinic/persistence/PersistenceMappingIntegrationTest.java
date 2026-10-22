package com.dentalclinic.persistence;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.auth.entity.AuthSession;
import com.dentalclinic.auth.entity.AuthSessionStatus;
import com.dentalclinic.auth.entity.RefreshToken;
import com.dentalclinic.auth.repository.AuthSessionRepository;
import com.dentalclinic.auth.repository.RefreshTokenRepository;
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
import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentItemSnapshot;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.repository.PaymentRepository;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("development")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceMappingIntegrationTest {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final DoctorServiceRepository doctorServiceRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PaymentRepository paymentRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EntityManager entityManager;

    @Autowired
    PersistenceMappingIntegrationTest(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            DentalServiceRepository dentalServiceRepository,
            DoctorServiceRepository doctorServiceRepository,
            DoctorScheduleRepository doctorScheduleRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            MedicalRecordRepository medicalRecordRepository,
            PaymentRepository paymentRepository,
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            EntityManager entityManager
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.dentalServiceRepository = dentalServiceRepository;
        this.doctorServiceRepository = doctorServiceRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.paymentRepository = paymentRepository;
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.entityManager = entityManager;
    }

    @Test
    void persistsBusinessEntitiesWithPostgreSqlSpecificMappings() {
        String testId = UUID.randomUUID().toString();

        User patientUser = createUser("patient-" + testId + "@example.com", "Phase 02 Patient", Role.PATIENT);
        User doctorUser = createUser("doctor-" + testId + "@example.com", "Phase 02 Doctor", Role.DOCTOR);
        userRepository.save(patientUser);
        userRepository.saveAndFlush(doctorUser);

        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient.setDateOfBirth(LocalDate.of(1995, 6, 15));
        patient.setGender(Gender.FEMALE);
        patient.setPhone("0900000000");
        patient.setMedicalHistory("No chronic conditions");
        patientRepository.saveAndFlush(patient);

        Doctor doctor = new Doctor();
        doctor.setUser(doctorUser);
        doctor.setSpecialization("General Dentistry");
        doctor.setLicenseNumber("PHASE02-" + testId);
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        doctorRepository.saveAndFlush(doctor);

        DentalService dentalService = new DentalService();
        dentalService.setName("Phase 02 Service " + testId);
        dentalService.setDescription("Persistence mapping verification");
        dentalService.setPrice(new BigDecimal("250000.00"));
        dentalServiceRepository.saveAndFlush(dentalService);

        DoctorServiceId doctorServiceId = new DoctorServiceId(doctor.getId(), dentalService.getId());
        DoctorService doctorService = new DoctorService();
        doctorService.setId(doctorServiceId);
        doctorService.setDoctor(doctor);
        doctorService.setService(dentalService);
        doctorServiceRepository.saveAndFlush(doctorService);

        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) 1);
        schedule.setSession(ScheduleSession.MORNING);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(11, 0));
        doctorScheduleRepository.saveAndFlush(schedule);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(Instant.parse("2030-01-07T01:00:00Z"));
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointmentRepository.saveAndFlush(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.BOOK);
        history.setChangedBy(patientUser);
        history.setDetails(JsonNodeFactory.instance.objectNode().put("source", "phase02-test"));
        appointmentHistoryRepository.saveAndFlush(history);

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setAppointment(appointment);
        medicalRecord.setDiagnosis("Persistence test diagnosis");
        medicalRecord.setTreatment("Persistence test treatment");
        medicalRecord.setFollowUpRequired(false);
        ArrayNode prescription = JsonNodeFactory.instance.arrayNode();
        prescription.addObject()
                .put("medicine", "Test medicine")
                .put("dosage", "Once daily");
        medicalRecord.setPrescription(prescription);
        medicalRecordRepository.saveAndFlush(medicalRecord);

        Payment payment = new Payment();
        payment.setAppointment(appointment);
        payment.setItems(List.of(new PaymentItemSnapshot(
                dentalService.getId(),
                dentalService.getName(),
                new BigDecimal("250000.00"),
                1,
                new BigDecimal("250000.00"),
                false
        )));
        payment.setTotalAmount(new BigDecimal("250000.00"));
        payment.setStatus(PaymentStatus.DRAFT);
        paymentRepository.saveAndFlush(payment);

        Instant loggedInAt = Instant.parse("2030-01-01T00:00:00Z");
        AuthSession authSession = new AuthSession();
        authSession.setUserId(patientUser.getId());
        authSession.setRole(Role.PATIENT);
        authSession.setStatus(AuthSessionStatus.ACTIVE);
        authSession.setLoggedInAt(loggedInAt);
        authSession.setAbsoluteExpiresAt(loggedInAt.plusSeconds(7 * 24 * 60 * 60));
        authSession.setLastActivityAt(loggedInAt);
        authSession.setIdleTimeoutSeconds(null);
        authSession.setCsrfTokenHash("a".repeat(64));
        authSessionRepository.saveAndFlush(authSession);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setSession(authSession);
        refreshToken.setTokenHash("b".repeat(64));
        refreshToken.setIssuedAt(loggedInAt);
        refreshToken.setExpiresAt(authSession.getAbsoluteExpiresAt());
        refreshTokenRepository.saveAndFlush(refreshToken);

        UUID appointmentId = appointment.getId();
        UUID historyId = history.getId();
        UUID medicalRecordId = medicalRecord.getId();
        UUID paymentId = payment.getId();
        UUID authSessionId = authSession.getId();
        UUID refreshTokenId = refreshToken.getId();
        entityManager.clear();

        Appointment persistedAppointment = appointmentRepository.findById(appointmentId).orElseThrow();
        AppointmentHistory persistedHistory = appointmentHistoryRepository.findById(historyId).orElseThrow();
        MedicalRecord persistedRecord = medicalRecordRepository.findById(medicalRecordId).orElseThrow();
        DoctorSchedule persistedSchedule = doctorScheduleRepository.findById(schedule.getId()).orElseThrow();
        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();
        AuthSession persistedSession = authSessionRepository.findById(authSessionId).orElseThrow();
        RefreshToken persistedRefreshToken = refreshTokenRepository
                .findById(refreshTokenId)
                .orElseThrow();

        assertThat(persistedAppointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(persistedAppointment.getScheduledStartAt()).isEqualTo(Instant.parse("2030-01-07T01:00:00Z"));
        assertThat(persistedAppointment.getDoctorService().getId()).isEqualTo(doctorServiceId);
        assertThat(persistedAppointment.getCreatedAt()).isNotNull();
        assertThat(persistedAppointment.getUpdatedAt()).isNotNull();

        assertThat(persistedHistory.getAction()).isEqualTo(AppointmentAction.BOOK);
        assertThat(persistedHistory.getDetails().path("source").asText()).isEqualTo("phase02-test");
        assertThat(persistedHistory.getCreatedAt()).isNotNull();

        assertThat(persistedRecord.getPrescription().isArray()).isTrue();
        assertThat(persistedRecord.getPrescription().get(0).path("medicine").asText())
                .isEqualTo("Test medicine");
        assertThat(persistedRecord.getCreatedAt()).isNotNull();
        assertThat(persistedRecord.getUpdatedAt()).isNotNull();

        assertThat(persistedSchedule.getSession()).isEqualTo(ScheduleSession.MORNING);
        assertThat(persistedSchedule.getDoctor().getWorkingStatus()).isEqualTo(DoctorStatus.ACTIVE);
        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.DRAFT);
        assertThat(persistedPayment.getItems()).hasSize(1);
        assertThat(persistedPayment.getItems().getFirst().serviceName())
                .isEqualTo(dentalService.getName());
        assertThat(persistedPayment.getItems().getFirst().unitPrice())
                .isEqualByComparingTo("250000.00");
        assertThat(persistedPayment.getCreatedAt()).isNotNull();
        assertThat(persistedPayment.getUpdatedAt()).isNotNull();
        assertThat(persistedAppointment.getPatient().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(persistedSession.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(persistedSession.getAbsoluteExpiresAt())
                .isEqualTo(loggedInAt.plusSeconds(7 * 24 * 60 * 60));
        assertThat(persistedRefreshToken.getSession().getId()).isEqualTo(authSessionId);
        assertThat(persistedRefreshToken.getExpiresAt())
                .isEqualTo(persistedSession.getAbsoluteExpiresAt());
    }

    private User createUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$phase02.persistence.test.hash");
        user.setFullName(fullName);
        user.setRole(role);
        return user;
    }
}
