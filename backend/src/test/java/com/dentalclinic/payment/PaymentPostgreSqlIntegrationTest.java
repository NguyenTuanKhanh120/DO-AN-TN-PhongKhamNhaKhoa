package com.dentalclinic.payment;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentCheckInService;
import com.dentalclinic.appointment.service.AppointmentCompleteExaminationService;
import com.dentalclinic.appointment.service.AppointmentStartExaminationService;
import com.dentalclinic.common.exception.ConflictException;
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
import com.dentalclinic.payment.dto.PaymentItemRequest;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.dto.UpdatePaymentItemsRequest;
import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentItemSnapshot;
import com.dentalclinic.payment.entity.PaymentMethod;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.repository.PaymentRepository;
import com.dentalclinic.payment.service.PaymentService;
import com.dentalclinic.payment.service.PaymentRevenueService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
class PaymentPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRevenueService paymentRevenueService;
    @Autowired
    private AppointmentCheckInService checkInService;
    @Autowired
    private AppointmentStartExaminationService startExaminationService;
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
    private PaymentRepository paymentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<Fixture> fixtures = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "payment-postgresql-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @AfterEach
    void cleanUp() {
        for (Fixture fixture : fixtures.reversed()) {
            jdbcTemplate.update(
                    "delete from appointment_histories where appointment_id = ?",
                    fixture.appointmentId()
            );
            jdbcTemplate.update(
                    "delete from payments where appointment_id = ?",
                    fixture.appointmentId()
            );
            jdbcTemplate.update(
                    "delete from medical_records where appointment_id = ?",
                    fixture.appointmentId()
            );
            jdbcTemplate.update("delete from appointments where id = ?", fixture.appointmentId());
            jdbcTemplate.update("delete from doctor_services where doctor_id = ?", fixture.doctorId());
            jdbcTemplate.update("delete from doctors where id = ?", fixture.doctorId());
            jdbcTemplate.update("delete from patients where id = ?", fixture.patientId());
            jdbcTemplate.update(
                    "delete from dental_services where id in (?, ?)",
                    fixture.originalServiceId(),
                    fixture.additionalServiceId()
            );
            jdbcTemplate.update(
                    "delete from users where id in (?, ?, ?, ?)",
                    fixture.doctorUserId(),
                    fixture.patientUserId(),
                    fixture.adminOneId(),
                    fixture.adminTwoId()
            );
        }
        fixtures.clear();
    }

    @Test
    void shouldPersistFullCounterPaymentFlowAndJsonSnapshots() throws Exception {
        Fixture fixture = createFixture(AppointmentStatus.BOOKED, null);

        checkInService.checkIn(fixture.doctorUserId(), fixture.appointmentId());
        startExaminationService.start(fixture.doctorUserId(), fixture.appointmentId());
        saveMedicalRecord(fixture.appointmentId());

        PaymentResponse draft = paymentService.updateDoctorItems(
                fixture.doctorUserId(),
                fixture.appointmentId(),
                new UpdatePaymentItemsRequest(List.of(
                        new PaymentItemRequest(fixture.originalServiceId(), 1),
                        new PaymentItemRequest(fixture.additionalServiceId(), 1)
                ))
        );
        assertThat(draft.status()).isEqualTo(PaymentStatus.DRAFT);
        assertThat(draft.totalAmount()).isEqualByComparingTo("400000.00");
        assertThat(draft.items()).extracting("additional")
                .containsExactly(false, true);

        completeExaminationService.complete(
                fixture.doctorUserId(),
                fixture.appointmentId()
        );
        assertThat(appointmentStatus(fixture.appointmentId())).isEqualTo("COMPLETED");
        assertThat(paymentStatus(draft.id())).isEqualTo("UNPAID");

        PaymentResponse paid = paymentService.confirmPayment(
                fixture.adminOneId(),
                draft.id(),
                PaymentMethod.BANK_TRANSFER
        );
        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.confirmedById()).isEqualTo(fixture.adminOneId());
        assertThat(paid.paidAt()).isNotNull();

        String itemsJson = jdbcTemplate.queryForObject(
                "select items::text from payments where id = ?",
                String.class,
                draft.id()
        );
        JsonNode items = objectMapper.readTree(itemsJson);
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("serviceName").asText()).startsWith("Khám tổng quát");
        assertThat(items.get(0).path("unitPrice").decimalValue())
                .isEqualByComparingTo("100000.00");
        assertThat(items.get(1).path("serviceName").asText()).startsWith("Cạo vôi răng");
        assertThat(items.get(1).path("additional").asBoolean()).isTrue();

        DentalService original = dentalServiceRepository.findById(
                fixture.originalServiceId()
        ).orElseThrow();
        original.setPrice(new BigDecimal("999000.00"));
        dentalServiceRepository.saveAndFlush(original);

        PaymentResponse patientView = paymentService.getPatientPayment(
                fixture.patientUserId(),
                draft.id()
        );
        assertThat(patientView.totalAmount()).isEqualByComparingTo("400000.00");
        assertThat(patientView.items().getFirst().unitPrice())
                .isEqualByComparingTo("100000.00");
        assertThat(patientView.doctorId()).isEqualTo(fixture.doctorId());

        var revenue = paymentRevenueService.getRevenue(null, null, null);
        assertThat(revenue.todayRevenue()).isGreaterThanOrEqualTo(new BigDecimal("400000.00"));
        assertThat(revenue.totalPaidPayments()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldRollbackPaymentFinalizationWhenCompletionFailsAfterFlush() {
        Instant futureStart = Instant.now().plus(2, ChronoUnit.HOURS);
        Fixture fixture = createFixture(AppointmentStatus.IN_PROGRESS, futureStart);
        saveMedicalRecord(fixture.appointmentId());
        Payment payment = saveDraftPayment(fixture);

        assertThatThrownBy(() -> completeExaminationService.complete(
                fixture.doctorUserId(),
                fixture.appointmentId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Thời gian hoàn tất khám không hợp lệ");

        assertThat(appointmentStatus(fixture.appointmentId())).isEqualTo("IN_PROGRESS");
        assertThat(paymentStatus(payment.getId())).isEqualTo("DRAFT");
        Integer completeHistories = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from appointment_histories
                        where appointment_id = ? and action = 'COMPLETE'
                        """,
                Integer.class,
                fixture.appointmentId()
        );
        assertThat(completeHistories).isZero();
    }

    @Test
    void shouldAllowOnlyOneConcurrentConfirmation() throws Exception {
        Fixture fixture = createFixture(AppointmentStatus.COMPLETED, Instant.now());
        Payment payment = saveDraftPayment(fixture);
        payment.setStatus(PaymentStatus.UNPAID);
        payment = paymentRepository.saveAndFlush(payment);

        UUID paymentId = payment.getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> confirmConcurrently(
                    fixture.adminOneId(), paymentId, ready, start
            ));
            Future<Object> second = executor.submit(() -> confirmConcurrently(
                    fixture.adminTwoId(), paymentId, ready, start
            ));
            ready.await();
            start.countDown();

            List<Object> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(PaymentResponse.class::isInstance).count())
                    .isEqualTo(1);
            assertThat(results.stream().filter(ConflictException.class::isInstance).count())
                    .isEqualTo(1);
        }

        assertThat(paymentStatus(paymentId)).isEqualTo("PAID");
        Integer paymentRows = jdbcTemplate.queryForObject(
                "select count(*) from payments where appointment_id = ?",
                Integer.class,
                fixture.appointmentId()
        );
        assertThat(paymentRows).isEqualTo(1);
        BigDecimal paidRevenue = jdbcTemplate.queryForObject(
                """
                        select coalesce(sum(total_amount), 0)
                        from payments
                        where id = ? and status = 'PAID'
                        """,
                BigDecimal.class,
                paymentId
        );
        assertThat(paidRevenue).isEqualByComparingTo("100000.00");
    }

    private Object confirmConcurrently(
            UUID adminId,
            UUID paymentId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            start.await();
            return paymentService.confirmPayment(adminId, paymentId, PaymentMethod.CASH);
        } catch (ConflictException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Fixture createFixture(AppointmentStatus status, Instant actualStartAt) {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser("payment-doctor-" + marker + "@example.com", Role.DOCTOR);
        User patientUser = saveUser("payment-patient-" + marker + "@example.com", Role.PATIENT);
        User adminOne = saveUser("payment-admin-one-" + marker + "@example.com", Role.ADMIN);
        User adminTwo = saveUser("payment-admin-two-" + marker + "@example.com", Role.ADMIN);

        Doctor doctor = new Doctor();
        doctor.setUser(doctorUser);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber("PAYMENT-" + marker);
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        doctor = doctorRepository.saveAndFlush(doctor);

        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient.setGender(Gender.UNSPECIFIED);
        patient.setPhone("0901000001");
        patient = patientRepository.saveAndFlush(patient);

        DentalService original = saveService("Khám tổng quát", "100000.00", marker);
        DentalService additional = saveService("Cạo vôi răng", "300000.00", marker);
        saveMapping(doctor, original);
        saveMapping(doctor, additional);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorServiceRepository.findById(
                new DoctorServiceId(doctor.getId(), original.getId())
        ).orElseThrow());
        appointment.setScheduledStartAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        appointment.setStatus(status);
        if (status == AppointmentStatus.IN_PROGRESS) {
            appointment.setCheckedInAt(Instant.now().minus(5, ChronoUnit.MINUTES));
            appointment.setActualStartAt(actualStartAt);
        } else if (status == AppointmentStatus.COMPLETED) {
            appointment.setCheckedInAt(Instant.now().minus(20, ChronoUnit.MINUTES));
            appointment.setActualStartAt(actualStartAt);
            appointment.setActualEndAt(Instant.now());
        }
        appointment = appointmentRepository.saveAndFlush(appointment);

        Fixture fixture = new Fixture(
                doctorUser.getId(),
                patientUser.getId(),
                adminOne.getId(),
                adminTwo.getId(),
                doctor.getId(),
                patient.getId(),
                original.getId(),
                additional.getId(),
                appointment.getId()
        );
        fixtures.add(fixture);
        return fixture;
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash("integration-test-password-hash");
        user.setFullName(role.name() + " Payment Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private DentalService saveService(String name, String price, String marker) {
        DentalService service = new DentalService();
        service.setName(name + " " + marker);
        service.setDescription("Payment integration test");
        service.setPrice(new BigDecimal(price));
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

    private MedicalRecord saveMedicalRecord(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        MedicalRecord record = new MedicalRecord();
        record.setAppointment(appointment);
        record.setDiagnosis("Viêm nướu");
        record.setTreatment("Khám và vệ sinh răng");
        record.setPrescription(JsonNodeFactory.instance.arrayNode());
        record.setFollowUpRequired(false);
        return medicalRecordRepository.saveAndFlush(record);
    }

    private Payment saveDraftPayment(Fixture fixture) {
        Appointment appointment = appointmentRepository.findById(
                fixture.appointmentId()
        ).orElseThrow();
        DentalService service = dentalServiceRepository.findById(
                fixture.originalServiceId()
        ).orElseThrow();
        Payment payment = new Payment();
        payment.setAppointment(appointment);
        payment.setItems(List.of(new PaymentItemSnapshot(
                service.getId(),
                service.getName(),
                new BigDecimal("100000.00"),
                1,
                new BigDecimal("100000.00"),
                false
        )));
        payment.setTotalAmount(new BigDecimal("100000.00"));
        payment.setStatus(PaymentStatus.DRAFT);
        return paymentRepository.saveAndFlush(payment);
    }

    private String appointmentStatus(UUID appointmentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from appointments where id = ?",
                String.class,
                appointmentId
        );
    }

    private String paymentStatus(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "select status::text from payments where id = ?",
                String.class,
                paymentId
        );
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            UUID doctorUserId,
            UUID patientUserId,
            UUID adminOneId,
            UUID adminTwoId,
            UUID doctorId,
            UUID patientId,
            UUID originalServiceId,
            UUID additionalServiceId,
            UUID appointmentId
    ) {
    }
}
