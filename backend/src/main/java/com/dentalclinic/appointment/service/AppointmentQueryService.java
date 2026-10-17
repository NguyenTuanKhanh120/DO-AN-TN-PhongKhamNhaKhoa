package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class AppointmentQueryService {

    private static final char LIKE_ESCAPE = '\\';

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration slotDuration;

    public AppointmentQueryService(
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.slot-duration-minutes:60}") long slotDurationMinutes
    ) {
        if (slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("Cấu hình thời lượng slot không hợp lệ");
        }
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.clinicTimeProvider = clinicTimeProvider;
        this.slotDuration = Duration.ofMinutes(slotDurationMinutes);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getPatientAppointments(
            UUID authenticatedUserId,
            String search,
            AppointmentStatus status,
            UUID doctorId,
            UUID serviceId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        Patient patient = patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        return queryAppointments(
                search,
                status,
                patient.getId(),
                doctorId,
                serviceId,
                fromDate,
                toDate,
                page,
                size,
                defaultSort()
        );
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getPatientAppointment(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Patient patient = patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        Appointment appointment = appointmentRepository
                .findDetailedByIdAndPatientId(appointmentId, patient.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        return toResponse(appointment, cancellationInfo(List.of(appointment)).get(appointment.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getDoctorAppointments(
            UUID authenticatedUserId,
            String search,
            AppointmentStatus status,
            UUID patientId,
            UUID serviceId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        Doctor doctor = findDoctorByUser(authenticatedUserId);
        return queryAppointments(
                search,
                status,
                patientId,
                doctor.getId(),
                serviceId,
                fromDate,
                toDate,
                page,
                size,
                defaultSort()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getDoctorTodayAppointments(
            UUID authenticatedUserId,
            String search,
            AppointmentStatus status,
            UUID patientId,
            UUID serviceId,
            int page,
            int size
    ) {
        Doctor doctor = findDoctorByUser(authenticatedUserId);
        LocalDate today = clinicTimeProvider.today();
        return queryAppointments(
                search,
                status,
                patientId,
                doctor.getId(),
                serviceId,
                today,
                today,
                page,
                size,
                Sort.by(Sort.Order.asc("scheduledStartAt"), Sort.Order.asc("id"))
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getAdminAppointments(
            String search,
            AppointmentStatus status,
            UUID patientId,
            UUID doctorId,
            UUID serviceId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        return queryAppointments(
                search,
                status,
                patientId,
                doctorId,
                serviceId,
                fromDate,
                toDate,
                page,
                size,
                defaultSort()
        );
    }

    private PageResponse<AppointmentResponse> queryAppointments(
            String search,
            AppointmentStatus status,
            UUID patientId,
            UUID doctorId,
            UUID serviceId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            Sort sort
    ) {
        validateDateRange(fromDate, toDate);
        Specification<Appointment> specification = buildSpecification(
                search,
                status,
                patientId,
                doctorId,
                serviceId,
                fromDate,
                toDate
        );
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Appointment> appointmentPage = appointmentRepository.findAll(specification, pageRequest);
        Map<UUID, CancellationInfo> cancellationInfo = cancellationInfo(appointmentPage.getContent());
        Page<AppointmentResponse> appointments = appointmentPage.map(appointment ->
                toResponse(appointment, cancellationInfo.get(appointment.getId()))
        );
        return PageResponse.from(appointments);
    }

    private Specification<Appointment> buildSpecification(
            String search,
            AppointmentStatus status,
            UUID patientId,
            UUID doctorId,
            UUID serviceId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Specification<Appointment> specification = Specification.unrestricted();
        String normalizedSearch = normalizeSearch(search);

        if (normalizedSearch != null) {
            String pattern = "%" + escapeLikePattern(normalizedSearch) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> {
                Expression<String> patientName = criteriaBuilder.lower(
                        root.get("patient").get("user").get("fullName")
                );
                Expression<String> patientPhone = criteriaBuilder.lower(
                        root.get("patient").get("phone")
                );
                Expression<String> doctorName = criteriaBuilder.lower(
                        root.get("doctorService").get("doctor").get("user").get("fullName")
                );
                Expression<String> serviceName = criteriaBuilder.lower(
                        root.get("doctorService").get("service").get("name")
                );
                Predicate patientNameMatches = criteriaBuilder.like(
                        patientName,
                        pattern,
                        LIKE_ESCAPE
                );
                Predicate patientPhoneMatches = criteriaBuilder.like(
                        patientPhone,
                        pattern,
                        LIKE_ESCAPE
                );
                Predicate doctorNameMatches = criteriaBuilder.like(
                        doctorName,
                        pattern,
                        LIKE_ESCAPE
                );
                Predicate serviceNameMatches = criteriaBuilder.like(
                        serviceName,
                        pattern,
                        LIKE_ESCAPE
                );
                return criteriaBuilder.or(
                        patientNameMatches,
                        patientPhoneMatches,
                        doctorNameMatches,
                        serviceNameMatches
                );
            });
        }
        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }
        if (patientId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("patient").get("id"), patientId));
        }
        if (doctorId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("doctorService").get("doctor").get("id"),
                            doctorId
                    ));
        }
        if (serviceId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("doctorService").get("service").get("id"),
                            serviceId
                    ));
        }
        if (fromDate != null) {
            Instant rangeStart = clinicTimeProvider.toInstant(fromDate, LocalTime.MIDNIGHT);
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(
                            root.get("scheduledStartAt"),
                            rangeStart
                    ));
        }
        if (toDate != null) {
            Instant rangeEnd = startOfNextDay(toDate);
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThan(root.get("scheduledStartAt"), rangeEnd));
        }
        return specification;
    }

    private Doctor findDoctorByUser(UUID authenticatedUserId) {
        return doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
    }

    private AppointmentResponse toResponse(
            Appointment appointment,
            CancellationInfo cancellationInfo
    ) {
        Patient patient = appointment.getPatient();
        DoctorService doctorService = appointment.getDoctorService();
        Doctor doctor = doctorService.getDoctor();
        OffsetDateTime scheduledStart = toClinicOffset(appointment.getScheduledStartAt());
        return new AppointmentResponse(
                appointment.getId(),
                patient.getId(),
                patient.getUser().getFullName(),
                patient.getPhone(),
                doctor.getId(),
                doctor.getUser().getFullName(),
                doctor.getSpecialization(),
                doctorService.getService().getId(),
                doctorService.getService().getName(),
                appointment.getPreviousAppointment() == null
                        ? null
                        : appointment.getPreviousAppointment().getId(),
                scheduledStart,
                scheduledStart.plus(slotDuration),
                appointment.getStatus(),
                toClinicOffset(appointment.getCheckedInAt()),
                toClinicOffset(appointment.getActualStartAt()),
                toClinicOffset(appointment.getActualEndAt()),
                cancellationInfo == null ? null : cancellationInfo.reason(),
                cancellationInfo == null ? null : cancellationInfo.source(),
                cancellationInfo == null ? null : cancellationInfo.cancelledAt(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }

    private Map<UUID, CancellationInfo> cancellationInfo(List<Appointment> appointments) {
        List<UUID> cancelledIds = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CANCELLED)
                .map(Appointment::getId)
                .toList();
        if (cancelledIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CancellationInfo> result = new HashMap<>();
        appointmentHistoryRepository.findByAppointmentIdsAndAction(
                cancelledIds,
                AppointmentAction.CANCEL
        ).forEach(history -> result.putIfAbsent(
                history.getAppointment().getId(),
                toCancellationInfo(history)
        ));
        return result;
    }

    private CancellationInfo toCancellationInfo(AppointmentHistory history) {
        String source = history.getDetails().path("source").asText(null);
        if (source == null && history.getChangedBy() != null) {
            source = history.getChangedBy().getRole() == com.dentalclinic.user.entity.Role.PATIENT
                    ? "PATIENT_REQUEST"
                    : "CLINIC";
        }
        return new CancellationInfo(history.getReason(), source, history.getCreatedAt());
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return instant == null
                ? null
                : clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Từ ngày không được sau đến ngày"
            );
        }
    }

    private Instant startOfNextDay(LocalDate date) {
        try {
            return clinicTimeProvider.toInstant(date.plusDays(1), LocalTime.MIDNIGHT);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đến ngày không hợp lệ");
        }
    }

    private Sort defaultSort() {
        return Sort.by(Sort.Order.desc("scheduledStartAt"), Sort.Order.asc("id"));
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private record CancellationInfo(String reason, String source, Instant cancelledAt) {
    }
}
