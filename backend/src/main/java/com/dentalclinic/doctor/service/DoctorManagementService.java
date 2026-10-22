package com.dentalclinic.doctor.service;

import com.dentalclinic.auth.security.BcryptPasswordValidator;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.doctor.dto.AdminDoctorResponse;
import com.dentalclinic.doctor.dto.CreateDoctorRequest;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.UpdateDoctorRequest;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.dentalclinic.schedule.service.DoctorScheduleService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
public class DoctorManagementService {

    private static final char LIKE_ESCAPE = '\\';

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorScheduleService doctorScheduleService;

    public DoctorManagementService(
            DoctorRepository doctorRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DoctorScheduleService doctorScheduleService
    ) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorScheduleService = doctorScheduleService;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> getDoctors(
            String search,
            String specialization,
            DoctorStatus workingStatus,
            int page,
            int size
    ) {
        Specification<Doctor> specification = buildPublicSpecification(
                search,
                specialization,
                workingStatus
        );
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("user.fullName"), Sort.Order.asc("id"))
        );
        Page<DoctorResponse> doctors = doctorRepository.findAll(specification, pageRequest)
                .map(this::toPublicResponse);
        return PageResponse.from(doctors);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctor(UUID doctorId) {
        Doctor doctor = doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        return toPublicResponse(doctor);
    }

    @Transactional
    public AdminDoctorResponse createDoctor(CreateDoctorRequest request) {
        BcryptPasswordValidator.validate(request.password());
        String email = normalizeEmail(request.email());
        String licenseNumber = normalizeRequired(request.licenseNumber());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (doctorRepository.existsByLicenseNumber(licenseNumber)) {
            throw new BusinessException(ErrorCode.LICENSE_NUMBER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(normalizeFullName(request.fullName()));
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization(normalizeRequired(request.specialization()));
        doctor.setLicenseNumber(licenseNumber);
        doctor.setBio(normalizeNullable(request.bio()));
        doctor.setRating(new BigDecimal("0.0"));
        doctor.setExperienceYears(request.experienceYears());
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);

        try {
            doctorRepository.saveAndFlush(doctor);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.LICENSE_NUMBER_ALREADY_EXISTS);
        }
        doctorScheduleService.createDefaultWeeklySchedule(doctor);
        return toAdminResponse(doctor);
    }

    @Transactional
    public AdminDoctorResponse updateDoctor(UUID doctorId, UpdateDoctorRequest request) {
        Doctor doctor = findDoctor(doctorId);
        String licenseNumber = normalizeRequired(request.licenseNumber());
        if (doctorRepository.existsByLicenseNumberAndIdNot(licenseNumber, doctorId)) {
            throw new BusinessException(ErrorCode.LICENSE_NUMBER_ALREADY_EXISTS);
        }

        doctor.getUser().setFullName(normalizeFullName(request.fullName()));
        doctor.setSpecialization(normalizeRequired(request.specialization()));
        doctor.setLicenseNumber(licenseNumber);
        doctor.setBio(normalizeNullable(request.bio()));
        doctor.setRating(request.rating());
        doctor.setExperienceYears(request.experienceYears());

        try {
            doctorRepository.saveAndFlush(doctor);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.LICENSE_NUMBER_ALREADY_EXISTS);
        }
        return toAdminResponse(doctor);
    }

    @Transactional
    public AdminDoctorResponse updateWorkingStatus(UUID doctorId, DoctorStatus status) {
        Doctor doctor = findDoctor(doctorId);
        doctor.setWorkingStatus(status);
        doctorRepository.saveAndFlush(doctor);
        return toAdminResponse(doctor);
    }

    private Specification<Doctor> buildPublicSpecification(
            String search,
            String specialization,
            DoctorStatus workingStatus
    ) {
        Specification<Doctor> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("status"), UserStatus.ACTIVE);
        String normalizedSearch = normalizeSearch(search);
        String normalizedSpecialization = normalizeSearch(specialization);

        if (normalizedSearch != null) {
            String pattern = "%" + escapeLikePattern(normalizedSearch) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> {
                Expression<String> fullName = criteriaBuilder.lower(root.get("user").get("fullName"));
                Expression<String> doctorSpecialization = criteriaBuilder.lower(root.get("specialization"));
                Expression<String> licenseNumber = criteriaBuilder.lower(root.get("licenseNumber"));
                Predicate nameMatches = criteriaBuilder.like(fullName, pattern, LIKE_ESCAPE);
                Predicate specializationMatches = criteriaBuilder.like(
                        doctorSpecialization,
                        pattern,
                        LIKE_ESCAPE
                );
                Predicate licenseMatches = criteriaBuilder.like(licenseNumber, pattern, LIKE_ESCAPE);
                return criteriaBuilder.or(nameMatches, specializationMatches, licenseMatches);
            });
        }
        if (normalizedSpecialization != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("specialization")),
                            normalizedSpecialization
                    ));
        }
        if (workingStatus != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("workingStatus"), workingStatus));
        }
        return specification;
    }

    private Doctor findDoctor(UUID doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
    }

    private DoctorResponse toPublicResponse(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getUser().getFullName(),
                doctor.getSpecialization(),
                doctor.getLicenseNumber(),
                doctor.getBio(),
                doctor.getRating(),
                doctor.getExperienceYears(),
                doctor.getWorkingStatus(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }

    private AdminDoctorResponse toAdminResponse(Doctor doctor) {
        User user = doctor.getUser();
        return new AdminDoctorResponse(
                doctor.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                doctor.getSpecialization(),
                doctor.getLicenseNumber(),
                doctor.getBio(),
                doctor.getRating(),
                doctor.getExperienceYears(),
                doctor.getWorkingStatus(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }

    private String normalizeRequired(String value) {
        return value.trim().replaceAll("\\s+", " ");
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
}
