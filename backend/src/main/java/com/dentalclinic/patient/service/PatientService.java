package com.dentalclinic.patient.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.patient.dto.CreateStaffPatientRequest;
import com.dentalclinic.patient.dto.PatientProfileResponse;
import com.dentalclinic.patient.dto.StaffPatientResponse;
import com.dentalclinic.patient.dto.UpdatePatientProfileRequest;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.mapper.PatientMapper;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.user.entity.User;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class PatientService {

    private static final char LIKE_ESCAPE = '\\';

    private final PatientRepository patientRepository;
    private final ClinicTimeProvider timeProvider;
    private final PatientMapper patientMapper;

    public PatientService(
            PatientRepository patientRepository,
            ClinicTimeProvider timeProvider,
            PatientMapper patientMapper
    ) {
        this.patientRepository = patientRepository;
        this.timeProvider = timeProvider;
        this.patientMapper = patientMapper;
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile(UUID authenticatedUserId) {
        return toResponse(findByUserId(authenticatedUserId));
    }

    @Transactional
    public PatientProfileResponse updateMyProfile(
            UUID authenticatedUserId,
            UpdatePatientProfileRequest request
    ) {
        validateDateOfBirth(request.dateOfBirth());

        Patient patient = findByUserId(authenticatedUserId);
        User user = requireLinkedUser(patient);
        String normalizedFullName = normalizeFullName(request.fullName());
        user.setFullName(normalizedFullName);
        patient.setFullName(normalizedFullName);

        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setPhone(normalizeNullable(request.phone()));
        patient.setAddress(normalizeNullable(request.address()));
        patient.setMedicalHistory(normalizeNullable(request.medicalHistory()));
        patient.setAllergies(normalizeNullable(request.allergies()));

        patientRepository.saveAndFlush(patient);
        return toResponse(patient);
    }

    @Transactional
    public void syncLinkedPatientFullName(UUID userId, String fullName) {
        Patient patient = findByUserId(userId);
        patient.setFullName(normalizeFullName(fullName));
        patientRepository.saveAndFlush(patient);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffPatientResponse> searchForStaff(
            String search,
            int page,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"))
        );
        Page<StaffPatientResponse> patients = patientRepository
                .findAll(buildStaffSearchSpecification(search), pageRequest)
                .map(patientMapper::toStaffResponse);
        return PageResponse.from(patients);
    }

    @Transactional
    public StaffPatientResponse createForStaff(CreateStaffPatientRequest request) {
        validateDateOfBirth(request.dateOfBirth());
        String fullName = normalizeFullName(request.fullName());
        String phone = request.phone().trim();
        requireNoDuplicate(fullName, phone, request.dateOfBirth());

        Patient patient = new Patient();
        patient.setUser(null);
        patient.setFullName(fullName);
        patient.setPhone(phone);
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender() == null ? Gender.UNSPECIFIED : request.gender());
        patient.setAddress(normalizeNullable(request.address()));
        patient.setMedicalHistory(normalizeNullable(request.medicalHistory()));
        patient.setAllergies(normalizeNullable(request.allergies()));

        return patientMapper.toStaffResponse(patientRepository.saveAndFlush(patient));
    }

    private Patient findByUserId(UUID userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth != null && dateOfBirth.isAfter(timeProvider.today())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Ngày sinh không được nằm trong tương lai"
            );
        }
    }

    private PatientProfileResponse toResponse(Patient patient) {
        User user = requireLinkedUser(patient);
        return new PatientProfileResponse(
                patient.getId(),
                user.getId(),
                user.getEmail(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getPhone(),
                patient.getAddress(),
                patient.getMedicalHistory(),
                patient.getAllergies(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }

    private User requireLinkedUser(Patient patient) {
        if (patient.getUser() == null) {
            throw new NotFoundException("Không tìm thấy tài khoản liên kết của bệnh nhân");
        }
        return patient.getUser();
    }

    private Specification<Patient> buildStaffSearchSpecification(String search) {
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch == null) {
            return Specification.unrestricted();
        }
        String pattern = "%" + escapeLikePattern(normalizedSearch) + "%";
        UUID patientId = parseUuid(normalizedSearch);
        return (root, query, criteriaBuilder) -> {
            Expression<String> patientName = criteriaBuilder.lower(root.get("fullName"));
            Expression<String> patientPhone = criteriaBuilder.lower(root.get("phone"));
            Predicate nameMatches = criteriaBuilder.like(
                    patientName,
                    pattern,
                    LIKE_ESCAPE
            );
            Predicate phoneMatches = criteriaBuilder.like(
                    patientPhone,
                    pattern,
                    LIKE_ESCAPE
            );
            if (patientId == null) {
                return criteriaBuilder.or(nameMatches, phoneMatches);
            }
            return criteriaBuilder.or(
                    criteriaBuilder.equal(root.get("id"), patientId),
                    nameMatches,
                    phoneMatches
            );
        };
    }

    private void requireNoDuplicate(String fullName, String phone, LocalDate dateOfBirth) {
        boolean duplicate = dateOfBirth == null
                ? patientRepository.existsByFullNameIgnoreCaseAndPhone(fullName, phone)
                : patientRepository.existsByFullNameIgnoreCaseAndPhoneAndDateOfBirth(
                        fullName,
                        phone,
                        dateOfBirth
                );
        if (duplicate) {
            throw new ConflictException(
                    "Đã tồn tại hồ sơ bệnh nhân trùng thông tin nhận diện"
            );
        }
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
