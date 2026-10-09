package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.CheckInAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AppointmentCheckInService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final ClinicTimeProvider clinicTimeProvider;

    public AppointmentCheckInService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional
    public CheckInAppointmentResponse checkIn(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException(
                    "Chỉ có thể check-in lịch hẹn đang ở trạng thái BOOKED"
            );
        }

        Instant checkedInAt = clinicTimeProvider.now();
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setCheckedInAt(checkedInAt);
        appointmentRepository.saveAndFlush(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.CHECK_IN);
        history.setChangedBy(doctor.getUser());
        history.setDetails(checkInDetails(appointment, checkedInAt));
        appointmentHistoryRepository.saveAndFlush(history);

        return new CheckInAppointmentResponse(
                appointment.getId(),
                appointment.getStatus(),
                toClinicOffset(appointment.getScheduledStartAt()),
                toClinicOffset(checkedInAt)
        );
    }

    private ObjectNode checkInDetails(Appointment appointment, Instant checkedInAt) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", AppointmentStatus.BOOKED.name());
        details.put("new_status", AppointmentStatus.CHECKED_IN.name());
        details.put(
                "scheduled_start_at",
                toClinicOffset(appointment.getScheduledStartAt()).toString()
        );
        details.put("checked_in_at", toClinicOffset(checkedInAt).toString());
        return details;
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }
}
