package com.dentalclinic.user.service;

import com.dentalclinic.auth.security.BcryptPasswordValidator;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.user.dto.AdminUserResponse;
import com.dentalclinic.user.dto.CreateStaffRequest;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.AdminAccountLockRepository;
import com.dentalclinic.user.repository.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final char LIKE_ESCAPE = '\\';

    private final UserRepository userRepository;
    private final AdminAccountLockRepository adminAccountLockRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    public AdminUserService(
            UserRepository userRepository,
            AdminAccountLockRepository adminAccountLockRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService
    ) {
        this.userRepository = userRepository;
        this.adminAccountLockRepository = adminAccountLockRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public AdminUserResponse createStaff(UUID actorUserId, CreateStaffRequest request) {
        requireActiveAdmin(actorUserId);
        BcryptPasswordValidator.validate(request.password());
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User staff = new User();
        staff.setEmail(normalizedEmail);
        staff.setPasswordHash(passwordEncoder.encode(request.password()));
        staff.setFullName(normalizeFullName(request.fullName()));
        staff.setRole(Role.STAFF);
        staff.setStatus(UserStatus.ACTIVE);

        try {
            return toResponse(userRepository.saveAndFlush(staff));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getStaff(
            String search,
            UserStatus status,
            int page,
            int size
    ) {
        return getUsers(search, Role.STAFF, status, page, size);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getStaff(UUID userId) {
        return toResponse(findStaff(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(
            String search,
            Role role,
            UserStatus status,
            int page,
            int size
    ) {
        Specification<User> specification = buildSpecification(search, role, status);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))
        );
        Page<AdminUserResponse> users = userRepository.findAll(specification, pageRequest)
                .map(this::toResponse);
        return PageResponse.from(users);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public AdminUserResponse updateStatus(
            UUID actorUserId,
            UUID targetUserId,
            UserStatus status
    ) {
        return updateStatus(actorUserId, targetUserId, status, null);
    }

    @Transactional
    public AdminUserResponse updateStaffStatus(
            UUID actorUserId,
            UUID targetUserId,
            UserStatus status
    ) {
        return updateStatus(actorUserId, targetUserId, status, Role.STAFF);
    }

    private AdminUserResponse updateStatus(
            UUID actorUserId,
            UUID targetUserId,
            UserStatus status,
            Role requiredTargetRole
    ) {
        adminAccountLockRepository.acquireTransactionLock();
        User actor = requireActiveAdmin(actorUserId);

        User target = findUser(targetUserId);
        if (requiredTargetRole != null && target.getRole() != requiredTargetRole) {
            throw new NotFoundException("Không tìm thấy tài khoản nhân viên");
        }
        if (target.getStatus() == status) {
            if (status == UserStatus.INACTIVE) {
                authSessionService.revokeAllForUser(target.getId());
            }
            return toResponse(target);
        }
        if (status == UserStatus.INACTIVE && target.getRole() == Role.ADMIN) {
            if (actor.getId().equals(target.getId())) {
                throw new ConflictException(
                        "Không thể vô hiệu hóa tài khoản quản trị đang đăng nhập."
                );
            }
            long activeAdmins = userRepository.countByRoleAndStatus(
                    Role.ADMIN,
                    UserStatus.ACTIVE
            );
            if (activeAdmins <= 1) {
                throw new ConflictException(
                        "Hệ thống phải có ít nhất một tài khoản quản trị đang hoạt động."
                );
            }
        }

        target.setStatus(status);
        userRepository.saveAndFlush(target);
        if (status == UserStatus.INACTIVE) {
            authSessionService.revokeAllForUser(target.getId());
        }
        return toResponse(target);
    }

    private Specification<User> buildSpecification(
            String search,
            Role role,
            UserStatus status
    ) {
        Specification<User> specification = Specification.unrestricted();
        String normalizedSearch = normalizeSearch(search);

        if (normalizedSearch != null) {
            String pattern = "%" + escapeLikePattern(normalizedSearch) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> {
                Expression<String> email = criteriaBuilder.lower(root.get("email"));
                Expression<String> fullName = criteriaBuilder.lower(root.get("fullName"));
                Predicate emailMatches = criteriaBuilder.like(email, pattern, LIKE_ESCAPE);
                Predicate fullNameMatches = criteriaBuilder.like(fullName, pattern, LIKE_ESCAPE);
                return criteriaBuilder.or(emailMatches, fullNameMatches);
            });
        }
        if (role != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("role"), role));
        }
        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }
        return specification;
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));
    }

    private User findStaff(UUID userId) {
        User user = findUser(userId);
        if (user.getRole() != Role.STAFF) {
            throw new NotFoundException("Không tìm thấy tài khoản nhân viên");
        }
        return user;
    }

    private User requireActiveAdmin(UUID userId) {
        User user = findUser(userId);
        if (user.getRole() != Role.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException(
                    "Chỉ tài khoản quản trị đang hoạt động mới được quản lý tài khoản"
            );
        }
        return user;
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
