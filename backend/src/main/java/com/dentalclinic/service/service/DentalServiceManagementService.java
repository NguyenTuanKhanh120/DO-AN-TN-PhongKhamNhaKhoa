package com.dentalclinic.service.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.service.dto.CreateDentalServiceRequest;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.dto.UpdateDentalServiceRequest;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DentalServiceManagementService {

    private static final char LIKE_ESCAPE = '\\';
    private static final double RECOMMENDATION_MATCH_THRESHOLD = 0.5;
    private static final int MAX_RECOMMENDATION_HINTS = 50;
    private static final Pattern CLEANING_TERMS = Pattern.compile(
            "\\b(?:ve sinh|lam sach|cao voi)\\b"
    );
    private static final Set<String> GENERIC_RECOMMENDATION_TERMS = Set.of(
            "cham", "chuyen", "co", "danh", "dieu", "dich", "gia", "kham",
            "khoa", "mieng", "nghiep", "nha", "nguy", "rang", "soc", "suc",
            "tri", "tu", "van", "va", "vu"
    );

    private final DentalServiceRepository dentalServiceRepository;

    public DentalServiceManagementService(DentalServiceRepository dentalServiceRepository) {
        this.dentalServiceRepository = dentalServiceRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DentalServiceResponse> getServices(
            String search,
            boolean active,
            int page,
            int size
    ) {
        Specification<DentalService> specification = buildSpecification(search, active);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        );
        Page<DentalServiceResponse> services = dentalServiceRepository.findAll(specification, pageRequest)
                .map(this::toResponse);
        return PageResponse.from(services);
    }

    @Transactional(readOnly = true)
    public DentalServiceResponse getService(UUID serviceId) {
        return toResponse(findService(serviceId));
    }

    @Transactional(readOnly = true)
    public List<DentalServiceResponse> getActiveServices() {
        return dentalServiceRepository.findAllByActiveTrueOrderByNameAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DentalServiceResponse> findActiveServicesMatchingCategoryHints(
            List<String> categoryHints
    ) {
        List<String> normalizedHints = normalizeRecommendationHints(categoryHints);
        if (normalizedHints.isEmpty()) {
            return List.of();
        }
        return dentalServiceRepository.findAllByActiveTrueOrderByNameAscIdAsc().stream()
                .map(service -> new ServiceRecommendationMatch(
                        service,
                        bestRecommendationScore(service, normalizedHints)
                ))
                .filter(match -> match.score() >= RECOMMENDATION_MATCH_THRESHOLD)
                .sorted(Comparator
                        .comparingDouble(ServiceRecommendationMatch::score)
                        .reversed()
                        .thenComparing(
                                match -> match.service().getName(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(match -> match.service().getId()))
                .map(ServiceRecommendationMatch::service)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DentalServiceResponse createService(CreateDentalServiceRequest request) {
        String name = normalizeName(request.name());
        if (dentalServiceRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.SERVICE_NAME_ALREADY_EXISTS);
        }

        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription(normalizeNullable(request.description()));
        service.setPrice(request.price());
        service.setImageUrl(normalizeNullable(request.imageUrl()));
        service.setActive(true);

        try {
            dentalServiceRepository.saveAndFlush(service);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SERVICE_NAME_ALREADY_EXISTS);
        }
        return toResponse(service);
    }

    @Transactional
    public DentalServiceResponse updateService(UUID serviceId, UpdateDentalServiceRequest request) {
        DentalService service = findService(serviceId);
        String name = normalizeName(request.name());
        if (dentalServiceRepository.existsByNameAndIdNot(name, serviceId)) {
            throw new BusinessException(ErrorCode.SERVICE_NAME_ALREADY_EXISTS);
        }

        service.setName(name);
        service.setDescription(normalizeNullable(request.description()));
        service.setPrice(request.price());
        service.setImageUrl(normalizeNullable(request.imageUrl()));

        try {
            dentalServiceRepository.saveAndFlush(service);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SERVICE_NAME_ALREADY_EXISTS);
        }
        return toResponse(service);
    }

    @Transactional
    public DentalServiceResponse updateStatus(UUID serviceId, boolean active) {
        DentalService service = findService(serviceId);
        service.setActive(active);
        dentalServiceRepository.saveAndFlush(service);
        return toResponse(service);
    }

    private Specification<DentalService> buildSpecification(String search, boolean active) {
        Specification<DentalService> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("active"), active);
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch == null) {
            return specification;
        }

        String pattern = "%" + escapeLikePattern(normalizedSearch) + "%";
        return specification.and((root, query, criteriaBuilder) -> {
            Expression<String> name = criteriaBuilder.lower(root.get("name"));
            Expression<String> description = criteriaBuilder.lower(root.get("description"));
            Predicate nameMatches = criteriaBuilder.like(name, pattern, LIKE_ESCAPE);
            Predicate descriptionMatches = criteriaBuilder.like(description, pattern, LIKE_ESCAPE);
            return criteriaBuilder.or(nameMatches, descriptionMatches);
        });
    }

    private DentalService findService(UUID serviceId) {
        return dentalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dịch vụ nha khoa"));
    }

    private DentalServiceResponse toResponse(DentalService service) {
        return new DentalServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getImageUrl(),
                service.isActive(),
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private List<String> normalizeRecommendationHints(List<String> categoryHints) {
        if (categoryHints == null || categoryHints.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String hint : categoryHints) {
            String value = normalizeRecommendationText(hint);
            if (!value.isBlank()) {
                normalized.add(value);
            }
            if (normalized.size() == MAX_RECOMMENDATION_HINTS) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private double bestRecommendationScore(
            DentalService service,
            List<String> normalizedHints
    ) {
        Set<String> serviceTerms = significantTerms(
                normalizeRecommendationText(
                        service.getName() + " " + (service.getDescription() == null
                                ? ""
                                : service.getDescription())
                )
        );
        return normalizedHints.stream()
                .map(this::significantTerms)
                .mapToDouble(hintTerms -> recommendationScore(hintTerms, serviceTerms))
                .max()
                .orElse(0.0);
    }

    private double recommendationScore(Set<String> hintTerms, Set<String> serviceTerms) {
        if (hintTerms.isEmpty() || serviceTerms.isEmpty()) {
            return 0.0;
        }
        long sharedTerms = hintTerms.stream().filter(serviceTerms::contains).count();
        if (sharedTerms == 0) {
            return 0.0;
        }
        return (double) sharedTerms / Math.min(hintTerms.size(), serviceTerms.size());
    }

    private Set<String> significantTerms(String value) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : value.split(" ")) {
            if (!term.isBlank() && !GENERIC_RECOMMENDATION_TERMS.contains(term)) {
                terms.add(term);
            }
        }
        return Set.copyOf(terms);
    }

    private String normalizeRecommendationText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
        normalized = CLEANING_TERMS.matcher(normalized).replaceAll(" lam_sach ");
        return normalized
                .replaceAll("[^a-z0-9_]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record ServiceRecommendationMatch(
            DentalService service,
            double score
    ) {
    }
}
