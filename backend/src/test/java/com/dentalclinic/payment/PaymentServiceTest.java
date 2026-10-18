package com.dentalclinic.payment;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.payment.dto.PaymentItemRequest;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.dto.UpdatePaymentItemsRequest;
import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentItemSnapshot;
import com.dentalclinic.payment.entity.PaymentMethod;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.mapper.PaymentMapper;
import com.dentalclinic.payment.repository.PaymentRepository;
import com.dentalclinic.payment.service.PaymentService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private DoctorServiceRepository doctorServiceRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;

    private ClinicTimeProvider timeProvider;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        timeProvider = new ClinicTimeProvider(Clock.fixed(NOW, CLINIC_ZONE));
        paymentService = new PaymentService(
                doctorRepository,
                appointmentRepository,
                doctorServiceRepository,
                patientRepository,
                paymentRepository,
                userRepository,
                new PaymentMapper(timeProvider),
                timeProvider
        );
    }

    @Test
    void shouldCreateDraftFromActualOriginalAndAdditionalServices() {
        Fixture fixture = fixture();
        stubDoctorUpdate(fixture, Optional.empty());
        when(doctorServiceRepository.findAllWithServiceByDoctorId(fixture.doctor().getId()))
                .thenReturn(List.of(fixture.originalMapping(), fixture.additionalMapping()));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });

        PaymentResponse response = paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of(
                        new PaymentItemRequest(fixture.originalService().getId(), 1),
                        new PaymentItemRequest(fixture.additionalService().getId(), 2)
                ))
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.DRAFT);
        assertThat(response.totalAmount()).isEqualByComparingTo("700000.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).additional()).isFalse();
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("100000.00");
        assertThat(response.items().get(1).additional()).isTrue();
        assertThat(response.items().get(1).subtotal()).isEqualByComparingTo("600000.00");
    }

    @Test
    void shouldAllowDoctorToRemoveEveryDraftItemBeforeCompletion() {
        Fixture fixture = fixture();
        Payment existing = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        stubDoctorUpdate(fixture, Optional.of(existing));
        when(doctorServiceRepository.findAllWithServiceByDoctorId(fixture.doctor().getId()))
                .thenReturn(List.of(fixture.originalMapping()));
        when(paymentRepository.saveAndFlush(existing)).thenReturn(existing);

        PaymentResponse response = paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(existing.getStatus()).isEqualTo(PaymentStatus.DRAFT);
    }

    @Test
    void shouldPreserveSnapshotPriceWhenCatalogPriceChanges() {
        Fixture fixture = fixture();
        Payment existing = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        fixture.originalService().setPrice(new BigDecimal("999000.00"));
        stubDoctorUpdate(fixture, Optional.of(existing));
        when(doctorServiceRepository.findAllWithServiceByDoctorId(fixture.doctor().getId()))
                .thenReturn(List.of(fixture.originalMapping()));
        when(paymentRepository.saveAndFlush(existing)).thenReturn(existing);

        PaymentResponse response = paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of(
                        new PaymentItemRequest(fixture.originalService().getId(), 2)
                ))
        );

        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("100000.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("200000.00");
    }

    @Test
    void shouldRejectDuplicateServiceIds() {
        Fixture fixture = fixture();
        stubDoctorUpdate(fixture, Optional.empty());
        UUID serviceId = fixture.originalService().getId();

        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of(
                        new PaymentItemRequest(serviceId, 1),
                        new PaymentItemRequest(serviceId, 2)
                ))
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Một dịch vụ không được xuất hiện nhiều lần trong phiếu");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectUnassignedOrUnknownService() {
        Fixture fixture = fixture();
        stubDoctorUpdate(fixture, Optional.empty());
        when(doctorServiceRepository.findAllWithServiceByDoctorId(fixture.doctor().getId()))
                .thenReturn(List.of(fixture.originalMapping()));

        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of(
                        new PaymentItemRequest(UUID.randomUUID(), 1)
                ))
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Dịch vụ không tồn tại hoặc chưa được phân công cho bác sĩ");
    }

    @Test
    void shouldRejectInactiveServiceWhenItIsAdded() {
        Fixture fixture = fixture();
        fixture.additionalService().setActive(false);
        stubDoctorUpdate(fixture, Optional.empty());
        when(doctorServiceRepository.findAllWithServiceByDoctorId(fixture.doctor().getId()))
                .thenReturn(List.of(fixture.originalMapping(), fixture.additionalMapping()));

        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of(
                        new PaymentItemRequest(fixture.additionalService().getId(), 1)
                ))
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Dịch vụ đã ngừng hoạt động");
    }

    @Test
    void shouldRejectEditingWhenAppointmentIsNotInProgress() {
        Fixture fixture = fixture();
        fixture.appointment().setStatus(AppointmentStatus.COMPLETED);
        when(doctorRepository.findByUserId(fixture.doctorUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.appointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.appointment()));

        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("IN_PROGRESS");

        verify(paymentRepository, never()).findForUpdateByAppointmentId(any());
    }

    @Test
    void shouldNotCreatePaymentForCancelledOrNoShowAppointment() {
        Fixture fixture = fixture();
        when(doctorRepository.findByUserId(fixture.doctorUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.appointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.appointment()));

        fixture.appointment().setStatus(AppointmentStatus.CANCELLED);
        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        )).isInstanceOf(ConflictException.class).hasMessageContaining("IN_PROGRESS");

        fixture.appointment().setStatus(AppointmentStatus.NO_SHOW);
        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        )).isInstanceOf(ConflictException.class).hasMessageContaining("IN_PROGRESS");

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectEditingUnpaidOrPaidPayment() {
        Fixture fixture = fixture();
        Payment existing = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        existing.setStatus(PaymentStatus.UNPAID);
        stubDoctorUpdate(fixture, Optional.of(existing));

        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể chỉnh sửa dịch vụ sau khi phiếu thanh toán đã được chốt");

        existing.setStatus(PaymentStatus.PAID);
        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể chỉnh sửa dịch vụ sau khi phiếu thanh toán đã được chốt");
    }

    @Test
    void shouldNotRevealAnotherDoctorsAppointment() {
        Fixture fixture = fixture();
        when(doctorRepository.findByUserId(fixture.doctorUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.appointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.updateDoctorItems(
                fixture.doctorUser().getId(),
                fixture.appointment().getId(),
                new UpdatePaymentItemsRequest(List.of())
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldFinalizeValidDraftAsUnpaid() {
        Fixture fixture = fixture();
        Payment payment = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false),
                snapshot(fixture.additionalService(), "300000.00", 2, true)
        ));
        when(paymentRepository.findForUpdateByAppointmentId(fixture.appointment().getId()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        Payment finalized = paymentService.finalizeDraftForCompletion(fixture.appointment());

        assertThat(finalized.getStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(finalized.getTotalAmount()).isEqualByComparingTo("700000.00");
        assertThat(finalized.getPaymentMethod()).isNull();
        assertThat(finalized.getConfirmedBy()).isNull();
        assertThat(finalized.getPaidAt()).isNull();
    }

    @Test
    void shouldRejectCompletionWithoutPaymentOrWithEmptyItems() {
        Fixture fixture = fixture();
        when(paymentRepository.findForUpdateByAppointmentId(fixture.appointment().getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.finalizeDraftForCompletion(fixture.appointment()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Phải ghi nhận ít nhất một dịch vụ trước khi hoàn tất khám");

        Payment empty = draftPayment(fixture.appointment(), List.of());
        when(paymentRepository.findForUpdateByAppointmentId(fixture.appointment().getId()))
                .thenReturn(Optional.of(empty));
        assertThatThrownBy(() -> paymentService.finalizeDraftForCompletion(fixture.appointment()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Phiếu thanh toán phải có ít nhất một dịch vụ thực tế");
    }

    @Test
    void shouldRejectCompletionWhenStoredTotalWasTampered() {
        Fixture fixture = fixture();
        Payment payment = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        payment.setTotalAmount(new BigDecimal("1.00"));
        when(paymentRepository.findForUpdateByAppointmentId(fixture.appointment().getId()))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.finalizeDraftForCompletion(fixture.appointment()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tổng tiền của phiếu thanh toán không hợp lệ");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DRAFT);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldConfirmUnpaidPaymentAsPaid() {
        Fixture fixture = fixture();
        fixture.appointment().setStatus(AppointmentStatus.COMPLETED);
        Payment payment = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        payment.setStatus(PaymentStatus.UNPAID);
        User admin = admin();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(paymentRepository.findForUpdateDetailedById(payment.getId()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.confirmPayment(
                admin.getId(),
                payment.getId(),
                PaymentMethod.CASH
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.confirmedById()).isEqualTo(admin.getId());
        assertThat(response.paidAt().toInstant()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectDraftAndAlreadyPaidConfirmation() {
        Fixture fixture = fixture();
        User admin = admin();
        Payment payment = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(paymentRepository.findForUpdateDetailedById(payment.getId()))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPayment(
                admin.getId(), payment.getId(), PaymentMethod.CASH
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("UNPAID");

        payment.setStatus(PaymentStatus.PAID);
        assertThatThrownBy(() -> paymentService.confirmPayment(
                admin.getId(), payment.getId(), PaymentMethod.BANK_TRANSFER
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Phiếu thanh toán đã được xác nhận trước đó");
    }

    @Test
    void shouldOnlyReturnOwnedFinalizedPatientPayment() {
        Fixture fixture = fixture();
        Payment payment = draftPayment(fixture.appointment(), List.of(
                snapshot(fixture.originalService(), "100000.00", 1, false)
        ));
        payment.setStatus(PaymentStatus.UNPAID);
        when(patientRepository.findByUserId(fixture.patientUser().getId()))
                .thenReturn(Optional.of(fixture.patient()));
        when(paymentRepository.findPatientPaymentDetail(
                payment.getId(),
                fixture.patient().getId(),
                PaymentStatus.DRAFT
        )).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPatientPayment(
                fixture.patientUser().getId(),
                payment.getId()
        );

        assertThat(response.patientId()).isEqualTo(fixture.patient().getId());
        assertThat(response.status()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void shouldNotRevealAnotherPatientsPayment() {
        Fixture fixture = fixture();
        UUID paymentId = UUID.randomUUID();
        when(patientRepository.findByUserId(fixture.patientUser().getId()))
                .thenReturn(Optional.of(fixture.patient()));
        when(paymentRepository.findPatientPaymentDetail(
                paymentId,
                fixture.patient().getId(),
                PaymentStatus.DRAFT
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPatientPayment(
                fixture.patientUser().getId(),
                paymentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy phiếu thanh toán");
    }

    @Test
    void shouldRejectInvalidAdminSortAndDateRange() {
        assertThatThrownBy(() -> paymentService.getAdminPayments(
                null,
                null,
                LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 20),
                0,
                20,
                "createdAt",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Từ ngày không được sau đến ngày");

        assertThatThrownBy(() -> paymentService.getAdminPayments(
                null, null, null, null, 0, 20, "patientName", null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Trường sắp xếp không hợp lệ");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldListAdminPaymentsWithPagination() {
        Fixture fixture = fixture();
        Payment payment = draftPayment(fixture.appointment(), List.of());
        when(paymentRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(payment)));

        var response = paymentService.getAdminPayments(
                "bệnh nhân",
                PaymentStatus.DRAFT,
                null,
                null,
                0,
                20,
                "totalAmount",
                null
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo(payment.getId());
    }

    private void stubDoctorUpdate(Fixture fixture, Optional<Payment> payment) {
        when(doctorRepository.findByUserId(fixture.doctorUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.appointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.appointment()));
        when(paymentRepository.findForUpdateByAppointmentId(fixture.appointment().getId()))
                .thenReturn(payment);
    }

    private Payment draftPayment(Appointment appointment, List<PaymentItemSnapshot> items) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setAppointment(appointment);
        payment.setItems(items);
        payment.setTotalAmount(items.stream()
                .map(PaymentItemSnapshot::subtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add));
        payment.setStatus(PaymentStatus.DRAFT);
        return payment;
    }

    private PaymentItemSnapshot snapshot(
            DentalService service,
            String price,
            int quantity,
            boolean additional
    ) {
        BigDecimal unitPrice = new BigDecimal(price);
        return new PaymentItemSnapshot(
                service.getId(),
                service.getName(),
                unitPrice,
                quantity,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                additional
        );
    }

    private User admin() {
        User admin = user(Role.ADMIN, "Quản trị viên");
        admin.setStatus(UserStatus.ACTIVE);
        return admin;
    }

    private Fixture fixture() {
        User patientUser = user(Role.PATIENT, "Bệnh nhân kiểm thử");
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(patientUser);
        patient.setPhone("0901000001");

        User doctorUser = user(Role.DOCTOR, "Bác sĩ kiểm thử");
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);

        DentalService original = dentalService("Khám tổng quát", "100000.00");
        DentalService additional = dentalService("Cạo vôi răng", "300000.00");
        DoctorService originalMapping = mapping(doctor, original);
        DoctorService additionalMapping = mapping(doctor, additional);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctorService(originalMapping);
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        appointment.setScheduledStartAt(Instant.parse("2026-10-20T02:00:00Z"));
        appointment.setActualStartAt(Instant.parse("2026-10-20T02:30:00Z"));
        return new Fixture(
                patientUser,
                patient,
                doctorUser,
                doctor,
                original,
                additional,
                originalMapping,
                additionalMapping,
                appointment
        );
    }

    private User user(Role role, String fullName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private DentalService dentalService(String name, String price) {
        DentalService service = new DentalService();
        service.setId(UUID.randomUUID());
        service.setName(name);
        service.setPrice(new BigDecimal(price));
        service.setActive(true);
        return service;
    }

    private DoctorService mapping(Doctor doctor, DentalService service) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);
        return mapping;
    }

    private record Fixture(
            User patientUser,
            Patient patient,
            User doctorUser,
            Doctor doctor,
            DentalService originalService,
            DentalService additionalService,
            DoctorService originalMapping,
            DoctorService additionalMapping,
            Appointment appointment
    ) {
    }
}
