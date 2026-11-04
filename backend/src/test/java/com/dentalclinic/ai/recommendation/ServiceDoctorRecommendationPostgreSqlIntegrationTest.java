package com.dentalclinic.ai.recommendation;

import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendationResult;
import com.dentalclinic.ai.recommendation.service.ServiceDoctorRecommendationService;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("development")
@Transactional
class ServiceDoctorRecommendationPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private ServiceDoctorRecommendationService recommendationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DentalServiceRepository dentalServiceRepository;

    @Autowired
    private DoctorServiceRepository doctorServiceRepository;

    @MockitoBean
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "ai-recommendation-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @Test
    void shouldVerifyServiceMappingAccountAndDoctorStatusOnPostgreSql() {
        String marker = UUID.randomUUID().toString().replace("-", "");
        String serviceHint = "Phục hồi men răng chuyên biệt " + marker;
        DentalService activeService = saveService(serviceHint, true);
        DentalService inactiveService = saveService(serviceHint + " ngừng hoạt động", false);

        Doctor activeDoctor = saveDoctor(
                marker + "-active",
                UserStatus.ACTIVE,
                DoctorStatus.ACTIVE
        );
        Doctor inactiveAccountDoctor = saveDoctor(
                marker + "-account-inactive",
                UserStatus.INACTIVE,
                DoctorStatus.ACTIVE
        );
        Doctor onLeaveDoctor = saveDoctor(
                marker + "-on-leave",
                UserStatus.ACTIVE,
                DoctorStatus.ON_LEAVE
        );
        Doctor inactiveDoctor = saveDoctor(
                marker + "-doctor-inactive",
                UserStatus.ACTIVE,
                DoctorStatus.INACTIVE
        );
        saveDoctor(marker + "-unassigned", UserStatus.ACTIVE, DoctorStatus.ACTIVE);

        saveMapping(activeDoctor, activeService);
        saveMapping(activeDoctor, inactiveService);
        saveMapping(inactiveAccountDoctor, activeService);
        saveMapping(onLeaveDoctor, activeService);
        saveMapping(inactiveDoctor, activeService);

        String query = "Tôi cần " + serviceHint;
        when(knowledgeRetrievalService.retrieve(KnowledgeRetrievalRequest.of(query)))
                .thenReturn(List.of(evidence(serviceHint)));

        ServiceDoctorRecommendationResult result = recommendationService.recommend(query);

        assertThat(result.serviceCategoryHints()).containsExactly(serviceHint);
        assertThat(result.recommendations()).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.service().id()).isEqualTo(activeService.getId());
            assertThat(recommendation.service().active()).isTrue();
            assertThat(recommendation.doctors()).singleElement().satisfies(doctor -> {
                assertThat(doctor.id()).isEqualTo(activeDoctor.getId());
                assertThat(doctor.workingStatus()).isEqualTo(DoctorStatus.ACTIVE);
            });
        });
    }

    private KnowledgeRetrievalResult evidence(String serviceHint) {
        return new KnowledgeRetrievalResult(
                UUID.randomUUID(),
                "Nội dung tham khảo",
                "DENTAL_CONDITION",
                "vi",
                List.of("ê buốt răng"),
                List.of(serviceHint),
                List.of("nha khoa phục hồi"),
                new KnowledgeSourceTrace(
                        "nidcr-dental-knowledge-vi",
                        "1.0.0",
                        "AI09-POSTGRESQL",
                        "Kiểm thử gợi ý dịch vụ",
                        "https://www.nidcr.nih.gov/health-info"
                ),
                0.95
        );
    }

    private DentalService saveService(String name, boolean active) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Mô tả " + name);
        service.setPrice(new BigDecimal("500000.00"));
        service.setActive(active);
        return dentalServiceRepository.saveAndFlush(service);
    }

    private Doctor saveDoctor(
            String marker,
            UserStatus accountStatus,
            DoctorStatus doctorStatus
    ) {
        User user = new User();
        user.setEmail(marker + "@ai09.example.com");
        user.setPasswordHash("$2a$10$ai09-not-used-in-this-test");
        user.setFullName("Bác sĩ " + marker);
        user.setRole(Role.DOCTOR);
        user.setStatus(accountStatus);
        User savedUser = userRepository.saveAndFlush(user);

        Doctor doctor = new Doctor();
        doctor.setUser(savedUser);
        doctor.setSpecialization("Nha khoa phục hồi");
        doctor.setLicenseNumber("AI09-" + marker);
        doctor.setBio("Bác sĩ kiểm thử AI-09");
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(doctorStatus);
        return doctorRepository.saveAndFlush(doctor);
    }

    private void saveMapping(Doctor doctor, DentalService service) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);
        doctorServiceRepository.saveAndFlush(mapping);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
