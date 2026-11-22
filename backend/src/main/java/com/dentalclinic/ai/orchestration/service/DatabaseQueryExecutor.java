package com.dentalclinic.ai.orchestration.service;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.orchestration.model.DatabaseGroundingContext;
import com.dentalclinic.ai.orchestration.model.DatabaseQueryResult;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendation;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendationResult;
import com.dentalclinic.ai.recommendation.service.ServiceDoctorRecommendationService;
import com.dentalclinic.ai.slot.model.ChatSlotLookupRequest;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.ai.slot.model.SlotLookupTarget;
import com.dentalclinic.ai.slot.service.AiSlotLookupService;
import com.dentalclinic.ai.telemetry.ChatTelemetry;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.DoctorServiceMatch;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorManagementService;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DatabaseQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryExecutor.class);
    private static final int MAX_REFERENCE_SEARCH_RESULTS = 50;

    private final ServiceDoctorRecommendationService recommendationService;
    private final DentalServiceManagementService dentalServiceManagementService;
    private final DoctorManagementService doctorManagementService;
    private final DoctorServiceMappingService doctorServiceMappingService;
    private final AiSlotLookupService slotLookupService;

    public DatabaseQueryExecutor(
            ServiceDoctorRecommendationService recommendationService,
            DentalServiceManagementService dentalServiceManagementService,
            DoctorManagementService doctorManagementService,
            DoctorServiceMappingService doctorServiceMappingService,
            AiSlotLookupService slotLookupService
    ) {
        this.recommendationService = recommendationService;
        this.dentalServiceManagementService = dentalServiceManagementService;
        this.doctorManagementService = doctorManagementService;
        this.doctorServiceMappingService = doctorServiceMappingService;
        this.slotLookupService = slotLookupService;
    }

    public DatabaseGroundingContext prepare(ChatContextResolution resolution) {
        ChatSemanticContext originalContext = resolution.context();
        boolean alternativeDoctor = resolution.followUpType()
                == ChatFollowUpType.ALTERNATIVE_DOCTOR;
        boolean alternativeService = resolution.followUpType()
                == ChatFollowUpType.ALTERNATIVE_SERVICE;
        UUID excludedDoctorId = alternativeDoctor && originalContext.selectedDoctor() != null
                ? originalContext.selectedDoctor().id()
                : null;
        UUID excludedServiceId = alternativeService && originalContext.selectedService() != null
                ? originalContext.selectedService().id()
                : null;
        Optional<DentalServiceResponse> selectedService = alternativeService
                ? Optional.empty()
                : resolveService(originalContext.selectedService());
        Optional<DoctorResponse> selectedDoctor = alternativeDoctor
                ? Optional.empty()
                : resolveDoctor(originalContext.selectedDoctor());
        ChatSemanticContext context = enrichContext(
                originalContext,
                selectedService,
                selectedDoctor,
                alternativeService,
                alternativeDoctor
        );
        return new DatabaseGroundingContext(
                context,
                selectedService.orElse(null),
                selectedDoctor.orElse(null),
                excludedDoctorId,
                excludedServiceId
        );
    }

    public DatabaseQueryResult execute(
            DatabaseGroundingContext prepared,
            List<KnowledgeRetrievalResult> evidence
    ) {
        long startedNanos = System.nanoTime();
        ChatSemanticContext context = prepared.context();
        ServiceDoctorRecommendationResult recommendation = shouldRecommend(context)
                && !evidence.isEmpty()
                ? recommendationService.recommendFromEvidence(evidence)
                : new ServiceDoctorRecommendationResult(List.of(), List.of(), List.of());

        LinkedHashMap<UUID, DentalServiceResponse> services = new LinkedHashMap<>();
        if (context.lastIntents().contains(ChatIntent.SERVICE_CATALOG)) {
            if (prepared.selectedService() != null) {
                DentalServiceResponse service = prepared.selectedService();
                services.put(service.id(), service);
            } else {
                dentalServiceManagementService.getActiveServices()
                        .forEach(service -> services.put(service.id(), service));
            }
        } else if (prepared.selectedService() != null) {
            services.put(prepared.selectedService().id(), prepared.selectedService());
        }
        for (ServiceDoctorRecommendation item : recommendation.recommendations()) {
            if (!item.service().id().equals(prepared.excludedServiceId())) {
                services.putIfAbsent(item.service().id(), item.service());
            }
        }

        List<DoctorServiceMatch> mappings = needsDoctors(context)
                ? doctorServiceMappingService
                        .getActiveDoctorMappingsByServiceIds(List.copyOf(services.keySet()))
                : List.of();
        Map<UUID, List<DoctorResponse>> doctorsByService = groupDoctorsByService(
                mappings,
                prepared.excludedDoctorId()
        );
        LinkedHashMap<UUID, DoctorResponse> doctors = new LinkedHashMap<>();
        if (needsDoctors(context)) {
            if (prepared.selectedDoctor() != null) {
                doctors.put(prepared.selectedDoctor().id(), prepared.selectedDoctor());
            }
            doctorsByService.values().forEach(group -> group.forEach(doctor ->
                    doctors.putIfAbsent(doctor.id(), doctor)
            ));
        }

        List<ChatSlotSuggestion> slots = needsSlots(context) && context.dateRange() != null
                ? lookupSlots(prepared, doctorsByService)
                : List.of();
        DatabaseQueryResult result = new DatabaseQueryResult(
                List.copyOf(services.values()),
                List.copyOf(doctors.values()),
                slots
        );
        log.info(
                "chat_database_query_completed requestId={} durationMs={} serviceCount={} doctorCount={} slotCount={}",
                ChatTelemetry.currentRequestId(),
                ChatTelemetry.elapsedMillis(startedNanos),
                result.services().size(),
                result.doctors().size(),
                result.slots().size()
        );
        return result;
    }

    private Optional<DentalServiceResponse> resolveService(ChatEntityReference reference) {
        if (reference == null) {
            return Optional.empty();
        }
        try {
            if (reference.id() != null) {
                DentalServiceResponse service = dentalServiceManagementService.getService(reference.id());
                return service.active() ? Optional.of(service) : Optional.empty();
            }
            PageResponse<DentalServiceResponse> page = dentalServiceManagementService.getServices(
                    reference.name(),
                    true,
                    0,
                    MAX_REFERENCE_SEARCH_RESULTS
            );
            return selectByName(page.content(), reference.name(), DentalServiceResponse::name);
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    private Optional<DoctorResponse> resolveDoctor(ChatEntityReference reference) {
        if (reference == null) {
            return Optional.empty();
        }
        try {
            if (reference.id() != null) {
                DoctorResponse doctor = doctorManagementService.getDoctor(reference.id());
                return doctor.workingStatus() == DoctorStatus.ACTIVE
                        ? Optional.of(doctor)
                        : Optional.empty();
            }
            PageResponse<DoctorResponse> page = doctorManagementService.getDoctors(
                    reference.name(),
                    null,
                    DoctorStatus.ACTIVE,
                    0,
                    MAX_REFERENCE_SEARCH_RESULTS
            );
            return selectByName(page.content(), reference.name(), DoctorResponse::fullName);
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> selectByName(
            List<T> candidates,
            String requestedName,
            java.util.function.Function<T, String> nameExtractor
    ) {
        String normalizedName = normalizeName(requestedName);
        List<T> exactMatches = candidates.stream()
                .filter(candidate -> normalizeName(nameExtractor.apply(candidate)).equals(normalizedName))
                .toList();
        if (exactMatches.size() == 1) {
            return Optional.of(exactMatches.getFirst());
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private ChatSemanticContext enrichContext(
            ChatSemanticContext context,
            Optional<DentalServiceResponse> service,
            Optional<DoctorResponse> doctor,
            boolean clearService,
            boolean clearDoctor
    ) {
        ChatEntityReference serviceReference = clearService
                ? null
                : service.<ChatEntityReference>map(value ->
                        new ChatEntityReference(value.id(), value.name())
                ).orElse(context.selectedService());
        ChatEntityReference doctorReference = clearDoctor
                ? null
                : doctor.<ChatEntityReference>map(value ->
                        new ChatEntityReference(value.id(), value.fullName())
                ).orElse(context.selectedDoctor());
        return new ChatSemanticContext(
                context.lastIntents(),
                context.symptoms(),
                doctorReference,
                serviceReference,
                context.dateRange(),
                context.sessionPreference(),
                context.timePreference()
        );
    }

    private boolean shouldRecommend(ChatSemanticContext context) {
        Set<ChatIntent> intents = Set.copyOf(context.lastIntents());
        return intents.contains(ChatIntent.SERVICE_RECOMMENDATION)
                || intents.contains(ChatIntent.DOCTOR_RECOMMENDATION);
    }

    private boolean needsDoctors(ChatSemanticContext context) {
        Set<ChatIntent> intents = Set.copyOf(context.lastIntents());
        return intents.contains(ChatIntent.DOCTOR_RECOMMENDATION)
                || intents.contains(ChatIntent.SERVICE_AVAILABLE_DOCTORS)
                || needsSlots(context);
    }

    private boolean needsSlots(ChatSemanticContext context) {
        Set<ChatIntent> intents = Set.copyOf(context.lastIntents());
        return intents.contains(ChatIntent.DOCTOR_AVAILABLE_SLOTS)
                || intents.contains(ChatIntent.AVAILABLE_SLOTS);
    }

    private Map<UUID, List<DoctorResponse>> groupDoctorsByService(
            List<DoctorServiceMatch> mappings,
            UUID excludedDoctorId
    ) {
        LinkedHashMap<UUID, LinkedHashMap<UUID, DoctorResponse>> grouped = new LinkedHashMap<>();
        for (DoctorServiceMatch mapping : mappings) {
            if (!mapping.doctor().id().equals(excludedDoctorId)) {
                grouped.computeIfAbsent(mapping.serviceId(), ignored -> new LinkedHashMap<>())
                        .putIfAbsent(mapping.doctor().id(), mapping.doctor());
            }
        }
        LinkedHashMap<UUID, List<DoctorResponse>> result = new LinkedHashMap<>();
        grouped.forEach((serviceId, values) ->
                result.put(serviceId, List.copyOf(values.values()))
        );
        return result;
    }

    private List<ChatSlotSuggestion> lookupSlots(
            DatabaseGroundingContext prepared,
            Map<UUID, List<DoctorResponse>> doctorsByService
    ) {
        LinkedHashSet<SlotLookupTarget> targets = new LinkedHashSet<>();
        if (prepared.selectedDoctor() != null) {
            DoctorResponse doctor = prepared.selectedDoctor();
            if (prepared.selectedService() == null) {
                targets.add(new SlotLookupTarget(doctor.id(), null));
            } else {
                DentalServiceResponse service = prepared.selectedService();
                boolean assigned = doctorsByService.getOrDefault(service.id(), List.of()).stream()
                        .anyMatch(candidate -> candidate.id().equals(doctor.id()));
                if (assigned) {
                    targets.add(new SlotLookupTarget(doctor.id(), service.id()));
                }
            }
        } else {
            doctorsByService.forEach((serviceId, doctors) -> doctors.forEach(doctor ->
                    targets.add(new SlotLookupTarget(doctor.id(), serviceId))
            ));
        }
        if (targets.isEmpty()) {
            return List.of();
        }
        ChatSemanticContext context = prepared.context();
        return slotLookupService.lookup(new ChatSlotLookupRequest(
                        List.copyOf(targets),
                        context.dateRange().from(),
                        context.dateRange().to(),
                        context.sessionPreference()
                )).availability().stream()
                .flatMap(availability -> availability.slots().stream())
                .toList();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
