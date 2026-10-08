package com.dentalclinic.user.service;

import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.user.dto.AdminUserResponse;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final char LIKE_ESCAPE = '\\';

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
    public AdminUserResponse updateStatus(UUID userId, UserStatus status) {
        User user = findUser(userId);
        user.setStatus(status);
        userRepository.saveAndFlush(user);
        return toResponse(user);
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

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
