package com.dentalclinic.medicalrecord.service;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.dto.MedicalRecordRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.MedicalRecordDraftRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordDraftResponse;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.entity.MedicalRecordDraft;
import com.dentalclinic.medicalrecord.mapper.MedicalRecordMapper;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.medicalrecord.repository.MedicalRecordDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MedicalRecordService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordDraftRepository medicalRecordDraftRepository;
    private final MedicalRecordMapper medicalRecordMapper;

    public MedicalRecordService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            MedicalRecordRepository medicalRecordRepository,
            MedicalRecordDraftRepository medicalRecordDraftRepository,
            MedicalRecordMapper medicalRecordMapper
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.medicalRecordDraftRepository = medicalRecordDraftRepository;
        this.medicalRecordMapper = medicalRecordMapper;
    }

    @Transactional
    public MedicalRecordResponse create(
            UUID authenticatedUserId,
            UUID appointmentId,
            MedicalRecordRequest request
    ) {
        Appointment appointment = findOwnedAppointmentForUpdate(
                authenticatedUserId,
                appointmentId
        );
        requireInProgress(appointment);
        validateFollowUp(request);
        if (medicalRecordRepository.existsByAppointmentId(appointmentId)) {
            throw new ConflictException("Lịch hẹn đã có bệnh án");
        }

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setAppointment(appointment);
        applyRequest(medicalRecord, request);
        MedicalRecord saved = medicalRecordRepository.saveAndFlush(medicalRecord);
        medicalRecordDraftRepository.deleteByAppointmentId(appointmentId);
        return medicalRecordMapper.toResponse(saved);
    }

    @Transactional
    public MedicalRecordResponse update(
            UUID authenticatedUserId,
            UUID appointmentId,
            MedicalRecordRequest request
    ) {
        Appointment appointment = findOwnedAppointmentForUpdate(
                authenticatedUserId,
                appointmentId
        );
        requireInProgress(appointment);
        validateFollowUp(request);

        MedicalRecord medicalRecord = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bệnh án"));
        applyRequest(medicalRecord, request);
        MedicalRecord saved = medicalRecordRepository.saveAndFlush(medicalRecord);
        medicalRecordDraftRepository.deleteByAppointmentId(appointmentId);
        return medicalRecordMapper.toResponse(saved);
    }

    @Transactional
    public MedicalRecordDraftResponse saveDraft(
            UUID authenticatedUserId,
            UUID appointmentId,
            MedicalRecordDraftRequest request
    ) {
        Appointment appointment = findOwnedAppointmentForUpdate(authenticatedUserId, appointmentId);
        requireInProgress(appointment);
        MedicalRecordDraft draft = medicalRecordDraftRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> {
                    MedicalRecordDraft created = new MedicalRecordDraft();
                    created.setAppointment(appointment);
                    return created;
                });
        draft.setDraftData(medicalRecordMapper.toDraftJson(request));
        return medicalRecordMapper.toDraftResponse(
                medicalRecordDraftRepository.saveAndFlush(draft)
        );
    }

    @Transactional(readOnly = true)
    public MedicalRecordDraftResponse getDraft(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        Appointment appointment = appointmentRepository
                .findDetailedByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        requireInProgress(appointment);
        return medicalRecordMapper.toDraftResponse(
                medicalRecordDraftRepository.findByAppointmentId(appointmentId)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy bản nháp bệnh án"))
        );
    }

    private Appointment findOwnedAppointmentForUpdate(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        return appointmentRepository.findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
    }

    private void requireInProgress(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Chỉ có thể nhập bệnh án cho lịch hẹn đang ở trạng thái IN_PROGRESS"
            );
        }
    }

    private void validateFollowUp(MedicalRecordRequest request) {
        if (request.followUpRequired() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Phải chọn có cần tái khám hay không"
            );
        }
        boolean followUpRequired = Boolean.TRUE.equals(request.followUpRequired());
        boolean hasFollowUpDate = request.recommendedFollowUpDate() != null;
        boolean hasFollowUpNote = hasText(request.followUpNote());
        if (followUpRequired && !hasFollowUpDate) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khi cần tái khám phải có ngày tái khám dự kiến"
            );
        }
        if (!followUpRequired && (hasFollowUpDate || hasFollowUpNote)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Không được nhập ngày hoặc ghi chú tái khám khi không cần tái khám"
            );
        }
    }

    private void applyRequest(MedicalRecord medicalRecord, MedicalRecordRequest request) {
        medicalRecord.setDiagnosis(request.diagnosis().trim());
        medicalRecord.setTreatment(request.treatment().trim());
        medicalRecord.setTreatmentPlan(normalizeOptional(request.treatmentPlan()));
        medicalRecord.setNotes(normalizeOptional(request.notes()));
        medicalRecord.setPrescription(medicalRecordMapper.toPrescriptionJson(
                request.prescription()
        ));
        medicalRecord.setFollowUpRequired(Boolean.TRUE.equals(request.followUpRequired()));
        medicalRecord.setRecommendedFollowUpDate(request.recommendedFollowUpDate());
        medicalRecord.setFollowUpNote(normalizeOptional(request.followUpNote()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
