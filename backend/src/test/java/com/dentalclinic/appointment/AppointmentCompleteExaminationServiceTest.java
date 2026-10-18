package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CompleteExaminationResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentCompleteExaminationService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentItemSnapshot;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.service.PaymentService;
import com.dentalclinic.user.entity.User;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentCompleteExaminationServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:45:00Z");

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private PaymentService paymentService;

    private AppointmentCompleteExaminationService completeExaminationService;

    @BeforeEach
    void setUp() {
        completeExaminationService = new AppointmentCompleteExaminationService(
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                medicalRecordRepository,
                paymentService,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE))
        );
    }

    @Test
    void shouldCompleteOwnedInProgressAppointmentAndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        MedicalRecord medicalRecord = validMedicalRecord(appointment);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.of(medicalRecord));
        Payment payment = stubValidPayment(appointment);

        CompleteExaminationResponse response = completeExaminationService.complete(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(appointment.getActualEndAt()).isEqualTo(FIXED_NOW);
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(response.actualStartAt().toString())
                .isEqualTo("2026-10-12T09:15+07:00");
        assertThat(response.actualEndAt().toString())
                .isEqualTo("2026-10-12T09:45+07:00");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.COMPLETE);
        assertThat(history.getChangedBy()).isSameAs(doctor.getUser());
        assertThat(history.getDetails().path("old_status").asText())
                .isEqualTo("IN_PROGRESS");
        assertThat(history.getDetails().path("new_status").asText())
                .isEqualTo("COMPLETED");
        assertThat(history.getDetails().path("actual_end_at").asText())
                .isEqualTo("2026-10-12T09:45+07:00");
        assertThat(history.getDetails().path("medical_record_id").asText())
                .isEqualTo(medicalRecord.getId().toString());
        assertThat(history.getDetails().path("follow_up_required").asBoolean()).isFalse();
        assertThat(history.getDetails().path("payment_id").asText())
                .isEqualTo(payment.getId().toString());
        assertThat(history.getDetails().path("payment_status").asText())
                .isEqualTo("UNPAID");
    }

    @Test
    void shouldAllowCompletionWhenValidFollowUpIsNotBookedYet() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        MedicalRecord medicalRecord = validMedicalRecord(appointment);
        medicalRecord.setFollowUpRequired(true);
        medicalRecord.setRecommendedFollowUpDate(LocalDate.of(2026, 10, 20));
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.of(medicalRecord));
        stubValidPayment(appointment);

        CompleteExaminationResponse response = completeExaminationService.complete(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getDetails()
                .path("follow_up_required").asBoolean()).isTrue();
    }

    @Test
    void shouldRejectCompletionWhenAppointmentIsNotInProgress() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.CHECKED_IN);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> completeExaminationService.complete(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ có thể hoàn tất lịch hẹn đang ở trạng thái IN_PROGRESS");

        verify(medicalRecordRepository, never()).findByAppointmentId(any());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectCompletionWithoutMedicalRecord() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> completeExaminationService.complete(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Phải có bệnh án hợp lệ trước khi hoàn tất khám");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        assertThat(appointment.getActualEndAt()).isNull();
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectCompletionWithInvalidMedicalRecord() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        MedicalRecord medicalRecord = validMedicalRecord(appointment);
        medicalRecord.setDiagnosis(" ");
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.of(medicalRecord));

        assertThatThrownBy(() -> completeExaminationService.complete(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Phải có bệnh án hợp lệ trước khi hoàn tất khám");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectCompletionWhenActualStartIsMissing() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        appointment.setActualStartAt(null);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> completeExaminationService.complete(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Lịch hẹn chưa có thời gian bắt đầu khám");

        verify(medicalRecordRepository, never()).findByAppointmentId(any());
    }

    @Test
    void shouldNotRevealAnotherDoctorsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(appointmentId, doctor.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> completeExaminationService.complete(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldReportMissingDoctorProfile() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> completeExaminationService.complete(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ bác sĩ");

        verify(appointmentRepository, never()).findForUpdateByIdAndDoctorId(any(), any());
    }

    private void stubOwnedAppointment(
            UUID authenticatedUserId,
            Doctor doctor,
            Appointment appointment
    ) {
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
    }

    private Doctor doctor(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setFullName("Bác sĩ kiểm thử");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        return doctor;
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setScheduledStartAt(Instant.parse("2026-10-12T02:00:00Z"));
        appointment.setCheckedInAt(Instant.parse("2026-10-12T01:55:00Z"));
        appointment.setActualStartAt(Instant.parse("2026-10-12T02:15:00Z"));
        appointment.setStatus(status);
        return appointment;
    }

    private MedicalRecord validMedicalRecord(Appointment appointment) {
        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setId(UUID.randomUUID());
        medicalRecord.setAppointment(appointment);
        medicalRecord.setDiagnosis("Viêm tủy");
        medicalRecord.setTreatment("Điều trị tủy lần 1");
        medicalRecord.setPrescription(JsonNodeFactory.instance.arrayNode());
        medicalRecord.setFollowUpRequired(false);
        return medicalRecord;
    }

    private Payment stubValidPayment(Appointment appointment) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setAppointment(appointment);
        payment.setItems(List.of(new PaymentItemSnapshot(
                UUID.randomUUID(),
                "Khám tổng quát",
                new BigDecimal("100000.00"),
                1,
                new BigDecimal("100000.00"),
                false
        )));
        payment.setTotalAmount(new BigDecimal("100000.00"));
        payment.setStatus(PaymentStatus.UNPAID);
        when(paymentService.finalizeDraftForCompletion(appointment)).thenReturn(payment);
        return payment;
    }
}
