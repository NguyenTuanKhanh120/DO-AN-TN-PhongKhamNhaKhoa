package com.dentalclinic.ai.recommendation;

import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendationResult;
import com.dentalclinic.ai.recommendation.service.ServiceDoctorRecommendationService;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.DoctorServiceMatch;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceDoctorRecommendationServiceTest {

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Mock
    private DentalServiceManagementService dentalServiceManagementService;

    @Mock
    private DoctorServiceMappingService doctorServiceMappingService;

    private ServiceDoctorRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new ServiceDoctorRecommendationService(
                knowledgeRetrievalService,
                dentalServiceManagementService,
                doctorServiceMappingService
        );
    }

    @Test
    void shouldRecommendOnlyVerifiedServicesHavingActiveMappedDoctors() {
        String query = "Tôi bị đau răng và muốn tìm bác sĩ phù hợp";
        KnowledgeSourceTrace source = source("NIDCR-VI-001", "Sâu răng");
        when(knowledgeRetrievalService.retrieve(KnowledgeRetrievalRequest.of(query)))
                .thenReturn(List.of(
                        evidence(
                                List.of("Khám nha khoa tổng quát", "Điều trị sâu răng"),
                                source
                        ),
                        evidence(List.of("khám nha khoa tổng quát"), source)
                ));
        DentalServiceResponse generalExamination = dentalService("Khám răng tổng quát");
        DentalServiceResponse restorativeService = dentalService("Trám răng sâu");
        when(dentalServiceManagementService.findActiveServicesMatchingCategoryHints(List.of(
                "Khám nha khoa tổng quát",
                "Điều trị sâu răng"
        ))).thenReturn(List.of(generalExamination, restorativeService));
        DoctorResponse doctor = doctor();
        when(doctorServiceMappingService.getActiveDoctorMappingsByServiceIds(List.of(
                generalExamination.id(),
                restorativeService.id()
        ))).thenReturn(List.of(new DoctorServiceMatch(generalExamination.id(), doctor)));

        ServiceDoctorRecommendationResult result = service.recommend(query);

        assertThat(result.serviceCategoryHints()).containsExactly(
                "Khám nha khoa tổng quát",
                "Điều trị sâu răng"
        );
        assertThat(result.sources()).containsExactly(source);
        assertThat(result.recommendations()).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.service()).isEqualTo(generalExamination);
            assertThat(recommendation.doctors()).containsExactly(doctor);
        });
    }

    @Test
    void shouldNotInventServiceWhenRagHintsDoNotMatchDatabase() {
        String query = "Tôi đau khớp hàm";
        when(knowledgeRetrievalService.retrieve(KnowledgeRetrievalRequest.of(query)))
                .thenReturn(List.of(evidence(
                        List.of("Khám khớp thái dương hàm"),
                        source("NIDCR-VI-005", "Rối loạn khớp thái dương hàm")
                )));
        when(dentalServiceManagementService.findActiveServicesMatchingCategoryHints(
                List.of("Khám khớp thái dương hàm")
        )).thenReturn(List.of());

        ServiceDoctorRecommendationResult result = service.recommend(query);

        assertThat(result.recommendations()).isEmpty();
        verify(doctorServiceMappingService, never())
                .getActiveDoctorMappingsByServiceIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldStopBeforeDatabaseVerificationWhenRagHasNoServiceHints() {
        String query = "Nội dung không có gợi ý dịch vụ";
        when(knowledgeRetrievalService.retrieve(KnowledgeRetrievalRequest.of(query)))
                .thenReturn(List.of(evidence(
                        List.of(),
                        source("NIDCR-VI-999", "Kiến thức chung")
                )));

        ServiceDoctorRecommendationResult result = service.recommend(query);

        assertThat(result.serviceCategoryHints()).isEmpty();
        assertThat(result.recommendations()).isEmpty();
        assertThat(result.sources()).hasSize(1);
        verify(dentalServiceManagementService, never())
                .findActiveServicesMatchingCategoryHints(org.mockito.ArgumentMatchers.anyList());
        verify(doctorServiceMappingService, never())
                .getActiveDoctorMappingsByServiceIds(org.mockito.ArgumentMatchers.anyList());
    }

    private KnowledgeRetrievalResult evidence(
            List<String> serviceHints,
            KnowledgeSourceTrace source
    ) {
        return new KnowledgeRetrievalResult(
                UUID.randomUUID(),
                "Nội dung tham khảo nha khoa",
                "DENTAL_CONDITION",
                "vi",
                List.of("đau răng"),
                serviceHints,
                List.of("nha khoa tổng quát"),
                source,
                0.91
        );
    }

    private KnowledgeSourceTrace source(String recordId, String title) {
        return new KnowledgeSourceTrace(
                "nidcr-dental-knowledge-vi",
                "1.0.0",
                recordId,
                title,
                "https://www.nidcr.nih.gov/health-info"
        );
    }

    private DentalServiceResponse dentalService(String name) {
        return new DentalServiceResponse(
                UUID.randomUUID(),
                name,
                "Mô tả " + name,
                new BigDecimal("200000.00"),
                null,
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private DoctorResponse doctor() {
        return new DoctorResponse(
                UUID.randomUUID(),
                "Bác sĩ Nguyễn Văn An",
                "Nha khoa tổng quát",
                "GP-AI-09",
                "Bác sĩ kiểm thử",
                new BigDecimal("4.5"),
                8,
                DoctorStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }
}
