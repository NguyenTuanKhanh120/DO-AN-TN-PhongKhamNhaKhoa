package com.dentalclinic.schedule.service;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentQueueOccupancy;
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
import com.dentalclinic.schedule.dto.WalkInAvailabilityResponse;
import com.dentalclinic.schedule.dto.WalkInCaseAvailabilityResponse;
import com.dentalclinic.schedule.dto.WalkInSessionAvailabilityResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class SlotService {

    private static final int MAX_CALENDAR_DAYS = 30;
    private static final List<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_PROGRESS
    );
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
    private final SchedulingPolicy schedulingPolicy;

    public SlotService(
            DoctorRepository doctorRepository,
            DentalServiceRepository dentalServiceRepository,
            DoctorServiceRepository doctorServiceRepository,
            DoctorScheduleRepository doctorScheduleRepository,
            AppointmentRepository appointmentRepository,
            ClinicTimeProvider clinicTimeProvider,
            SchedulingPolicy schedulingPolicy
    ) {
        this.doctorRepository = doctorRepository;
        this.dentalServiceRepository = dentalServiceRepository;
        this.doctorServiceRepository = doctorServiceRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.clinicTimeProvider = clinicTimeProvider;
        this.schedulingPolicy = schedulingPolicy;
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
        if (!schedulingPolicy.isBeforePatientDailyCutoff(date)) {
            return List.of();
        }

        ZoneId clinicZone = clinicTimeProvider.zoneId();
        Map<QueueKey, Set<Integer>> occupiedQueues = loadOccupiedQueues(
                doctorId,
                date,
                date
        );
        return generateCaseCandidates(context.schedules(), date, clinicZone).stream()
                .filter(candidate -> !isOccupied(candidate, date, occupiedQueues))
                .map(candidate -> toResponse(date, candidate))
                .toList();
    }

    @Transactional(readOnly = true)
    public WalkInAvailabilityResponse getWalkInAvailability(
            UUID doctorId,
            UUID serviceId
    ) {
        LocalDate today = clinicTimeProvider.today();
        SlotContext context = loadSlotContext(doctorId, serviceId, today);
        Map<ScheduleSession, DoctorSchedule> schedulesBySession = new EnumMap<>(
                ScheduleSession.class
        );
        context.schedules().forEach(schedule ->
                schedulesBySession.put(schedule.getSession(), schedule)
        );
        Map<QueueKey, Set<Integer>> occupiedQueues = context.schedules().isEmpty()
                ? Map.of()
                : loadOccupiedQueues(doctorId, today, today);
        Instant now = clinicTimeProvider.now();

        List<WalkInSessionAvailabilityResponse> sessions = CALENDAR_SESSIONS.stream()
                .map(session -> walkInSession(
                        today,
                        session,
                        schedulesBySession.get(session),
                        occupiedQueues,
                        now
                ))
                .toList();
        return new WalkInAvailabilityResponse(
                doctorId,
                serviceId,
                today,
                sessions
        );
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

        return buildCalendar(doctorId, serviceId, range);
    }

    @Transactional(readOnly = true)
    public DoctorCalendarResponse getDoctorCalendar(
            UUID doctorId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        CalendarRange range = resolveCalendarRange(fromDate, toDate);
        requireActiveDoctor(doctorId);

        return buildCalendar(doctorId, null, range);
    }

    private DoctorCalendarResponse buildCalendar(
            UUID doctorId,
            UUID serviceId,
            CalendarRange range
    ) {
        List<DoctorSchedule> schedules = doctorScheduleRepository
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctorId)
                .stream()
                .filter(schedule -> DoctorScheduleRules.isSupported(schedule, schedulingPolicy))
                .toList();
        ZoneId clinicZone = clinicTimeProvider.zoneId();
        Map<QueueKey, Set<Integer>> occupiedQueues = loadOccupiedQueues(
                doctorId,
                range.fromDate(),
                range.toDate()
        );

        Map<Integer, Map<ScheduleSession, DoctorSchedule>> schedulesByDay = groupSchedules(schedules);
        List<DoctorCalendarDayResponse> days = range.fromDate().datesUntil(
                        range.toDate().plusDays(1)
                )
                .map(date -> calendarDay(
                        date,
                        schedulesByDay,
                        occupiedQueues,
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
    public DoctorService requireAvailableCase(
            UUID doctorId,
            UUID serviceId,
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber
    ) {
        return requireAvailableCase(
                doctorId,
                serviceId,
                appointmentDate,
                session,
                queueNumber,
                null,
                "Lịch khám trong ngày phải được đặt trước 05:00",
                slotConflict()
        );
    }

    @Transactional(readOnly = true)
    public DoctorService requireAvailableRescheduleCase(
            UUID doctorId,
            UUID serviceId,
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber,
            UUID excludedAppointmentId
    ) {
        return requireAvailableCase(
                doctorId,
                serviceId,
                appointmentDate,
                session,
                queueNumber,
                excludedAppointmentId,
                "Không thể đổi sang ngày đã qua thời hạn 05:00",
                rescheduleSlotConflict()
        );
    }

    @Transactional(readOnly = true)
    public DoctorService requireAvailableWalkInCase(
            UUID doctorId,
            UUID serviceId,
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber
    ) {
        if (!clinicTimeProvider.today().equals(appointmentDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lịch WALK_IN chỉ được tạo cho ngày hiện tại. "
                            + "Đối với lịch tương lai, bệnh nhân vui lòng đặt lịch online."
            );
        }

        SlotContext context = loadSlotContext(doctorId, serviceId, appointmentDate);
        schedulingPolicy.estimatedStartAt(appointmentDate, session, queueNumber);
        boolean scheduled = context.schedules().stream()
                .anyMatch(schedule -> schedule.getSession() == session);
        if (!scheduled) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Ca khám không thuộc lịch làm việc của bác sĩ"
            );
        }

        Instant sessionEnd = clinicTimeProvider.toInstant(
                appointmentDate,
                schedulingPolicy.sessionEnd(session)
        );
        if (!clinicTimeProvider.now().isBefore(sessionEnd)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Buổi khám đã kết thúc, không thể tạo lịch WALK_IN"
            );
        }

        Map<QueueKey, Set<Integer>> occupiedQueues = loadOccupiedQueues(
                doctorId,
                appointmentDate,
                appointmentDate
        );
        if (occupiedQueues
                .getOrDefault(new QueueKey(appointmentDate, session), Set.of())
                .contains(queueNumber)) {
            throw walkInSlotConflict();
        }
        return context.doctorService();
    }

    private DoctorService requireAvailableCase(
            UUID doctorId,
            UUID serviceId,
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber,
            UUID excludedAppointmentId,
            String cutoffMessage,
            ConflictException occupiedConflict
    ) {
        SlotContext context = loadSlotContext(doctorId, serviceId, appointmentDate);
        schedulingPolicy.estimatedStartAt(appointmentDate, session, queueNumber);
        boolean scheduled = context.schedules().stream()
                .anyMatch(schedule -> schedule.getSession() == session);
        if (!scheduled) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Ca khám không thuộc lịch làm việc của bác sĩ"
            );
        }
        if (!schedulingPolicy.isBeforePatientDailyCutoff(appointmentDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    cutoffMessage
            );
        }

        Map<QueueKey, Set<Integer>> occupiedQueues = loadOccupiedQueues(
                doctorId,
                appointmentDate,
                appointmentDate,
                excludedAppointmentId
        );
        if (occupiedQueues
                .getOrDefault(new QueueKey(appointmentDate, session), Set.of())
                .contains(queueNumber)) {
            throw occupiedConflict;
        }
        return context.doctorService();
    }

    private SlotContext loadSlotContext(UUID doctorId, UUID serviceId, LocalDate date) {
        DoctorService doctorService = requireDoctorService(doctorId, serviceId);

        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        List<DoctorSchedule> schedules = doctorScheduleRepository
                .findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(doctorId, dayOfWeek)
                .stream()
                .filter(schedule -> DoctorScheduleRules.isSupported(schedule, schedulingPolicy))
                .toList();
        return new SlotContext(doctorService, schedules);
    }

    private DoctorService requireDoctorService(UUID doctorId, UUID serviceId) {
        requireActiveDoctor(doctorId);

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

    private void requireActiveDoctor(UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        validateDoctorAvailability(doctor);
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
            Map<QueueKey, Set<Integer>> occupiedQueues,
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
                        occupiedQueues,
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
            Map<QueueKey, Set<Integer>> occupiedQueues,
            ZoneId clinicZone
    ) {
        if (schedule == null) {
            return emptyCalendarSession(session, CalendarAvailabilityStatus.NOT_WORKING);
        }
        List<CaseCandidate> candidates = generateCaseCandidates(
                List.of(schedule),
                date,
                clinicZone
        );
        boolean beforeCutoff = schedulingPolicy.isBeforePatientDailyCutoff(date);
        List<CalendarSlotResponse> availableSlots = beforeCutoff
                ? candidates.stream()
                .filter(candidate -> !isOccupied(candidate, date, occupiedQueues))
                .map(candidate -> new CalendarSlotResponse(
                        candidate.queueNumber(),
                        candidate.estimatedStartAt().toLocalTime(),
                        candidate.estimatedStartAt().toOffsetDateTime(),
                        date.atTime(schedule.getEndTime()).atZone(clinicZone).toOffsetDateTime()
                ))
                .toList()
                : List.of();

        CalendarAvailabilityStatus status;
        if (!availableSlots.isEmpty()) {
            status = CalendarAvailabilityStatus.AVAILABLE;
        } else if (!beforeCutoff) {
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

    private WalkInSessionAvailabilityResponse walkInSession(
            LocalDate date,
            ScheduleSession session,
            DoctorSchedule schedule,
            Map<QueueKey, Set<Integer>> occupiedQueues,
            Instant now
    ) {
        boolean working = schedule != null;
        boolean ended = !now.isBefore(clinicTimeProvider.toInstant(
                date,
                schedulingPolicy.sessionEnd(session)
        ));
        Set<Integer> occupied = occupiedQueues.getOrDefault(
                new QueueKey(date, session),
                Set.of()
        );
        List<WalkInCaseAvailabilityResponse> cases = IntStream.rangeClosed(
                        SchedulingPolicy.MIN_QUEUE_NUMBER,
                        SchedulingPolicy.MAX_QUEUE_NUMBER
                )
                .mapToObj(queueNumber -> walkInCase(
                        date,
                        session,
                        queueNumber,
                        working && !ended && !occupied.contains(queueNumber)
                ))
                .toList();
        int availableCaseCount = (int) cases.stream()
                .filter(WalkInCaseAvailabilityResponse::available)
                .count();

        CalendarAvailabilityStatus status;
        if (!working) {
            status = CalendarAvailabilityStatus.NOT_WORKING;
        } else if (ended) {
            status = CalendarAvailabilityStatus.UNAVAILABLE;
        } else if (availableCaseCount > 0) {
            status = CalendarAvailabilityStatus.AVAILABLE;
        } else {
            status = CalendarAvailabilityStatus.FULL;
        }
        return new WalkInSessionAvailabilityResponse(
                session,
                status,
                schedulingPolicy.sessionStart(session),
                schedulingPolicy.sessionEnd(session),
                availableCaseCount,
                cases
        );
    }

    private WalkInCaseAvailabilityResponse walkInCase(
            LocalDate date,
            ScheduleSession session,
            int queueNumber,
            boolean available
    ) {
        ZonedDateTime estimatedStartAt = schedulingPolicy.estimatedStartAt(
                date,
                session,
                queueNumber
        ).atZone(clinicTimeProvider.zoneId());
        return new WalkInCaseAvailabilityResponse(
                queueNumber,
                estimatedStartAt.toLocalTime(),
                estimatedStartAt.toOffsetDateTime(),
                available
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

    private List<CaseCandidate> generateCaseCandidates(
            List<DoctorSchedule> schedules,
            LocalDate date,
            ZoneId clinicZone
    ) {
        List<CaseCandidate> candidates = new ArrayList<>();
        for (DoctorSchedule schedule : schedules) {
            for (int queueNumber = SchedulingPolicy.MIN_QUEUE_NUMBER;
                 queueNumber <= SchedulingPolicy.MAX_QUEUE_NUMBER;
                 queueNumber++) {
                candidates.add(new CaseCandidate(
                        schedule,
                        queueNumber,
                        schedulingPolicy.estimatedStartAt(
                                date,
                                schedule.getSession(),
                                queueNumber
                        ).atZone(clinicZone)
                ));
            }
        }
        return candidates.stream()
                .sorted((left, right) -> left.estimatedStartAt()
                        .compareTo(right.estimatedStartAt()))
                .toList();
    }

    private AvailableSlotResponse toResponse(LocalDate date, CaseCandidate candidate) {
        return new AvailableSlotResponse(
                date,
                candidate.schedule().getSession(),
                candidate.queueNumber(),
                candidate.schedule().getStartTime(),
                candidate.schedule().getEndTime(),
                candidate.estimatedStartAt().toLocalTime(),
                candidate.estimatedStartAt().toOffsetDateTime()
        );
    }

    private Map<QueueKey, Set<Integer>> loadOccupiedQueues(
            UUID doctorId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return loadOccupiedQueues(doctorId, fromDate, toDate, null);
    }

    private Map<QueueKey, Set<Integer>> loadOccupiedQueues(
            UUID doctorId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID excludedAppointmentId
    ) {
        List<AppointmentQueueOccupancy> occupancies = appointmentRepository
                .findActiveQueueOccupancies(
                        doctorId,
                        fromDate,
                        toDate,
                        ACTIVE_APPOINTMENT_STATUSES
                );
        Map<QueueKey, Set<Integer>> grouped = new HashMap<>();
        for (AppointmentQueueOccupancy occupancy : occupancies) {
            if (excludedAppointmentId != null
                    && excludedAppointmentId.equals(occupancy.getAppointmentId())) {
                continue;
            }
            grouped.computeIfAbsent(
                    new QueueKey(occupancy.getAppointmentDate(), occupancy.getSession()),
                    ignored -> new HashSet<>()
            ).add(occupancy.getQueueNumber().intValue());
        }
        grouped.replaceAll((ignored, queues) -> Set.copyOf(queues));
        return Map.copyOf(grouped);
    }

    private boolean isOccupied(
            CaseCandidate candidate,
            LocalDate date,
            Map<QueueKey, Set<Integer>> occupiedQueues
    ) {
        return occupiedQueues
                .getOrDefault(
                        new QueueKey(date, candidate.schedule().getSession()),
                        Set.of()
                )
                .contains(candidate.queueNumber());
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

    private ConflictException walkInSlotConflict() {
        return new ConflictException("Ca khám vừa được đặt. Vui lòng chọn ca khác.");
    }

    private record SlotContext(
            DoctorService doctorService,
            List<DoctorSchedule> schedules
    ) {
    }

    private record CaseCandidate(
            DoctorSchedule schedule,
            int queueNumber,
            ZonedDateTime estimatedStartAt
    ) {
    }

    private record QueueKey(LocalDate date, ScheduleSession session) {
    }

    private record CalendarRange(LocalDate fromDate, LocalDate toDate) {
    }
}
