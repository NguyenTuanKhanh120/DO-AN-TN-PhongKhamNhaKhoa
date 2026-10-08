package com.dentalclinic.user;

import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.user.dto.AdminUserResponse;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository);
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
    void shouldReturnUserDetail() {
        User user = user(Role.DOCTOR, UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserResponse response = adminUserService.getUser(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void shouldUpdateUserStatus() {
        User user = user(Role.PATIENT, UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserResponse response = adminUserService.updateStatus(user.getId(), UserStatus.INACTIVE);

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        verify(userRepository).saveAndFlush(user);
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
