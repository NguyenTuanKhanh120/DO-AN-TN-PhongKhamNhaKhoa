package com.dentalclinic.medicalrecord;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.dto.MedicalRecordRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PrescriptionItem;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.mapper.MedicalRecordMapper;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.medicalrecord.service.MedicalRecordService;
import com.dentalclinic.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Clock;
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
class MedicalRecordServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    private MedicalRecordService medicalRecordService;

    @BeforeEach
    void setUp() {
        medicalRecordService = new MedicalRecordService(
                doctorRepository,
                appointmentRepository,
                medicalRecordRepository,
                new MedicalRecordMapper(new ClinicTimeProvider(
                        Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"))
                ))
        );
    }

    @Test
    void shouldCreateMedicalRecordForOwnedInProgressAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        MedicalRecordRequest request = request(true);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.existsByAppointmentId(appointment.getId()))
                .thenReturn(false);
        when(medicalRecordRepository.saveAndFlush(any(MedicalRecord.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        MedicalRecordResponse response = medicalRecordService.create(
                authenticatedUserId,
                appointment.getId(),
                request
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.appointmentId()).isEqualTo(appointment.getId());
        assertThat(response.diagnosis()).isEqualTo("Viêm tủy răng 36");
        assertThat(response.treatment()).isEqualTo("Điều trị tủy lần 1");
        assertThat(response.treatmentPlan()).isEqualTo("Điều trị tiếp lần 2");
        assertThat(response.notes()).isEqualTo("Hạn chế nhai bên đang điều trị");
        assertThat(response.followUpRequired()).isTrue();
        assertThat(response.recommendedFollowUpDate())
                .isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(response.followUpNote()).isEqualTo("Tiếp tục điều trị tủy");
        assertThat(response.prescription()).containsExactly(new PrescriptionItem(
                "Paracetamol 500mg",
                "1 viên",
                10,
                "Uống sau ăn khi đau"
        ));
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void shouldNormalizeMissingPrescriptionToEmptyJsonArray() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        MedicalRecordRequest request = new MedicalRecordRequest(
                "Khám định kỳ",
                "Vệ sinh răng",
                " ",
                null,
                null,
                false,
                null,
                null
        );
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.existsByAppointmentId(appointment.getId()))
                .thenReturn(false);
        when(medicalRecordRepository.saveAndFlush(any(MedicalRecord.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        MedicalRecordResponse response = medicalRecordService.create(
                authenticatedUserId,
                appointment.getId(),
                request
        );

        assertThat(response.prescription()).isEmpty();
        assertThat(response.treatmentPlan()).isNull();
        assertThat(response.followUpRequired()).isFalse();
    }

    @Test
    void shouldRejectCreateWhenAppointmentAlreadyHasMedicalRecord() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.existsByAppointmentId(appointment.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> medicalRecordService.create(
                authenticatedUserId,
                appointment.getId(),
                request(false)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Lịch hẹn đã có bệnh án");

        verify(medicalRecordRepository, never()).saveAndFlush(any(MedicalRecord.class));
    }

    @Test
    void shouldRejectCreateWhenAppointmentIsNotInProgress() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.CHECKED_IN);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> medicalRecordService.create(
                authenticatedUserId,
                appointment.getId(),
                request(false)
        )).isInstanceOf(ConflictException.class)
                .hasMessage(
                        "Chỉ có thể nhập bệnh án cho lịch hẹn đang ở trạng thái IN_PROGRESS"
                );

        verify(medicalRecordRepository, never()).existsByAppointmentId(any());
    }

    @Test
    void shouldUpdateExistingMedicalRecordWhileAppointmentIsInProgress() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        MedicalRecord medicalRecord = medicalRecord(appointment);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.of(medicalRecord));
        when(medicalRecordRepository.saveAndFlush(medicalRecord))
                .thenReturn(medicalRecord);

        MedicalRecordResponse response = medicalRecordService.update(
                authenticatedUserId,
                appointment.getId(),
                request(true)
        );

        assertThat(response.diagnosis()).isEqualTo("Viêm tủy răng 36");
        assertThat(response.prescription()).hasSize(1);
        assertThat(medicalRecord.isFollowUpRequired()).isTrue();
        verify(medicalRecordRepository).saveAndFlush(medicalRecord);
    }

    @Test
    void shouldReportMissingMedicalRecordWhenUpdating() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.update(
                authenticatedUserId,
                appointment.getId(),
                request(false)
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bệnh án");
    }

    @Test
    void shouldRejectFollowUpWithoutDateOrNote() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        MedicalRecordRequest request = new MedicalRecordRequest(
                "Chẩn đoán",
                "Điều trị",
                null,
                null,
                List.of(),
                true,
                null,
                " "
        );

        assertThatThrownBy(() -> medicalRecordService.create(
                authenticatedUserId,
                appointment.getId(),
                request
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessage("Khi cần tái khám phải có ngày tái khám dự kiến");
    }

    @Test
    void shouldRejectFollowUpDataWhenFollowUpIsNotRequired() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);
        MedicalRecordRequest request = new MedicalRecordRequest(
                "Chẩn đoán",
                "Điều trị",
                null,
                null,
                List.of(),
                false,
                LocalDate.of(2026, 10, 20),
                null
        );

        assertThatThrownBy(() -> medicalRecordService.create(
                authenticatedUserId,
                appointment.getId(),
                request
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Không được nhập ngày hoặc ghi chú tái khám khi không cần tái khám");
    }

    @Test
    void shouldNotRevealAnotherDoctorsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        when(doctorRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointmentId,
                doctor.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.create(
                authenticatedUserId,
                appointmentId,
                request(false)
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldRejectUpdateAfterAppointmentIsCompleted() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.COMPLETED);
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> medicalRecordService.update(
                authenticatedUserId,
                appointment.getId(),
                request(false)
        )).isInstanceOf(ConflictException.class)
                .hasMessage(
                        "Chỉ có thể nhập bệnh án cho lịch hẹn đang ở trạng thái IN_PROGRESS"
                );

        verify(medicalRecordRepository, never()).findByAppointmentId(any());
    }

    @Test
    void shouldReturnOfficialMedicalRecordForCompletedAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.COMPLETED);
        MedicalRecord medicalRecord = medicalRecord(appointment);
        when(doctorRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findDetailedByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.of(medicalRecord));

        MedicalRecordResponse response = medicalRecordService.get(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(response.id()).isEqualTo(medicalRecord.getId());
        assertThat(response.appointmentId()).isEqualTo(appointment.getId());
        assertThat(response.diagnosis()).isEqualTo("Chẩn đoán cũ");
    }

    @Test
    void shouldReportMissingOfficialMedicalRecord() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.IN_PROGRESS);
        when(doctorRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findDetailedByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(medicalRecordRepository.findByAppointmentId(appointment.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.get(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bệnh án");
    }

    @Test
    void shouldNotRevealOfficialMedicalRecordBelongingToAnotherDoctor() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findDetailedByIdAndDoctorId(appointmentId, doctor.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.get(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");

        verify(medicalRecordRepository, never()).findByAppointmentId(any());
    }

    private void stubOwnedAppointment(
            UUID authenticatedUserId,
            Doctor doctor,
            Appointment appointment
    ) {
        when(doctorRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
    }

    private Doctor doctor(UUID userId) {
        User user = new User();
        user.setId(userId);
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        return doctor;
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setStatus(status);
        return appointment;
    }

    private MedicalRecord medicalRecord(Appointment appointment) {
        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setId(UUID.randomUUID());
        medicalRecord.setAppointment(appointment);
        medicalRecord.setDiagnosis("Chẩn đoán cũ");
        medicalRecord.setTreatment("Điều trị cũ");
        medicalRecord.setCreatedAt(Instant.parse("2026-10-12T01:00:00Z"));
        medicalRecord.setUpdatedAt(Instant.parse("2026-10-12T01:00:00Z"));
        return medicalRecord;
    }

    private MedicalRecord persisted(MedicalRecord medicalRecord) {
        medicalRecord.setId(UUID.randomUUID());
        medicalRecord.setCreatedAt(Instant.parse("2026-10-12T02:00:00Z"));
        medicalRecord.setUpdatedAt(Instant.parse("2026-10-12T02:00:00Z"));
        return medicalRecord;
    }

    private MedicalRecordRequest request(boolean followUpRequired) {
        return new MedicalRecordRequest(
                "  Viêm tủy răng 36  ",
                "  Điều trị tủy lần 1  ",
                "  Điều trị tiếp lần 2  ",
                "  Hạn chế nhai bên đang điều trị  ",
                List.of(new PrescriptionItem(
                        "  Paracetamol 500mg  ",
                        "  1 viên  ",
                        10,
                        "  Uống sau ăn khi đau  "
                )),
                followUpRequired,
                followUpRequired ? LocalDate.of(2026, 10, 20) : null,
                followUpRequired ? "  Tiếp tục điều trị tủy  " : null
        );
    }
}
