package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CreateFollowUpAppointmentRequest;
import com.dentalclinic.appointment.dto.FollowUpAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.FollowUpAppointmentService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.user.entity.User;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpAppointmentServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");
    private static final LocalDate FOLLOW_UP_DATE = LocalDate.of(2026, 10, 20);

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private SlotService slotService;

    private FollowUpAppointmentService followUpAppointmentService;

    @BeforeEach
    void setUp() {
        followUpAppointmentService = new FollowUpAppointmentService(
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                medicalRecordRepository,
                slotService,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                60
        );
    }

    @Test
    void shouldReturnAvailableSlotsForOwnedEligibleAppointment() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        AvailableSlotResponse slot = new AvailableSlotResponse(
                FOLLOW_UP_DATE,
                ScheduleSession.MORNING,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                OffsetDateTime.parse("2026-10-20T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-20T10:00:00+07:00")
        );
        stubDetailedSource(fixture);
        when(slotService.getAvailableSlots(
                fixture.doctor().getId(),
                fixture.service().getId(),
                FOLLOW_UP_DATE
        )).thenReturn(List.of(slot));

        List<AvailableSlotResponse> result = followUpAppointmentService.getFollowUpSlots(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                fixture.service().getId(),
                FOLLOW_UP_DATE
        );

        assertThat(result).containsExactly(slot);
        verify(slotService).getAvailableSlots(
                fixture.doctor().getId(),
                fixture.service().getId(),
                FOLLOW_UP_DATE
        );
    }

    @Test
    void shouldAllowFollowUpSelectionWhileExaminationIsInProgress() {
        Fixture fixture = fixture(AppointmentStatus.IN_PROGRESS, true);
        stubDetailedSource(fixture);
        when(slotService.getAvailableSlots(
                fixture.doctor().getId(),
                fixture.service().getId(),
                FOLLOW_UP_DATE
        )).thenReturn(List.of());

        assertThat(followUpAppointmentService.getFollowUpSlots(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                fixture.service().getId(),
                FOLLOW_UP_DATE
        )).isEmpty();
    }

    @Test
    void shouldCreateBookedFollowUpLinkedToPreviousAppointment() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        CreateFollowUpAppointmentRequest request = request(fixture.service().getId());
        stubLockedSource(fixture);
        when(slotService.requireAvailableSlot(
                fixture.doctor().getId(),
                fixture.service().getId(),
                request.scheduledStartAt().toInstant()
        )).thenReturn(fixture.mapping());
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(UUID.randomUUID());
            appointment.setCreatedAt(Instant.parse("2026-10-12T03:01:00Z"));
            return appointment;
        });
        when(appointmentHistoryRepository.saveAndFlush(any(AppointmentHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FollowUpAppointmentResponse response = followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request
        );

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(appointmentCaptor.capture());
        Appointment followUp = appointmentCaptor.getValue();
        assertThat(followUp.getPatient()).isSameAs(fixture.patient());
        assertThat(followUp.getDoctorService()).isSameAs(fixture.mapping());
        assertThat(followUp.getPreviousAppointment()).isSameAs(fixture.previousAppointment());
        assertThat(followUp.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(followUp.getScheduledStartAt())
                .isEqualTo(Instant.parse("2026-10-20T02:00:00Z"));

        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(followUp);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.BOOK);
        assertThat(history.getChangedBy()).isSameAs(fixture.doctor().getUser());
        assertThat(history.getDetails().path("booking_type").asText())
                .isEqualTo("FOLLOW_UP");
        assertThat(history.getDetails().path("previous_appointment_id").asText())
                .isEqualTo(fixture.previousAppointment().getId().toString());

        assertThat(response.previousAppointmentId())
                .isEqualTo(fixture.previousAppointment().getId());
        assertThat(response.patientId()).isEqualTo(fixture.patient().getId());
        assertThat(response.doctorId()).isEqualTo(fixture.doctor().getId());
        assertThat(response.serviceId()).isEqualTo(fixture.service().getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.scheduledStartAt().toString())
                .isEqualTo("2026-10-20T09:00+07:00");
        assertThat(response.scheduledEndAt().toString())
                .isEqualTo("2026-10-20T10:00+07:00");
    }

    @Test
    void shouldRejectFollowUpForAppointmentWithoutFollowUpRequirement() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, false);
        stubLockedSource(fixture);

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request(fixture.service().getId())
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Bệnh án không yêu cầu tái khám");

        verify(slotService, never()).requireAvailableSlot(any(), any(), any());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldRejectFollowUpWhenMedicalRecordIsMissing() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        when(doctorRepository.findForUpdateByUserId(fixture.doctor().getUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.previousAppointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.previousAppointment()));
        when(medicalRecordRepository.findByAppointmentId(fixture.previousAppointment().getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request(fixture.service().getId())
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Lịch hẹn chưa có bệnh án yêu cầu tái khám");
    }

    @Test
    void shouldRejectFollowUpForInvalidPreviousAppointmentStatus() {
        Fixture fixture = fixture(AppointmentStatus.BOOKED, true);
        when(doctorRepository.findForUpdateByUserId(fixture.doctor().getUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.previousAppointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.previousAppointment()));

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request(fixture.service().getId())
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ có thể đặt tái khám cho lịch hẹn đang khám hoặc đã hoàn tất");

        verify(medicalRecordRepository, never()).findByAppointmentId(any());
    }

    @Test
    void shouldNotRevealAnotherDoctorsAppointment() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        when(doctorRepository.findForUpdateByUserId(fixture.doctor().getUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.previousAppointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request(fixture.service().getId())
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldMapPostgreSqlUniqueViolationToSlotConflict() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        CreateFollowUpAppointmentRequest request = request(fixture.service().getId());
        stubLockedSource(fixture);
        when(slotService.requireAvailableSlot(
                fixture.doctor().getId(),
                fixture.service().getId(),
                request.scheduledStartAt().toInstant()
        )).thenReturn(fixture.mapping());
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "unique constraint",
                        new SQLException("duplicate key", "23505")
                ));

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác");

        verify(appointmentHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotMaskOtherDatabaseIntegrityFailures() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        CreateFollowUpAppointmentRequest request = request(fixture.service().getId());
        stubLockedSource(fixture);
        when(slotService.requireAvailableSlot(
                fixture.doctor().getId(),
                fixture.service().getId(),
                request.scheduledStartAt().toInstant()
        )).thenReturn(fixture.mapping());
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "foreign key",
                new SQLException("foreign key violation", "23503")
        );
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenThrow(failure);

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request
        )).isSameAs(failure);
    }

    @Test
    void shouldRejectFollowUpWhenDoctorIsOffWithoutCreatingAppointment() {
        Fixture fixture = fixture(AppointmentStatus.COMPLETED, true);
        CreateFollowUpAppointmentRequest request = request(fixture.service().getId());
        stubLockedSource(fixture);
        when(slotService.requireAvailableSlot(
                fixture.doctor().getId(),
                fixture.service().getId(),
                request.scheduledStartAt().toInstant()
        )).thenThrow(new ConflictException(
                "Bác sĩ đã nghỉ trong khung giờ này. Vui lòng chọn khung giờ khác"
        ));

        assertThatThrownBy(() -> followUpAppointmentService.createFollowUp(
                fixture.doctor().getUser().getId(),
                fixture.previousAppointment().getId(),
                request
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Bác sĩ đã nghỉ");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    private void stubDetailedSource(Fixture fixture) {
        when(doctorRepository.findByUserId(fixture.doctor().getUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findDetailedByIdAndDoctorId(
                fixture.previousAppointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.previousAppointment()));
        when(medicalRecordRepository.findByAppointmentId(fixture.previousAppointment().getId()))
                .thenReturn(Optional.of(fixture.medicalRecord()));
    }

    private void stubLockedSource(Fixture fixture) {
        when(doctorRepository.findForUpdateByUserId(fixture.doctor().getUser().getId()))
                .thenReturn(Optional.of(fixture.doctor()));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                fixture.previousAppointment().getId(),
                fixture.doctor().getId()
        )).thenReturn(Optional.of(fixture.previousAppointment()));
        when(medicalRecordRepository.findByAppointmentId(fixture.previousAppointment().getId()))
                .thenReturn(Optional.of(fixture.medicalRecord()));
    }

    private CreateFollowUpAppointmentRequest request(UUID serviceId) {
        return new CreateFollowUpAppointmentRequest(
                serviceId,
                OffsetDateTime.parse("2026-10-20T09:00:00+07:00")
        );
    }

    private Fixture fixture(AppointmentStatus status, boolean followUpRequired) {
        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());

        DentalService service = new DentalService();
        service.setId(UUID.randomUUID());
        service.setName("Điều trị tủy");
        service.setPrice(new BigDecimal("500000"));
        service.setActive(true);

        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);

        Appointment previousAppointment = new Appointment();
        previousAppointment.setId(UUID.randomUUID());
        previousAppointment.setPatient(patient);
        previousAppointment.setDoctorService(mapping);
        previousAppointment.setScheduledStartAt(Instant.parse("2026-10-12T02:00:00Z"));
        previousAppointment.setStatus(status);

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setId(UUID.randomUUID());
        medicalRecord.setAppointment(previousAppointment);
        medicalRecord.setDiagnosis("Viêm tủy");
        medicalRecord.setTreatment("Điều trị tủy lần 1");
        medicalRecord.setPrescription(JsonNodeFactory.instance.arrayNode());
        medicalRecord.setFollowUpRequired(followUpRequired);
        if (followUpRequired) {
            medicalRecord.setRecommendedFollowUpDate(FOLLOW_UP_DATE);
        }

        return new Fixture(doctor, patient, service, mapping, previousAppointment, medicalRecord);
    }

    private record Fixture(
            Doctor doctor,
            Patient patient,
            DentalService service,
            DoctorService mapping,
            Appointment previousAppointment,
            MedicalRecord medicalRecord
    ) {
    }
}
