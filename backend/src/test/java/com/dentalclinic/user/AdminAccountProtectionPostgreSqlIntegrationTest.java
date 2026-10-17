package com.dentalclinic.user;

import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.dentalclinic.user.service.AdminUserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
class AdminAccountProtectionPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "admin-protection-integration-test");
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAllById(createdUserIds);
        userRepository.flush();
    }

    @Test
    void shouldRejectSelfDeactivationInRealTransaction() {
        User admin = saveActiveAdmin();

        assertThatThrownBy(() -> adminUserService.updateStatus(
                admin.getId(), admin.getId(), UserStatus.INACTIVE
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể vô hiệu hóa tài khoản quản trị đang đăng nhập.");

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void concurrentAdminsDeactivatingEachOtherMustLeaveOneActiveAdmin() throws Exception {
        User first = saveActiveAdmin();
        User second = saveActiveAdmin();
        CountDownLatch start = new CountDownLatch(1);

        List<Throwable> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> firstRequest = executor.submit(() -> runConcurrently(
                    start,
                    () -> adminUserService.updateStatus(
                            first.getId(), second.getId(), UserStatus.INACTIVE
                    )
            ));
            Future<Throwable> secondRequest = executor.submit(() -> runConcurrently(
                    start,
                    () -> adminUserService.updateStatus(
                            second.getId(), first.getId(), UserStatus.INACTIVE
                    )
            ));
            start.countDown();
            results = Arrays.asList(firstRequest.get(), secondRequest.get());
        }

        long successfulRequests = results.stream().filter(result -> result == null).count();
        List<Throwable> failures = results.stream().filter(result -> result != null).toList();
        assertThat(successfulRequests).isEqualTo(1);
        assertThat(failures).singleElement().satisfies(failure ->
                assertThat(failure).isInstanceOf(ForbiddenException.class)
        );
        assertThat(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .isGreaterThanOrEqualTo(1);
        assertThat(List.of(
                userRepository.findById(first.getId()).orElseThrow().getStatus(),
                userRepository.findById(second.getId()).orElseThrow().getStatus()
        )).containsExactlyInAnyOrder(UserStatus.ACTIVE, UserStatus.INACTIVE);
    }

    private Throwable runConcurrently(CountDownLatch start, ThrowingOperation operation) {
        try {
            start.await();
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private User saveActiveAdmin() {
        User user = new User();
        user.setEmail("phase261-admin-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$phase261.admin.protection.hash");
        user.setFullName("Quản trị viên Phase 26.1");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
