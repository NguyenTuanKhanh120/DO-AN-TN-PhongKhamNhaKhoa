package com.dentalclinic.schedule.service;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.schedule.dto.AdminDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffRequest;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.DayOffAffectedAppointmentResponse;
import com.dentalclinic.schedule.dto.DoctorDayOffPeriod;
import com.dentalclinic.schedule.dto.DoctorDayOffPreviewResponse;
import com.dentalclinic.schedule.dto.PublicDoctorDayOffResponse;
import com.dentalclinic.schedule.entity.DoctorDayOff;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorDayOffRepository;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DoctorDayOffService {

    public static final String CANCELLATION_SOURCE = "DOCTOR_DAY_OFF";
    public static final String PUBLIC_CANCELLATION_REASON =
            "Phòng khám hủy lịch vì bác sĩ không thể tiếp nhận. Vui lòng chọn lịch khám khác.";

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorDayOffRepository doctorDayOffRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final ClinicTimeProvider clinicTimeProvider;

    public DoctorDayOffService(
            DoctorRepository doctorRepository,
            UserRepository userRepository,
            DoctorScheduleRepository doctorScheduleRepository,
            DoctorDayOffRepository doctorDayOffRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.doctorDayOffRepository = doctorDayOffRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional(readOnly = true)
    public DoctorDayOffPreviewResponse preview(
            UUID actorUserId,
            UUID doctorId,
            CreateDoctorDayOffRequest request
    ) {
        requireActiveAdmin(actorUserId);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        ensureSessionsAreAvailable(doctorId, request.offDate(), request.session());
        DayOffImpact impact = analyzeImpact(doctor, request, false);
        return toPreviewResponse(doctor, request, impact);
    }

    @Transactional
    public CreateDoctorDayOffResponse create(
            UUID actorUserId,
            UUID doctorId,
            CreateDoctorDayOffRequest request
    ) {
        User actor = requireActiveAdmin(actorUserId);
        Doctor doctor = doctorRepository.findForUpdateById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        ensureSessionsAreAvailable(doctorId, request.offDate(), request.session());

        DayOffImpact impact = analyzeImpact(doctor, request, true);
        if (!impact.blocking().isEmpty()) {
            throw new ConflictException(
                    "Không thể đăng ký ngày nghỉ khi có lịch CHECKED_IN hoặc IN_PROGRESS. "
                            + "Vui lòng xử lý ca khám đang diễn ra trước."
            );
        }

        Map<ScheduleSession, DoctorDayOff> createdBySession = createDayOffRows(
                doctor,
                actor,
                request
        );
        Instant cancelledAt = clinicTimeProvider.now();
        List<AppointmentHistory> histories = new ArrayList<>();
        for (AffectedAppointment affected : impact.toCancel()) {
            Appointment appointment = affected.appointment();
            appointment.setStatus(AppointmentStatus.CANCELLED);
            histories.add(cancellationHistory(
                    appointment,
                    actor,
                    createdBySession.get(affected.session()),
                    cancelledAt
            ));
        }
        if (!impact.toCancel().isEmpty()) {
            appointmentRepository.saveAll(impact.toCancel().stream()
                    .map(AffectedAppointment::appointment)
                    .toList());
            appointmentRepository.flush();
            appointmentHistoryRepository.saveAll(histories);
            appointmentHistoryRepository.flush();
        }

        List<DayOffAffectedAppointmentResponse> cancelledAppointments = impact.toCancel().stream()
                .map(this::toAffectedResponse)
                .toList();
        List<DayOffAffectedAppointmentResponse> manualReviewAppointments = impact.manualReview().stream()
                .map(this::toAffectedResponse)
                .toList();
        return new CreateDoctorDayOffResponse(
                doctor.getId(),
                doctor.getUser().getFullName(),
                request.offDate(),
                request.session(),
                createdBySession.values().stream().map(this::toAdminResponse).toList(),
                cancelledAppointments.size(),
                cancelledAppointments,
                manualReviewAppointments.size(),
                manualReviewAppointments
        );
    }

    @Transactional(readOnly = true)
    public List<AdminDoctorDayOffResponse> getAdminDaysOff(
            UUID actorUserId,
            UUID doctorId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireActiveAdmin(actorUserId);
        requireValidDateRange(fromDate, toDate);
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        return doctorDayOffRepository.findByDoctorAndDateRange(doctorId, fromDate, toDate)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicDoctorDayOffResponse> getPublicDaysOff(
            UUID doctorId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireValidDateRange(fromDate, toDate);
        doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        return doctorDayOffRepository.findByDoctorAndDateRange(doctorId, fromDate, toDate)
                .stream()
                .map(this::toPublicResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID actorUserId, UUID doctorId, UUID dayOffId) {
        requireActiveAdmin(actorUserId);
        doctorRepository.findForUpdateById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        DoctorDayOff dayOff = doctorDayOffRepository.findByIdAndDoctor_Id(dayOffId, doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ngày nghỉ"));
        doctorDayOffRepository.delete(dayOff);
        doctorDayOffRepository.flush();
    }

    private User requireActiveAdmin(UUID actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản quản trị không hợp lệ"));
        if (actor.getRole() != Role.ADMIN || actor.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Chỉ tài khoản quản trị đang hoạt động mới được thực hiện thao tác này");
        }
        return actor;
    }

    private void ensureSessionsAreAvailable(
            UUID doctorId,
            LocalDate offDate,
            DoctorDayOffPeriod period
    ) {
        boolean duplicate = period.sessions().stream().anyMatch(session ->
                doctorDayOffRepository.existsByDoctor_IdAndOffDateAndSession(
                        doctorId,
                        offDate,
                        session
                )
        );
        if (duplicate) {
            throw new ConflictException("Ngày nghỉ của bác sĩ cho ngày và buổi này đã tồn tại");
        }
    }

    private DayOffImpact analyzeImpact(
            Doctor doctor,
            CreateDoctorDayOffRequest request,
            boolean lockAppointments
    ) {
        short dayOfWeek = (short) request.offDate().getDayOfWeek().getValue();
        List<DoctorSchedule> schedules = doctorScheduleRepository
                .findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(doctor.getId(), dayOfWeek)
                .stream()
                .filter(schedule -> request.session().sessions().contains(schedule.getSession()))
                .toList();

        Map<UUID, AffectedAppointment> affectedById = new LinkedHashMap<>();
        for (DoctorSchedule schedule : schedules) {
            Instant rangeStart = clinicTimeProvider.toInstant(request.offDate(), schedule.getStartTime());
            Instant rangeEnd = clinicTimeProvider.toInstant(request.offDate(), schedule.getEndTime());
            List<Appointment> appointments = lockAppointments
                    ? appointmentRepository.findForUpdateByDoctorIdAndScheduledRange(
                            doctor.getId(),
                            rangeStart,
                            rangeEnd
                    )
                    : appointmentRepository.findDetailedByDoctorIdAndScheduledRange(
                            doctor.getId(),
                            rangeStart,
                            rangeEnd
                    );
            appointments.forEach(appointment -> affectedById.putIfAbsent(
                    appointment.getId(),
                    new AffectedAppointment(appointment, schedule.getSession())
            ));
        }

        Instant now = clinicTimeProvider.now();
        List<AffectedAppointment> toCancel = new ArrayList<>();
        List<AffectedAppointment> blocking = new ArrayList<>();
        List<AffectedAppointment> manualReview = new ArrayList<>();
        for (AffectedAppointment affected : affectedById.values()) {
            Appointment appointment = affected.appointment();
            if (appointment.getStatus() == AppointmentStatus.CHECKED_IN
                    || appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
                blocking.add(affected);
            } else if (appointment.getStatus() == AppointmentStatus.BOOKED) {
                if (appointment.getScheduledStartAt().isAfter(now)) {
                    toCancel.add(affected);
                } else {
                    manualReview.add(affected);
                }
            }
        }
        return new DayOffImpact(toCancel, blocking, manualReview);
    }

    private Map<ScheduleSession, DoctorDayOff> createDayOffRows(
            Doctor doctor,
            User actor,
            CreateDoctorDayOffRequest request
    ) {
        String reason = request.reason().trim().replaceAll("\\s+", " ");
        List<DoctorDayOff> daysOff = request.session().sessions().stream()
                .map(session -> {
                    DoctorDayOff dayOff = new DoctorDayOff();
                    dayOff.setDoctor(doctor);
                    dayOff.setOffDate(request.offDate());
                    dayOff.setSession(session);
                    dayOff.setReason(reason);
                    dayOff.setCreatedBy(actor);
                    return dayOff;
                })
                .toList();
        try {
            List<DoctorDayOff> saved = doctorDayOffRepository.saveAllAndFlush(daysOff);
            Map<ScheduleSession, DoctorDayOff> result = new LinkedHashMap<>();
            saved.forEach(dayOff -> result.put(dayOff.getSession(), dayOff));
            return result;
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Ngày nghỉ của bác sĩ cho ngày và buổi này đã tồn tại");
        }
    }

    private AppointmentHistory cancellationHistory(
            Appointment appointment,
            User actor,
            DoctorDayOff dayOff,
            Instant cancelledAt
    ) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", AppointmentStatus.BOOKED.name());
        details.put("new_status", AppointmentStatus.CANCELLED.name());
        details.put("source", CANCELLATION_SOURCE);
        details.put("doctor_id", dayOff.getDoctor().getId().toString());
        details.put("day_off_id", dayOff.getId().toString());
        details.put("off_date", dayOff.getOffDate().toString());
        details.put("session", dayOff.getSession().name());
        details.put(
                "scheduled_start_at",
                clinicTimeProvider.toClinicTime(appointment.getScheduledStartAt())
                        .toOffsetDateTime()
                        .toString()
        );
        details.put(
                "cancelled_at",
                clinicTimeProvider.toClinicTime(cancelledAt).toOffsetDateTime().toString()
        );

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.CANCEL);
        history.setChangedBy(actor);
        history.setReason(PUBLIC_CANCELLATION_REASON);
        history.setDetails(details);
        return history;
    }

    private DoctorDayOffPreviewResponse toPreviewResponse(
            Doctor doctor,
            CreateDoctorDayOffRequest request,
            DayOffImpact impact
    ) {
        List<DayOffAffectedAppointmentResponse> toCancel = impact.toCancel().stream()
                .map(this::toAffectedResponse)
                .toList();
        List<DayOffAffectedAppointmentResponse> blocking = impact.blocking().stream()
                .map(this::toAffectedResponse)
                .toList();
        List<DayOffAffectedAppointmentResponse> manualReview = impact.manualReview().stream()
                .map(this::toAffectedResponse)
                .toList();
        return new DoctorDayOffPreviewResponse(
                doctor.getId(),
                doctor.getUser().getFullName(),
                request.offDate(),
                request.session(),
                toCancel.size(),
                toCancel,
                blocking.size(),
                blocking,
                manualReview.size(),
                manualReview,
                blocking.isEmpty()
        );
    }

    private DayOffAffectedAppointmentResponse toAffectedResponse(AffectedAppointment affected) {
        Appointment appointment = affected.appointment();
        OffsetDateTime scheduledStartAt = clinicTimeProvider
                .toClinicTime(appointment.getScheduledStartAt())
                .toOffsetDateTime();
        return new DayOffAffectedAppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getUser().getFullName(),
                appointment.getPatient().getPhone(),
                appointment.getDoctorService().getService().getId(),
                appointment.getDoctorService().getService().getName(),
                scheduledStartAt,
                appointment.getStatus()
        );
    }

    private AdminDoctorDayOffResponse toAdminResponse(DoctorDayOff dayOff) {
        return new AdminDoctorDayOffResponse(
                dayOff.getId(),
                dayOff.getDoctor().getId(),
                dayOff.getDoctor().getUser().getFullName(),
                dayOff.getOffDate(),
                dayOff.getSession(),
                dayOff.getReason(),
                dayOff.getCreatedBy().getId(),
                dayOff.getCreatedBy().getFullName(),
                dayOff.getCreatedAt()
        );
    }

    private PublicDoctorDayOffResponse toPublicResponse(DoctorDayOff dayOff) {
        return new PublicDoctorDayOffResponse(
                dayOff.getId(),
                dayOff.getDoctor().getId(),
                dayOff.getOffDate(),
                dayOff.getSession()
        );
    }

    private void requireValidDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Từ ngày không được sau đến ngày"
            );
        }
    }

    private record AffectedAppointment(Appointment appointment, ScheduleSession session) {
    }

    private record DayOffImpact(
            List<AffectedAppointment> toCancel,
            List<AffectedAppointment> blocking,
            List<AffectedAppointment> manualReview
    ) {
    }
}
