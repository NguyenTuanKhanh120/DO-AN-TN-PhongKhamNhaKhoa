package com.dentalclinic.doctor;

import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.doctor.dto.CreateDoctorRequest;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.service.DoctorManagementService;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("development")
class DoctorCreationRollbackPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private DoctorManagementService doctorManagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @MockitoBean
    private DoctorScheduleRepository doctorScheduleRepository;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-creation-rollback-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRollbackUserAndDoctorWhenDefaultSchedulePersistenceFails() {
        String marker = UUID.randomUUID().toString();
        String email = "rollback-doctor-" + marker + "@example.com";
        String licenseNumber = "ROLLBACK-" + marker;
        when(doctorScheduleRepository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("simulated schedule failure"));

        assertThatThrownBy(() -> doctorManagementService.createDoctor(new CreateDoctorRequest(
                email,
                "StrongPassword123!",
                "Bác sĩ Rollback",
                "Nha khoa tổng quát",
                licenseNumber,
                null,
                5
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("Lịch làm việc cho ngày và buổi này đã tồn tại");

        assertThat(userRepository.existsByEmail(email)).isFalse();
        assertThat(doctorRepository.existsByLicenseNumber(licenseNumber)).isFalse();
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
