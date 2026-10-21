package com.dentalclinic.user;

import com.dentalclinic.auth.service.AuthSessionService;
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
import com.dentalclinic.user.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAccountLockRepository adminAccountLockRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthSessionService authSessionService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                adminAccountLockRepository,
                passwordEncoder,
                authSessionService
        );
    }

    @Test
    void shouldReturnPagedUsersWithoutPasswordHash() {
        User user = user(Role.PATIENT, UserStatus.ACTIVE);
        PageRequest expectedPageable = PageRequest.of(
                1,
                5,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.asc("id")
                )
        );
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(expectedPageable)))
                .thenReturn(new PageImpl<>(List.of(user), expectedPageable, 6));

        PageResponse<AdminUserResponse> response = adminUserService.getUsers(
                "  PATIENT  ",
                Role.PATIENT,
                UserStatus.ACTIVE,
                1,
                5
        );

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(user.getId());
            assertThat(item.email()).isEqualTo(user.getEmail());
            assertThat(item.fullName()).isEqualTo(user.getFullName());
            assertThat(item.role()).isEqualTo(Role.PATIENT);
            assertThat(item.status()).isEqualTo(UserStatus.ACTIVE);
        });
    }

    @Test
    void shouldCreateActiveStaffWithEncodedPassword() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        CreateStaffRequest request = new CreateStaffRequest(
                " STAFF@Example.com ",
                "StrongPassword123!",
                "  Nhân viên   lễ tân  "
        );
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("staff@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AdminUserResponse response = adminUserService.createStaff(actor.getId(), request);

        ArgumentCaptor<User> staffCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(staffCaptor.capture());
        User staff = staffCaptor.getValue();
        assertThat(staff.getEmail()).isEqualTo("staff@example.com");
        assertThat(staff.getFullName()).isEqualTo("Nhân viên lễ tân");
        assertThat(staff.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(staff.getRole()).isEqualTo(Role.STAFF);
        assertThat(staff.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.role()).isEqualTo(Role.STAFF);
    }

    @Test
    void shouldRejectNonStaffFromStaffDetail() {
        User doctor = user(Role.DOCTOR, UserStatus.ACTIVE);
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> adminUserService.getStaff(doctor.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài khoản nhân viên");
    }

    @Test
    void shouldReturnUserDetail() {
        User user = user(Role.DOCTOR, UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserResponse response = adminUserService.getUser(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void shouldAllowAdminToDeactivatePatient() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        User user = user(Role.PATIENT, UserStatus.ACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserResponse response = adminUserService.updateStatus(
                actor.getId(),
                user.getId(),
                UserStatus.INACTIVE
        );

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        verify(adminAccountLockRepository).acquireTransactionLock();
        verify(userRepository).saveAndFlush(user);
        verify(authSessionService).revokeAllForUser(user.getId());
    }

    @Test
    void shouldAllowAdminToDeactivateDoctor() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        User doctor = user(Role.DOCTOR, UserStatus.ACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        AdminUserResponse response = adminUserService.updateStatus(
                actor.getId(),
                doctor.getId(),
                UserStatus.INACTIVE
        );

        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        verify(userRepository).saveAndFlush(doctor);
        verify(authSessionService).revokeAllForUser(doctor.getId());
    }

    @Test
    void shouldRejectAdminSelfDeactivationWithoutChangingData() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> adminUserService.updateStatus(
                actor.getId(),
                actor.getId(),
                UserStatus.INACTIVE
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể vô hiệu hóa tài khoản quản trị đang đăng nhập.");

        assertThat(actor.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectDeactivatingLastActiveAdminWithoutChangingData() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        User target = user(Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.updateStatus(
                actor.getId(),
                target.getId(),
                UserStatus.INACTIVE
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Hệ thống phải có ít nhất một tài khoản quản trị đang hoạt động.");

        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldAllowDeactivatingAnotherAdminWhenAnActiveAdminRemains() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        User target = user(Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

        AdminUserResponse response = adminUserService.updateStatus(
                actor.getId(),
                target.getId(),
                UserStatus.INACTIVE
        );

        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        verify(userRepository).saveAndFlush(target);
        verify(authSessionService).revokeAllForUser(target.getId());
    }

    @Test
    void shouldRejectInactiveAdminActor() {
        User actor = user(Role.ADMIN, UserStatus.INACTIVE);
        User target = user(Role.PATIENT, UserStatus.ACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> adminUserService.updateStatus(
                actor.getId(),
                target.getId(),
                UserStatus.INACTIVE
        )).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("quản trị đang hoạt động");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldHandleSameStatusIdempotently() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE);
        User target = user(Role.DOCTOR, UserStatus.INACTIVE);
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        AdminUserResponse response = adminUserService.updateStatus(
                actor.getId(),
                target.getId(),
                UserStatus.INACTIVE
        );

        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userRepository, never()).countByRoleAndStatus(any(), any());
        verify(authSessionService).revokeAllForUser(target.getId());
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUser(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài khoản");
    }

    @Test
    void shouldUseRequestedPageAndStableSort() {
        PageRequest expectedPageable = PageRequest.of(
                2,
                10,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.asc("id")
                )
        );
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(expectedPageable)))
                .thenReturn(new PageImpl<>(List.of(), expectedPageable, 0));

        adminUserService.getUsers(null, null, null, 2, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<User>>any(),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue()).isEqualTo(expectedPageable);
    }

    private User user(Role role, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setPasswordHash("secret-hash-that-must-not-be-returned");
        user.setFullName("Người dùng kiểm thử");
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return user;
    }
}
