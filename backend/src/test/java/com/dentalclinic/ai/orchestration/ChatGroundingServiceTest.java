package com.dentalclinic.ai.orchestration;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.service.ChatContextResolver;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.ai.gateway.AiCallExecutor;
import com.dentalclinic.ai.orchestration.service.ChatGroundingService;
import com.dentalclinic.ai.orchestration.service.ChatQueryPlanner;
import com.dentalclinic.ai.orchestration.service.DatabaseQueryExecutor;
import com.dentalclinic.ai.orchestration.service.RagQueryExecutor;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendation;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendationResult;
import com.dentalclinic.ai.recommendation.service.ServiceDoctorRecommendationService;
import com.dentalclinic.ai.slot.model.ChatSlotLookupRequest;
import com.dentalclinic.ai.slot.model.ChatSlotLookupResult;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.ai.slot.model.DoctorSlotAvailability;
import com.dentalclinic.ai.slot.model.SlotLookupTarget;
import com.dentalclinic.ai.slot.service.AiSlotLookupService;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.DoctorServiceMatch;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorManagementService;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatGroundingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 30);

    private final KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
    private final ServiceDoctorRecommendationService recommendationService =
            mock(ServiceDoctorRecommendationService.class);
    private final DentalServiceManagementService dentalServiceService =
            mock(DentalServiceManagementService.class);
    private final DoctorManagementService doctorService = mock(DoctorManagementService.class);
    private final DoctorServiceMappingService mappingService = mock(DoctorServiceMappingService.class);
    private final AiSlotLookupService slotLookupService = mock(AiSlotLookupService.class);
    private final AiCallExecutor aiCallExecutor = new AiCallExecutor(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            100,
            Duration.ofSeconds(1)
    );
    private final DatabaseQueryExecutor databaseQueryExecutor = new DatabaseQueryExecutor(
            recommendationService,
            dentalServiceService,
            doctorService,
            mappingService,
            slotLookupService
    );
    private final RagQueryExecutor ragQueryExecutor = new RagQueryExecutor(
            retrievalService,
            aiCallExecutor
    );
    private final ChatGroundingService service = new ChatGroundingService(
            databaseQueryExecutor,
            ragQueryExecutor,
            new ChatQueryPlanner()
    );

    @Test
    void shouldGroundRagRecommendationsAndSlotsWithVerifiedDatabaseData() {
        DentalServiceResponse dentalService = dentalService("Khám răng tổng quát");
        DoctorResponse doctor = doctor("Bác sĩ Nguyễn Văn An");
        KnowledgeRetrievalResult evidence = knowledge();
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(
                        ChatIntent.SYMPTOM_CONSULTATION,
                        ChatIntent.SERVICE_RECOMMENDATION,
                        ChatIntent.DOCTOR_RECOMMENDATION,
                        ChatIntent.AVAILABLE_SLOTS
                ),
                List.of("đau răng"),
                null,
                null,
                ChatDateRange.single(TOMORROW),
                ChatSessionPreference.AFTERNOON,
                null
        );
        ChatContextResolution resolution = resolution(context);
        ChatSlotSuggestion slot = new ChatSlotSuggestion(
                doctor.id(),
                dentalService.id(),
                TOMORROW,
                ChatSessionPreference.AFTERNOON,
                1,
                LocalTime.of(14, 0),
                LocalTime.of(17, 0),
                LocalTime.of(14, 0),
                OffsetDateTime.parse("2026-10-30T14:00:00+07:00")
        );

        when(retrievalService.retrieve(any(KnowledgeRetrievalRequest.class)))
                .thenReturn(List.of(evidence));
        when(recommendationService.recommendFromEvidence(List.of(evidence)))
                .thenReturn(new ServiceDoctorRecommendationResult(
                        List.of("Khám răng tổng quát"),
                        List.of(new ServiceDoctorRecommendation(dentalService, List.of(doctor))),
                        List.of(evidence.source())
                ));
        when(mappingService.getActiveDoctorMappingsByServiceIds(List.of(dentalService.id())))
                .thenReturn(List.of(new DoctorServiceMatch(dentalService.id(), doctor)));
        ChatSlotLookupRequest expectedRequest = new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(doctor.id(), dentalService.id())),
                TOMORROW,
                TOMORROW,
                ChatSessionPreference.AFTERNOON
        );
        when(slotLookupService.lookup(expectedRequest)).thenReturn(new ChatSlotLookupResult(
                TOMORROW,
                TOMORROW,
                ChatSessionPreference.AFTERNOON,
                List.of(new DoctorSlotAvailability(
                        doctor.id(),
                        dentalService.id(),
                        List.of(slot)
                ))
        ));

        var result = service.resolve(
                "Tôi đau răng, chiều mai bác sĩ nào còn lịch?",
                resolution
        );

        assertThat(result.knowledgeRequired()).isTrue();
        assertThat(result.knowledgeAvailable()).isTrue();
        assertThat(result.knowledge()).containsExactly(evidence);
        assertThat(result.services()).containsExactly(dentalService);
        assertThat(result.doctors()).containsExactly(doctor);
        assertThat(result.slots()).containsExactly(slot);
        verify(slotLookupService).lookup(expectedRequest);
    }

    @Test
    void shouldResolveDoctorByNameAndLookupDoctorOnlySlotsWithoutRag() {
        DoctorResponse doctor = doctor("Bác sĩ Nguyễn Văn An");
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS),
                List.of(),
                new ChatEntityReference(null, doctor.fullName()),
                null,
                ChatDateRange.single(TOMORROW),
                ChatSessionPreference.MORNING,
                null
        );
        when(doctorService.getDoctors(
                doctor.fullName(),
                null,
                DoctorStatus.ACTIVE,
                0,
                50
        )).thenReturn(page(List.of(doctor)));
        when(mappingService.getActiveDoctorMappingsByServiceIds(List.of())).thenReturn(List.of());
        ChatSlotLookupRequest expectedRequest = new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(doctor.id(), null)),
                TOMORROW,
                TOMORROW,
                ChatSessionPreference.MORNING
        );
        when(slotLookupService.lookup(expectedRequest)).thenReturn(new ChatSlotLookupResult(
                TOMORROW,
                TOMORROW,
                ChatSessionPreference.MORNING,
                List.of(new DoctorSlotAvailability(doctor.id(), null, List.of()))
        ));

        var result = service.resolve("Bác sĩ Nguyễn Văn An sáng mai còn lịch không?", resolution(context));

        assertThat(result.knowledgeRequired()).isFalse();
        assertThat(result.context().selectedDoctor().id()).isEqualTo(doctor.id());
        assertThat(result.doctors()).containsExactly(doctor);
        assertThat(result.slots()).isEmpty();
        verify(retrievalService, never()).retrieve(any());
        verify(slotLookupService).lookup(expectedRequest);
    }

    @Test
    void shouldLoadAllActiveServicesForServiceCatalogWithoutRag() {
        DentalServiceResponse cleaning = dentalService("Cạo vôi răng");
        DentalServiceResponse generalExamination = dentalService("Khám răng tổng quát");
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.SERVICE_CATALOG),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
        when(dentalServiceService.getActiveServices())
                .thenReturn(List.of(cleaning, generalExamination));

        var result = service.resolve(
                "Hiện tại nha khoa có những dịch vụ nào?",
                resolution(context)
        );

        assertThat(result.services()).containsExactly(cleaning, generalExamination);
        assertThat(result.knowledgeRequired()).isFalse();
        assertThat(result.doctors()).isEmpty();
        assertThat(result.slots()).isEmpty();
        verify(retrievalService, never()).retrieve(any());
        verify(mappingService, never()).getActiveDoctorMappingsByServiceIds(any());
    }

    @Test
    void shouldLoadOnlySelectedServiceForPriceQuestion() {
        DentalServiceResponse generalExamination = dentalService("Khám răng tổng quát");
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.SERVICE_CATALOG),
                List.of(),
                null,
                new ChatEntityReference(
                        generalExamination.id(),
                        generalExamination.name()
                ),
                null,
                null,
                null
        );
        when(dentalServiceService.getService(generalExamination.id()))
                .thenReturn(generalExamination);

        var result = service.resolve(
                "Dịch vụ khám răng tổng quát giá bao nhiêu?",
                resolution(context)
        );

        assertThat(result.services()).containsExactly(generalExamination);
        assertThat(result.context().selectedService().id())
                .isEqualTo(generalExamination.id());
        verify(dentalServiceService, never()).getActiveServices();
        verify(retrievalService, never()).retrieve(any());
    }

    @Test
    void shouldSendOnlyNewSymptomsToRagWithoutResolvingPreviousDoctorOrService() {
        var resolution = new ChatContextResolver().resolve(previousContext(), new ChatUnderstanding(
                List.of(ChatIntent.SYMPTOM_CONSULTATION),
                new ChatEntities(List.of("ê buốt răng"), null, null, null, null, null, null, null, null, null),
                ChatFollowUpType.NONE
        ));
        when(retrievalService.retrieve(any(KnowledgeRetrievalRequest.class))).thenReturn(List.of(knowledge()));

        var result = service.resolve("Tôi bị ê buốt răng", resolution);

        ArgumentCaptor<KnowledgeRetrievalRequest> query = ArgumentCaptor.forClass(KnowledgeRetrievalRequest.class);
        verify(retrievalService).retrieve(query.capture());
        assertThat(query.getValue().query()).contains("ê buốt răng").doesNotContain("đau răng");
        assertThat(result.context().selectedDoctor()).isNull();
        assertThat(result.context().selectedService()).isNull();
        verifyNoInteractions(doctorService, dentalServiceService, slotLookupService);
    }

    @Test
    void shouldLoadWholeCatalogAfterPreviousSpecificServiceConversation() {
        var resolution = new ChatContextResolver().resolve(previousContext(), new ChatUnderstanding(
                List.of(ChatIntent.SERVICE_CATALOG), ChatEntities.empty(), ChatFollowUpType.NONE
        ));
        var cleaning = dentalService("Cạo vôi răng");
        var examination = dentalService("Khám răng tổng quát");
        when(dentalServiceService.getActiveServices()).thenReturn(List.of(cleaning, examination));

        var result = service.resolve("Phòng khám có những dịch vụ nào?", resolution);

        assertThat(result.services()).containsExactly(cleaning, examination);
        assertThat(result.context().symptoms()).isEmpty();
        verify(dentalServiceService, never()).getService(any());
        verifyNoInteractions(doctorService, retrievalService, slotLookupService);
    }

    @Test
    void shouldLookupNewDoctorWithNoPreviousServiceOrMorningFilter() {
        var doctor = doctor("Bác sĩ Demo");
        var resolution = new ChatContextResolver().resolve(previousContext(), new ChatUnderstanding(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS),
                new ChatEntities(List.of(), doctor.fullName(), null, null, null, TOMORROW, null, null, null, null),
                ChatFollowUpType.NONE
        ));
        when(doctorService.getDoctors(doctor.fullName(), null, DoctorStatus.ACTIVE, 0, 50))
                .thenReturn(page(List.of(doctor)));
        when(mappingService.getActiveDoctorMappingsByServiceIds(List.of())).thenReturn(List.of());
        var expectedRequest = new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(doctor.id(), null)), TOMORROW, TOMORROW, null
        );
        when(slotLookupService.lookup(expectedRequest)).thenReturn(new ChatSlotLookupResult(
                TOMORROW, TOMORROW, null, List.of(new DoctorSlotAvailability(doctor.id(), null, List.of()))
        ));

        var result = service.resolve("Bác sĩ Demo ngày mai còn ca không?", resolution);

        verify(slotLookupService).lookup(expectedRequest);
        assertThat(result.context().symptoms()).isEmpty();
        verifyNoInteractions(dentalServiceService, retrievalService);
    }

    private ChatSemanticContext previousContext() {
        return new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS), List.of("đau răng"),
                new ChatEntityReference(UUID.randomUUID(), "Bác sĩ Nguyễn Văn An"),
                new ChatEntityReference(UUID.randomUUID(), "Cạo vôi răng"),
                ChatDateRange.single(TOMORROW.minusDays(1)), ChatSessionPreference.MORNING, "08:00"
        );
    }

    private ChatContextResolution resolution(ChatSemanticContext context) {
        return new ChatContextResolution(
                context,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
    }

    private DentalServiceResponse dentalService(String name) {
        return new DentalServiceResponse(
                UUID.randomUUID(),
                name,
                "Dịch vụ kiểm tra sức khỏe răng miệng",
                new BigDecimal("200000"),
                null,
                true,
                NOW,
                NOW
        );
    }

    private DoctorResponse doctor(String name) {
        return new DoctorResponse(
                UUID.randomUUID(),
                name,
                "Nha khoa tổng quát",
                "AI11-TEST",
                null,
                new BigDecimal("4.8"),
                8,
                DoctorStatus.ACTIVE,
                NOW,
                NOW
        );
    }

    private KnowledgeRetrievalResult knowledge() {
        return new KnowledgeRetrievalResult(
                UUID.randomUUID(),
                "Đau răng có thể liên quan đến nhiều nguyên nhân và cần được nha sĩ kiểm tra.",
                "TOOTH_DECAY",
                "vi",
                List.of("đau răng"),
                List.of("Khám răng tổng quát"),
                List.of("Nha khoa tổng quát"),
                new KnowledgeSourceTrace(
                        "nidcr-dental-knowledge-vi",
                        "1.0.0",
                        "NIDCR-VI-001",
                        "Sâu răng",
                        "https://www.nidcr.nih.gov/health-info/tooth-decay"
                ),
                0.92
        );
    }

    private <T> PageResponse<T> page(List<T> content) {
        return new PageResponse<>(content, 0, 50, content.size(), 1, true, true, false, false);
    }
}
