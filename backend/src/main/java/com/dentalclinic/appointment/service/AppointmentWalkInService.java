package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.CreateWalkInAppointmentRequest;
import com.dentalclinic.appointment.dto.CreateWalkInAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.entity.BookingSource;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.common.util.PostgreSqlErrorUtils;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentWalkInService {

    private static final List<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_PROGRESS
    );

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final SlotService slotService;
    private final SchedulingPolicy schedulingPolicy;
    private final ClinicTimeProvider clinicTimeProvider;

    public AppointmentWalkInService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            SlotService slotService,
            SchedulingPolicy schedulingPolicy,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.slotService = slotService;
        this.schedulingPolicy = schedulingPolicy;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional
    public CreateWalkInAppointmentResponse create(
            UUID authenticatedStaffId,
            CreateWalkInAppointmentRequest request
    ) {
        User staff = requireActiveStaff(authenticatedStaffId);
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        LocalDate appointmentDate = clinicTimeProvider.today();
        SchedulingPolicy.ScheduledCase scheduledCase = schedulingPolicy.resolveCase(
                appointmentDate,
                request.session(),
                request.queueNumber()
        );

        if (appointmentRepository.existsActivePatientSession(
                patient.getId(),
                appointmentDate,
                scheduledCase.session(),
                ACTIVE_APPOINTMENT_STATUSES
        )) {
            throw new ConflictException(
                    "Bệnh nhân đã có một lịch khám đang hoạt động trong buổi này"
            );
        }

        doctorRepository.findForUpdateById(request.doctorId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        DoctorService doctorService = slotService.requireAvailableWalkInCase(
                request.doctorId(),
                request.serviceId(),
                appointmentDate,
                scheduledCase.session(),
                scheduledCase.queueNumber()
        );

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(scheduledCase.estimatedStartAt());
        appointment.setAppointmentDate(appointmentDate);
        appointment.setSession(scheduledCase.session());
        appointment.setQueueNumber((short) scheduledCase.queueNumber());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setBookingSource(BookingSource.WALK_IN);

        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException exception) {
            if (PostgreSqlErrorUtils.isUniqueViolation(exception)) {
                throw new ConflictException("Ca khám vừa được đặt. Vui lòng chọn ca khác.");
            }
            throw exception;
        }

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.BOOK);
        history.setChangedBy(staff);
        history.setDetails(bookDetails(appointment));
        appointmentHistoryRepository.saveAndFlush(history);

        return toResponse(appointment);
    }

    private User requireActiveStaff(UUID authenticatedStaffId) {
        User user = userRepository.findById(authenticatedStaffId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản nhân viên không hợp lệ"));
        if (user.getRole() != Role.STAFF || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException(
                    "Chỉ nhân viên đang hoạt động mới được tạo lịch WALK_IN"
            );
        }
        return user;
    }

    private ObjectNode bookDetails(Appointment appointment) {
        OffsetDateTime scheduledAt = clinicTimeProvider
                .toClinicTime(appointment.getScheduledStartAt())
                .toOffsetDateTime();
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("status", AppointmentStatus.BOOKED.name());
        details.put("booking_source", BookingSource.WALK_IN.name());
        details.put("appointment_date", appointment.getAppointmentDate().toString());
        details.put("session", appointment.getSession().name());
        details.put("queue_number", appointment.getQueueNumber());
        details.put("doctor_id", appointment.getDoctorService().getDoctor().getId().toString());
        details.put("service_id", appointment.getDoctorService().getService().getId().toString());
        details.put("estimated_start_at", scheduledAt.toString());
        details.put("created_by_role", Role.STAFF.name());
        return details;
    }

    private CreateWalkInAppointmentResponse toResponse(Appointment appointment) {
        OffsetDateTime estimatedStartAt = clinicTimeProvider
                .toClinicTime(appointment.getScheduledStartAt())
                .toOffsetDateTime();
        return new CreateWalkInAppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                appointment.getAppointmentDate(),
                appointment.getSession(),
                appointment.getQueueNumber(),
                schedulingPolicy.sessionStart(appointment.getSession()),
                schedulingPolicy.sessionEnd(appointment.getSession()),
                schedulingPolicy.estimatedStartTime(
                        appointment.getSession(),
                        appointment.getQueueNumber()
                ),
                estimatedStartAt,
                appointment.getStatus(),
                appointment.getBookingSource(),
                appointment.getCreatedAt()
        );
    }
}
