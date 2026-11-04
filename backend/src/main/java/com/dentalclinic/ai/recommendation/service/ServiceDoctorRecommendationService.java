package com.dentalclinic.ai.recommendation.service;

import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendation;
import com.dentalclinic.ai.recommendation.model.ServiceDoctorRecommendationResult;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.DoctorServiceMatch;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ServiceDoctorRecommendationService {

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final DentalServiceManagementService dentalServiceManagementService;
    private final DoctorServiceMappingService doctorServiceMappingService;

    public ServiceDoctorRecommendationService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            DentalServiceManagementService dentalServiceManagementService,
            DoctorServiceMappingService doctorServiceMappingService
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.dentalServiceManagementService = dentalServiceManagementService;
        this.doctorServiceMappingService = doctorServiceMappingService;
    }

    public ServiceDoctorRecommendationResult recommend(String query) {
        List<KnowledgeRetrievalResult> evidence = knowledgeRetrievalService.retrieve(
                KnowledgeRetrievalRequest.of(query)
        );
        return recommendFromEvidence(evidence);
    }

    public ServiceDoctorRecommendationResult recommendFromEvidence(
            List<KnowledgeRetrievalResult> evidence
    ) {
        List<String> categoryHints = collectCategoryHints(evidence);
        List<KnowledgeSourceTrace> sources = collectSources(evidence);
        if (categoryHints.isEmpty()) {
            return new ServiceDoctorRecommendationResult(List.of(), List.of(), sources);
        }

        List<DentalServiceResponse> verifiedServices = dentalServiceManagementService
                .findActiveServicesMatchingCategoryHints(categoryHints);
        if (verifiedServices.isEmpty()) {
            return new ServiceDoctorRecommendationResult(categoryHints, List.of(), sources);
        }

        List<UUID> serviceIds = verifiedServices.stream()
                .map(DentalServiceResponse::id)
                .toList();
        Map<UUID, List<DoctorResponse>> doctorsByService = groupDoctorsByService(
                doctorServiceMappingService.getActiveDoctorMappingsByServiceIds(serviceIds)
        );
        List<ServiceDoctorRecommendation> recommendations = verifiedServices.stream()
                .filter(service -> doctorsByService.containsKey(service.id()))
                .map(service -> new ServiceDoctorRecommendation(
                        service,
                        doctorsByService.get(service.id())
                ))
                .toList();
        return new ServiceDoctorRecommendationResult(
                categoryHints,
                recommendations,
                sources
        );
    }

    private List<String> collectCategoryHints(List<KnowledgeRetrievalResult> evidence) {
        LinkedHashMap<String, String> uniqueHints = new LinkedHashMap<>();
        for (KnowledgeRetrievalResult result : evidence) {
            for (String hint : result.serviceCategoryHints()) {
                if (hint != null && !hint.isBlank()) {
                    String normalized = hint.strip();
                    uniqueHints.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
                }
            }
        }
        return List.copyOf(uniqueHints.values());
    }

    private List<KnowledgeSourceTrace> collectSources(List<KnowledgeRetrievalResult> evidence) {
        LinkedHashSet<KnowledgeSourceTrace> sources = new LinkedHashSet<>();
        evidence.stream()
                .map(KnowledgeRetrievalResult::source)
                .filter(java.util.Objects::nonNull)
                .forEach(sources::add);
        return List.copyOf(sources);
    }

    private Map<UUID, List<DoctorResponse>> groupDoctorsByService(
            List<DoctorServiceMatch> matches
    ) {
        LinkedHashMap<UUID, List<DoctorResponse>> grouped = new LinkedHashMap<>();
        for (DoctorServiceMatch match : matches) {
            grouped.computeIfAbsent(match.serviceId(), ignored -> new ArrayList<>())
                    .add(match.doctor());
        }
        LinkedHashMap<UUID, List<DoctorResponse>> immutable = new LinkedHashMap<>();
        grouped.forEach((serviceId, doctors) -> immutable.put(serviceId, List.copyOf(doctors)));
        return Map.copyOf(immutable);
    }
}
