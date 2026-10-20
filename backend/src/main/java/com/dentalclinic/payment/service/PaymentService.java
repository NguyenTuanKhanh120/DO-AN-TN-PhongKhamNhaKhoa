package com.dentalclinic.payment.service;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.payment.dto.PaymentItemRequest;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.dto.UpdatePaymentItemsRequest;
import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentItemSnapshot;
import com.dentalclinic.payment.entity.PaymentMethod;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.mapper.PaymentMapper;
import com.dentalclinic.payment.repository.PaymentRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final char LIKE_ESCAPE = '\\';
    private static final int MAX_QUANTITY = 100;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "totalAmount",
            "status",
            "paidAt"
    );

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorServiceRepository doctorServiceRepository;
    private final PatientRepository patientRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentMapper paymentMapper;
    private final ClinicTimeProvider clinicTimeProvider;

    public PaymentService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            DoctorServiceRepository doctorServiceRepository,
            PatientRepository patientRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PaymentMapper paymentMapper,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorServiceRepository = doctorServiceRepository;
        this.patientRepository = patientRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentMapper = paymentMapper;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getDoctorPayment(UUID authenticatedUserId, UUID appointmentId) {
        Doctor doctor = findDoctor(authenticatedUserId);
        Payment payment = paymentRepository.findDetailedByAppointmentIdAndDoctorId(
                        appointmentId,
                        doctor.getId()
                )
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu thanh toán"));
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse updateDoctorItems(
            UUID authenticatedUserId,
            UUID appointmentId,
            UpdatePaymentItemsRequest request
    ) {
        Doctor doctor = findDoctor(authenticatedUserId);
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Chỉ có thể ghi nhận dịch vụ khi lịch hẹn đang ở trạng thái IN_PROGRESS"
            );
        }

        Payment payment = paymentRepository.findForUpdateByAppointmentId(appointmentId)
                .orElseGet(() -> newDraftPayment(appointment));
        if (payment.getStatus() != PaymentStatus.DRAFT) {
            throw new ConflictException(
                    "Không thể chỉnh sửa dịch vụ sau khi phiếu thanh toán đã được chốt"
            );
        }

        List<PaymentItemRequest> requestedItems = request == null ? null : request.items();
        if (requestedItems == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Danh sách dịch vụ không được để trống"
            );
        }
        validateNoDuplicateServices(requestedItems);

        Map<UUID, PaymentItemSnapshot> existingItems = new HashMap<>();
        if (payment.getItems() != null) {
            payment.getItems().forEach(item -> existingItems.put(item.serviceId(), item));
        }
        Map<UUID, DoctorService> assignedServices = new HashMap<>();
        doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId())
                .forEach(mapping -> assignedServices.put(mapping.getService().getId(), mapping));

        UUID originalServiceId = appointment.getDoctorService().getService().getId();
        List<PaymentItemSnapshot> snapshots = requestedItems.stream()
                .map(item -> toSnapshot(
                        item,
                        existingItems.get(item.serviceId()),
                        assignedServices.get(item.serviceId()),
                        originalServiceId
                ))
                .toList();
        BigDecimal totalAmount = calculateTotal(snapshots, true);

        payment.setItems(snapshots);
        payment.setTotalAmount(totalAmount);
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Không thể lưu phiếu thanh toán do dữ liệu bị xung đột");
        }
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public Payment finalizeDraftForCompletion(Appointment appointment) {
        Payment payment = paymentRepository.findForUpdateByAppointmentId(appointment.getId())
                .orElseThrow(() -> new ConflictException(
                        "Phải ghi nhận ít nhất một dịch vụ trước khi hoàn tất khám"
                ));
        if (payment.getStatus() != PaymentStatus.DRAFT) {
            throw new ConflictException("Phiếu thanh toán không ở trạng thái DRAFT");
        }
        BigDecimal recalculatedTotal = calculateTotal(payment.getItems(), false);
        if (payment.getTotalAmount() == null
                || payment.getTotalAmount().compareTo(recalculatedTotal) != 0) {
            throw new ConflictException("Tổng tiền của phiếu thanh toán không hợp lệ");
        }

        payment.setTotalAmount(recalculatedTotal);
        payment.setStatus(PaymentStatus.UNPAID);
        payment.setPaymentMethod(null);
        payment.setConfirmedBy(null);
        payment.setPaidAt(null);
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getAdminPayments(
            String search,
            PaymentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {
        validateDateRange(fromDate, toDate);
        String resolvedSortBy = normalizeSortField(sortBy);
        Sort.Direction resolvedDirection = direction == null ? Sort.Direction.DESC : direction;
        Sort sort = Sort.by(
                new Sort.Order(resolvedDirection, resolvedSortBy),
                Sort.Order.asc("id")
        );
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<PaymentResponse> payments = paymentRepository.findAll(
                        buildSpecification(search, status, fromDate, toDate, false),
                        pageable
                )
                .map(paymentMapper::toResponse);
        return PageResponse.from(payments);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getAdminPayment(UUID paymentId) {
        return paymentMapper.toResponse(paymentRepository.findDetailedById(paymentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu thanh toán")));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getStaffPayments(
            String search,
            PaymentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {
        if (status == PaymentStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Nhân viên không được xem phiếu thanh toán DRAFT"
            );
        }
        validateDateRange(fromDate, toDate);
        String resolvedSortBy = normalizeSortField(sortBy);
        Sort.Direction resolvedDirection = direction == null ? Sort.Direction.DESC : direction;
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        new Sort.Order(resolvedDirection, resolvedSortBy),
                        Sort.Order.asc("id")
                )
        );
        return PageResponse.from(paymentRepository.findAll(
                        buildSpecification(search, status, fromDate, toDate, true),
                        pageable
                )
                .map(paymentMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getStaffPayment(UUID paymentId) {
        Payment payment = paymentRepository.findDetailedById(paymentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu thanh toán"));
        if (payment.getStatus() == PaymentStatus.DRAFT) {
            throw new NotFoundException("Không tìm thấy phiếu thanh toán");
        }
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse confirmPayment(
            UUID authenticatedUserId,
            UUID paymentId,
            PaymentMethod paymentMethod
    ) {
        User collector = requireActivePaymentCollector(authenticatedUserId);
        if (paymentMethod == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Phương thức thanh toán không được để trống"
            );
        }
        Payment payment = paymentRepository.findForUpdateDetailedById(paymentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu thanh toán"));
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new ConflictException("Phiếu thanh toán đã được xác nhận trước đó");
        }
        if (payment.getStatus() != PaymentStatus.UNPAID) {
            throw new ConflictException("Chỉ có thể xác nhận phiếu thanh toán đang ở trạng thái UNPAID");
        }
        if (payment.getAppointment().getStatus() != AppointmentStatus.COMPLETED) {
            throw new ConflictException("Chỉ có thể thu tiền cho lịch hẹn đã hoàn thành");
        }

        BigDecimal recalculatedTotal = calculateTotal(payment.getItems(), false);
        if (payment.getTotalAmount() == null
                || payment.getTotalAmount().compareTo(recalculatedTotal) != 0) {
            throw new ConflictException("Tổng tiền của phiếu thanh toán không hợp lệ");
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentMethod(paymentMethod);
        payment.setConfirmedBy(collector);
        payment.setPaidAt(clinicTimeProvider.now());
        return paymentMapper.toResponse(paymentRepository.saveAndFlush(payment));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getPatientPayments(
            UUID authenticatedUserId,
            int page,
            int size
    ) {
        Patient patient = findPatient(authenticatedUserId);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))
        );
        return PageResponse.from(paymentRepository.findPatientPayments(
                        patient.getId(),
                        PaymentStatus.DRAFT,
                        pageable
                )
                .map(paymentMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPatientPayment(UUID authenticatedUserId, UUID paymentId) {
        Patient patient = findPatient(authenticatedUserId);
        Payment payment = paymentRepository.findPatientPaymentDetail(
                        paymentId,
                        patient.getId(),
                        PaymentStatus.DRAFT
                )
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu thanh toán"));
        return paymentMapper.toResponse(payment);
    }

    private Payment newDraftPayment(Appointment appointment) {
        Payment payment = new Payment();
        payment.setAppointment(appointment);
        payment.setItems(List.of());
        payment.setTotalAmount(ZERO_AMOUNT);
        payment.setStatus(PaymentStatus.DRAFT);
        return payment;
    }

    private PaymentItemSnapshot toSnapshot(
            PaymentItemRequest request,
            PaymentItemSnapshot existing,
            DoctorService assigned,
            UUID originalServiceId
    ) {
        if (request == null || request.serviceId() == null || request.quantity() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dịch vụ và số lượng là bắt buộc");
        }
        if (request.quantity() < 1 || request.quantity() > MAX_QUANTITY) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Số lượng dịch vụ phải từ 1 đến " + MAX_QUANTITY
            );
        }

        BigDecimal unitPrice;
        String serviceName;
        boolean additional;
        if (existing != null) {
            unitPrice = normalizeMoney(existing.unitPrice());
            serviceName = existing.serviceName();
            additional = existing.additional();
            if (!hasText(serviceName)) {
                throw new ConflictException("Dữ liệu snapshot dịch vụ không hợp lệ");
            }
        } else {
            if (assigned == null) {
                throw new ConflictException(
                        "Dịch vụ không tồn tại hoặc chưa được phân công cho bác sĩ"
                );
            }
            if (!assigned.getService().isActive()) {
                throw new ConflictException("Dịch vụ đã ngừng hoạt động");
            }
            unitPrice = normalizeMoney(assigned.getService().getPrice());
            serviceName = assigned.getService().getName();
            additional = !request.serviceId().equals(originalServiceId);
        }

        BigDecimal subtotal = checkedAmount(unitPrice.multiply(
                BigDecimal.valueOf(request.quantity())
        ));
        return new PaymentItemSnapshot(
                request.serviceId(),
                serviceName,
                unitPrice,
                request.quantity(),
                subtotal,
                additional
        );
    }

    private BigDecimal calculateTotal(List<PaymentItemSnapshot> items, boolean allowEmpty) {
        if (items == null || (!allowEmpty && items.isEmpty())) {
            throw new ConflictException("Phiếu thanh toán phải có ít nhất một dịch vụ thực tế");
        }
        if (items.isEmpty()) {
            return ZERO_AMOUNT;
        }

        Set<UUID> serviceIds = new HashSet<>();
        BigDecimal total = ZERO_AMOUNT;
        for (PaymentItemSnapshot item : items) {
            if (item == null || item.serviceId() == null || !hasText(item.serviceName())) {
                throw new ConflictException("Dữ liệu dịch vụ trong phiếu thanh toán không hợp lệ");
            }
            if (!serviceIds.add(item.serviceId())) {
                throw new ConflictException("Một dịch vụ không được xuất hiện nhiều lần trong phiếu");
            }
            if (item.quantity() < 1 || item.quantity() > MAX_QUANTITY) {
                throw new ConflictException("Số lượng dịch vụ trong phiếu thanh toán không hợp lệ");
            }
            BigDecimal unitPrice = normalizeMoney(item.unitPrice());
            BigDecimal expectedSubtotal = checkedAmount(unitPrice.multiply(
                    BigDecimal.valueOf(item.quantity())
            ));
            if (item.subtotal() == null
                    || normalizeMoney(item.subtotal()).compareTo(expectedSubtotal) != 0) {
                throw new ConflictException("Thành tiền của dịch vụ không hợp lệ");
            }
            total = checkedAmount(total.add(expectedSubtotal));
        }
        return total;
    }

    private void validateNoDuplicateServices(List<PaymentItemRequest> items) {
        Set<UUID> serviceIds = new HashSet<>();
        for (PaymentItemRequest item : items) {
            if (item != null && item.serviceId() != null && !serviceIds.add(item.serviceId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Một dịch vụ không được xuất hiện nhiều lần trong phiếu"
                );
            }
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new ConflictException("Đơn giá dịch vụ không hợp lệ");
        }
        try {
            return checkedAmount(value.setScale(2, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException exception) {
            throw new ConflictException("Đơn giá dịch vụ không hợp lệ");
        }
    }

    private BigDecimal checkedAmount(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw new ConflictException("Số tiền vượt quá giới hạn cho phép");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ConflictException("Số tiền không đúng định dạng tiền tệ");
        }
    }

    private Specification<Payment> buildSpecification(
            String search,
            PaymentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            boolean excludeDraft
    ) {
        Specification<Payment> specification = Specification.unrestricted();
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch != null) {
            String pattern = "%" + escapeLikePattern(normalizedSearch) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> {
                Expression<String> patientName = criteriaBuilder.lower(
                        root.get("appointment").get("patient").get("user").get("fullName")
                );
                Expression<String> doctorName = criteriaBuilder.lower(
                        root.get("appointment").get("doctorService")
                                .get("doctor").get("user").get("fullName")
                );
                Expression<String> patientPhone = criteriaBuilder.lower(
                        root.get("appointment").get("patient").get("phone")
                );
                Predicate patientMatches = criteriaBuilder.like(
                        patientName,
                        pattern,
                        LIKE_ESCAPE
                );
                Predicate doctorMatches = criteriaBuilder.like(
                        doctorName,
                        pattern,
                        LIKE_ESCAPE
                );
                Predicate phoneMatches = criteriaBuilder.like(
                        patientPhone,
                        pattern,
                        LIKE_ESCAPE
                );
                return criteriaBuilder.or(patientMatches, doctorMatches, phoneMatches);
            });
        }
        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        } else if (excludeDraft) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.notEqual(root.get("status"), PaymentStatus.DRAFT));
        }
        if (fromDate != null) {
            Instant rangeStart = clinicTimeProvider.toInstant(fromDate, LocalTime.MIDNIGHT);
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), rangeStart));
        }
        if (toDate != null) {
            Instant rangeEnd = clinicTimeProvider.toInstant(
                    safePlusOneDay(toDate),
                    LocalTime.MIDNIGHT
            );
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThan(root.get("createdAt"), rangeEnd));
        }
        return specification;
    }

    private Doctor findDoctor(UUID userId) {
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
    }

    private Patient findPatient(UUID userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
    }

    private User requireActivePaymentCollector(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản thu tiền không hợp lệ"));
        boolean allowedRole = user.getRole() == Role.ADMIN || user.getRole() == Role.STAFF;
        if (!allowedRole || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException(
                    "Chỉ Admin hoặc nhân viên đang hoạt động mới được xác nhận thanh toán"
            );
        }
        return user;
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Từ ngày không được sau đến ngày"
            );
        }
    }

    private LocalDate safePlusOneDay(LocalDate date) {
        try {
            return date.plusDays(1);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Khoảng ngày không hợp lệ");
        }
    }

    private String normalizeSortField(String sortBy) {
        String resolved = hasText(sortBy) ? sortBy.trim() : "createdAt";
        if (!ALLOWED_SORT_FIELDS.contains(resolved)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Trường sắp xếp không hợp lệ");
        }
        return resolved;
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
