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
import com.dentalclinic.schedule.dto.CalendarAvailabilityStatus;
import com.dentalclinic.schedule.dto.CalendarSlotResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarDayResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarSessionResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
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
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class SlotService {

    private static final int MAX_CALENDAR_DAYS = 30;
    private static final List<ScheduleSession> CALENDAR_SESSIONS = List.of(
            ScheduleSession.MORNING,
            ScheduleSession.AFTERNOON
    );

    private final DoctorRepository doctorRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final DoctorServiceRepository doctorServiceRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration slotDuration;
    private final Duration bookingMinAdvance;

    public SlotService(
            DoctorRepository doctorRepository,
            DentalServiceRepository dentalServiceRepository,
            DoctorServiceRepository doctorServiceRepository,
            DoctorScheduleRepository doctorScheduleRepository,
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

        return generateSlotCandidates(context.schedules(), date, clinicZone).entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(earliestBookableStart))
                .filter(entry -> !occupiedStarts.contains(entry.getKey()))
                .map(entry -> toResponse(date, entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorCalendarResponse getCalendar(
            UUID doctorId,
            UUID serviceId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        CalendarRange range = resolveCalendarRange(fromDate, toDate);
        requireDoctorService(doctorId, serviceId);

        List<DoctorSchedule> schedules = doctorScheduleRepository
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctorId)
                .stream()
                .filter(DoctorScheduleRules::isSupported)
                .toList();
        ZoneId clinicZone = clinicTimeProvider.zoneId();
        Instant rangeStart = range.fromDate().atStartOfDay(clinicZone).toInstant();
        Instant rangeEnd = range.toDate().plusDays(1).atStartOfDay(clinicZone).toInstant();
        Set<Instant> occupiedStarts = new HashSet<>(appointmentRepository.findOccupiedStartTimes(
                doctorId,
                rangeStart,
                rangeEnd,
                AppointmentStatus.CANCELLED
        ));
        Instant earliestBookableStart = clinicTimeProvider.now().plus(bookingMinAdvance);

        Map<Integer, Map<ScheduleSession, DoctorSchedule>> schedulesByDay = groupSchedules(schedules);
        List<DoctorCalendarDayResponse> days = range.fromDate().datesUntil(
                        range.toDate().plusDays(1)
                )
                .map(date -> calendarDay(
                        date,
                        schedulesByDay,
                        occupiedStarts,
                        earliestBookableStart,
                        clinicZone
                ))
                .toList();
        return new DoctorCalendarResponse(
                doctorId,
                serviceId,
                range.fromDate(),
                range.toDate(),
                days
        );
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
        boolean scheduled = generateSlotCandidates(
                context.schedules(),
                appointmentDate,
                clinicZone
        ).containsKey(scheduledStartAt);
        if (!scheduled) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khung giờ không thuộc lịch làm việc của bác sĩ"
            );
        }
        return context;
    }

    private SlotContext loadSlotContext(UUID doctorId, UUID serviceId, LocalDate date) {
        DoctorService doctorService = requireDoctorService(doctorId, serviceId);

        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        List<DoctorSchedule> schedules = doctorScheduleRepository
                .findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(doctorId, dayOfWeek)
                .stream()
                .filter(DoctorScheduleRules::isSupported)
                .toList();
        return new SlotContext(doctorService, schedules);
    }

    private DoctorService requireDoctorService(UUID doctorId, UUID serviceId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        validateDoctorAvailability(doctor);

        DentalService dentalService = dentalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dịch vụ nha khoa"));
        if (!dentalService.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dịch vụ nha khoa hiện không hoạt động");
        }

        return doctorServiceRepository
                .findById(new DoctorServiceId(doctorId, serviceId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Bác sĩ không cung cấp dịch vụ nha khoa đã chọn"
                ));
    }

    private CalendarRange resolveCalendarRange(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = clinicTimeProvider.today();
        LocalDate maximumDate = today.plusDays(MAX_CALENDAR_DAYS - 1L);
        LocalDate fromDate = requestedFrom == null ? today : requestedFrom;
        LocalDate toDate = requestedTo == null ? maximumDate : requestedTo;

        if (fromDate.isBefore(today)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Từ ngày không được nằm trước ngày hiện tại"
            );
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Từ ngày không được sau đến ngày");
        }
        if (toDate.isAfter(maximumDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lịch chỉ hỗ trợ xem trong 30 ngày tính từ ngày hiện tại"
            );
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_CALENDAR_DAYS) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khoảng ngày truy vấn không được vượt quá 30 ngày"
            );
        }
        return new CalendarRange(fromDate, toDate);
    }

    private Map<Integer, Map<ScheduleSession, DoctorSchedule>> groupSchedules(
            List<DoctorSchedule> schedules
    ) {
        Map<Integer, Map<ScheduleSession, DoctorSchedule>> grouped = new HashMap<>();
        for (DoctorSchedule schedule : schedules) {
            grouped.computeIfAbsent(
                    (int) schedule.getDayOfWeek(),
                    ignored -> new EnumMap<>(ScheduleSession.class)
            ).put(schedule.getSession(), schedule);
        }
        return grouped;
    }

    private DoctorCalendarDayResponse calendarDay(
            LocalDate date,
            Map<Integer, Map<ScheduleSession, DoctorSchedule>> schedulesByDay,
            Set<Instant> occupiedStarts,
            Instant earliestBookableStart,
            ZoneId clinicZone
    ) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        Map<ScheduleSession, DoctorSchedule> schedules = date.getDayOfWeek() == DayOfWeek.SUNDAY
                ? Map.of()
                : schedulesByDay.getOrDefault(dayOfWeek, Map.of());
        List<DoctorCalendarSessionResponse> sessions = CALENDAR_SESSIONS.stream()
                .map(session -> calendarSession(
                        date,
                        session,
                        schedules.get(session),
                        occupiedStarts,
                        earliestBookableStart,
                        clinicZone
                ))
                .toList();

        return new DoctorCalendarDayResponse(
                date,
                dayOfWeek,
                resolveDayStatus(sessions),
                sessions
        );
    }

    private DoctorCalendarSessionResponse calendarSession(
            LocalDate date,
            ScheduleSession session,
            DoctorSchedule schedule,
            Set<Instant> occupiedStarts,
            Instant earliestBookableStart,
            ZoneId clinicZone
    ) {
        if (schedule == null) {
            return emptyCalendarSession(session, CalendarAvailabilityStatus.NOT_WORKING);
        }
        TreeMap<Instant, SlotCandidate> candidates = generateSlotCandidates(
                List.of(schedule),
                date,
                clinicZone
        );
        List<CalendarSlotResponse> availableSlots = candidates.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(earliestBookableStart))
                .filter(entry -> !occupiedStarts.contains(entry.getKey()))
                .map(entry -> new CalendarSlotResponse(
                        entry.getValue().startAt().toOffsetDateTime(),
                        entry.getValue().endAt().toOffsetDateTime()
                ))
                .toList();

        CalendarAvailabilityStatus status;
        if (!availableSlots.isEmpty()) {
            status = CalendarAvailabilityStatus.AVAILABLE;
        } else if (candidates.keySet().stream().allMatch(start -> start.isBefore(earliestBookableStart))) {
            status = CalendarAvailabilityStatus.UNAVAILABLE;
        } else {
            status = CalendarAvailabilityStatus.FULL;
        }
        return new DoctorCalendarSessionResponse(
                session,
                status,
                availableSlots.size(),
                availableSlots
        );
    }

    private DoctorCalendarSessionResponse emptyCalendarSession(
            ScheduleSession session,
            CalendarAvailabilityStatus status
    ) {
        return new DoctorCalendarSessionResponse(session, status, 0, List.of());
    }

    private CalendarAvailabilityStatus resolveDayStatus(
            List<DoctorCalendarSessionResponse> sessions
    ) {
        if (hasStatus(sessions, CalendarAvailabilityStatus.AVAILABLE)) {
            return CalendarAvailabilityStatus.AVAILABLE;
        }
        List<DoctorCalendarSessionResponse> workingSessions = sessions.stream()
                .filter(session -> session.status() != CalendarAvailabilityStatus.NOT_WORKING)
                .toList();
        if (workingSessions.isEmpty()) {
            return CalendarAvailabilityStatus.NOT_WORKING;
        }
        if (hasStatus(sessions, CalendarAvailabilityStatus.UNAVAILABLE)) {
            return CalendarAvailabilityStatus.UNAVAILABLE;
        }
        if (hasStatus(sessions, CalendarAvailabilityStatus.FULL)) {
            return CalendarAvailabilityStatus.FULL;
        }
        return CalendarAvailabilityStatus.NOT_WORKING;
    }

    private boolean hasStatus(
            List<DoctorCalendarSessionResponse> sessions,
            CalendarAvailabilityStatus status
    ) {
        return sessions.stream().anyMatch(session -> session.status() == status);
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
            List<DoctorSchedule> schedules
    ) {
    }

    private record SlotCandidate(
            DoctorSchedule schedule,
            ZonedDateTime startAt,
            ZonedDateTime endAt
    ) {
    }

    private record CalendarRange(LocalDate fromDate, LocalDate toDate) {
    }
}
