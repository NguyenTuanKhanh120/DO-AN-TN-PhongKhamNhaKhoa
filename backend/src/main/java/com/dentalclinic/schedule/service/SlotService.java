package com.dentalclinic.schedule.service;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorDayOffRepository;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class SlotService {

    private final DoctorRepository doctorRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final DoctorServiceRepository doctorServiceRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final DoctorDayOffRepository doctorDayOffRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration slotDuration;
    private final Duration bookingMinAdvance;

    public SlotService(
            DoctorRepository doctorRepository,
            DentalServiceRepository dentalServiceRepository,
            DoctorServiceRepository doctorServiceRepository,
            DoctorScheduleRepository doctorScheduleRepository,
            DoctorDayOffRepository doctorDayOffRepository,
            AppointmentRepository appointmentRepository,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.slot-duration-minutes:60}") long slotDurationMinutes,
            @Value("${clinic.appointment.booking-min-advance-minutes:120}") long bookingMinAdvanceMinutes
    ) {
        if (slotDurationMinutes <= 0 || bookingMinAdvanceMinutes < 0) {
            throw new IllegalArgumentException("Cấu hình thời lượng slot hoặc thời gian đặt trước không hợp lệ");
        }
        this.doctorRepository = doctorRepository;
        this.dentalServiceRepository = dentalServiceRepository;
        this.doctorServiceRepository = doctorServiceRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.doctorDayOffRepository = doctorDayOffRepository;
        this.appointmentRepository = appointmentRepository;
        this.clinicTimeProvider = clinicTimeProvider;
        this.slotDuration = Duration.ofMinutes(slotDurationMinutes);
        this.bookingMinAdvance = Duration.ofMinutes(bookingMinAdvanceMinutes);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getAvailableSlots(
            UUID doctorId,
            UUID serviceId,
            LocalDate date
    ) {
        SlotContext context = loadSlotContext(doctorId, serviceId, date);
        if (context.schedules().isEmpty()) {
            return List.of();
        }

        ZoneId clinicZone = clinicTimeProvider.zoneId();
        Instant dayStart = date.atStartOfDay(clinicZone).toInstant();
        Instant nextDayStart = date.plusDays(1).atStartOfDay(clinicZone).toInstant();
        Set<Instant> occupiedStarts = new HashSet<>(appointmentRepository.findOccupiedStartTimes(
                doctorId,
                dayStart,
                nextDayStart,
                AppointmentStatus.CANCELLED
        ));
        Instant earliestBookableStart = clinicTimeProvider.now().plus(bookingMinAdvance);

        List<DoctorSchedule> availableSchedules = context.schedules().stream()
                .filter(schedule -> !context.daysOff().contains(schedule.getSession()))
                .toList();
        return generateSlotCandidates(availableSchedules, date, clinicZone).entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(earliestBookableStart))
                .filter(entry -> !occupiedStarts.contains(entry.getKey()))
                .map(entry -> toResponse(date, entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorService requireAvailableSlot(
            UUID doctorId,
            UUID serviceId,
            Instant scheduledStartAt
    ) {
        SlotContext context = requireScheduledSlot(doctorId, serviceId, scheduledStartAt);
        if (scheduledStartAt.isBefore(clinicTimeProvider.now().plus(bookingMinAdvance))) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Bạn cần đặt lịch trước giờ khám ít nhất 2 giờ"
            );
        }
        if (appointmentRepository.existsOccupiedStart(
                doctorId,
                scheduledStartAt,
                AppointmentStatus.CANCELLED
        )) {
            throw slotConflict();
        }
        return context.doctorService();
    }

    @Transactional(readOnly = true)
    public DoctorService requireAvailableRescheduleSlot(
            UUID doctorId,
            UUID serviceId,
            Instant scheduledStartAt
    ) {
        SlotContext context = requireScheduledSlot(doctorId, serviceId, scheduledStartAt);
        if (!scheduledStartAt.isAfter(clinicTimeProvider.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khung giờ mới phải nằm trong tương lai"
            );
        }
        if (appointmentRepository.existsOccupiedStart(
                doctorId,
                scheduledStartAt,
                AppointmentStatus.CANCELLED
        )) {
            throw rescheduleSlotConflict();
        }
        return context.doctorService();
    }

    private SlotContext requireScheduledSlot(
            UUID doctorId,
            UUID serviceId,
            Instant scheduledStartAt
    ) {
        ZoneId clinicZone = clinicTimeProvider.zoneId();
        LocalDate appointmentDate = scheduledStartAt.atZone(clinicZone).toLocalDate();
        SlotContext context = loadSlotContext(doctorId, serviceId, appointmentDate);
        SlotCandidate candidate = generateSlotCandidates(
                context.schedules(),
                appointmentDate,
                clinicZone
        ).get(scheduledStartAt);
        if (candidate == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khung giờ không thuộc lịch làm việc của bác sĩ"
            );
        }
        if (context.daysOff().contains(candidate.schedule().getSession())) {
            throw new ConflictException(
                    "Bác sĩ đã nghỉ trong khung giờ này. Vui lòng chọn khung giờ khác"
            );
        }
        return context;
    }

    private SlotContext loadSlotContext(UUID doctorId, UUID serviceId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        validateDoctorAvailability(doctor);

        DentalService dentalService = dentalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dịch vụ nha khoa"));
        if (!dentalService.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dịch vụ nha khoa hiện không hoạt động");
        }

        DoctorService doctorService = doctorServiceRepository
                .findById(new DoctorServiceId(doctorId, serviceId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Bác sĩ không cung cấp dịch vụ nha khoa đã chọn"
                ));

        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        List<DoctorSchedule> schedules = doctorScheduleRepository
                .findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(doctorId, dayOfWeek);
        Set<ScheduleSession> daysOff = new HashSet<>(
                doctorDayOffRepository.findSessionsByDoctorIdAndOffDate(doctorId, date)
        );
        return new SlotContext(doctorService, schedules, daysOff);
    }

    private TreeMap<Instant, SlotCandidate> generateSlotCandidates(
            List<DoctorSchedule> schedules,
            LocalDate date,
            ZoneId clinicZone
    ) {
        TreeMap<Instant, SlotCandidate> candidates = new TreeMap<>();
        for (DoctorSchedule schedule : schedules) {
            LocalDateTime cursor = date.atTime(schedule.getStartTime());
            LocalDateTime scheduleEnd = date.atTime(schedule.getEndTime());
            while (!cursor.plus(slotDuration).isAfter(scheduleEnd)) {
                ZonedDateTime startAt = cursor.atZone(clinicZone);
                ZonedDateTime endAt = startAt.plus(slotDuration);
                candidates.putIfAbsent(
                        startAt.toInstant(),
                        new SlotCandidate(schedule, startAt, endAt)
                );
                cursor = cursor.plus(slotDuration);
            }
        }
        return candidates;
    }

    private AvailableSlotResponse toResponse(LocalDate date, SlotCandidate candidate) {
        return new AvailableSlotResponse(
                date,
                candidate.schedule().getSession(),
                candidate.startAt().toLocalTime(),
                candidate.endAt().toLocalTime(),
                candidate.startAt().toOffsetDateTime(),
                candidate.endAt().toOffsetDateTime()
        );
    }

    private void validateDoctorAvailability(Doctor doctor) {
        if (doctor.getUser().getStatus() != UserStatus.ACTIVE
                || doctor.getWorkingStatus() != DoctorStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Bác sĩ hiện không hoạt động");
        }
    }

    private ConflictException slotConflict() {
        return new ConflictException(
                "Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác"
        );
    }

    private ConflictException rescheduleSlotConflict() {
        return new ConflictException(
                "Khung giờ mới vừa được người khác đặt. Lịch hiện tại của bạn vẫn được giữ nguyên. "
                        + "Vui lòng chọn khung giờ khác."
        );
    }

    private record SlotContext(
            DoctorService doctorService,
            List<DoctorSchedule> schedules,
            Set<ScheduleSession> daysOff
    ) {
    }

    private record SlotCandidate(
            DoctorSchedule schedule,
            ZonedDateTime startAt,
            ZonedDateTime endAt
    ) {
    }
}
