package com.dentalclinic.ai.conversation.service;

import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorManagementService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeterministicConversationUnderstandingService {

    private static final Pattern PRICE_QUESTION_PATTERN = Pattern.compile(
            "\\b(?:gia|bang gia|muc gia|gia tien|bao nhieu|bao tien|chi phi|"
                    + "phi dich vu|het bao nhieu|het may tien)\\b"
    );
    private static final Pattern SERVICE_CATALOG_PATTERN = Pattern.compile(
            "\\b(?:dich vu nao|co dich vu gi|co nhung dich vu gi|nhung dich vu|"
                    + "cac dich vu|danh sach dich vu|xem dich vu|bang gia dich vu|"
                    + "dich vu ben minh)\\b"
    );
    private static final Pattern AVAILABILITY_PATTERN = Pattern.compile(
            "\\b(?:con lich|lich trong|con trong|trong lich|trong ca|ca trong|co lich|"
                    + "co ca|con ca|ranh|lich lam viec|xem lich)\\b"
    );
    private static final Pattern DOCTOR_BY_SERVICE_PATTERN = Pattern.compile(
            "\\b(?:bac si nao|do bac si nao|ai kham|ai lam|ai thuc hien|"
                    + "bac si thuc hien|bac si phu hop)\\b"
    );
    private static final Pattern DOCTOR_RECOMMENDATION_PATTERN = Pattern.compile(
            "\\b(?:bac si nao|nen gap bac si|nen kham bac si|bac si phu hop|"
                    + "bac si nao kham|bac si nao dieu tri)\\b"
    );
    private static final Pattern SERVICE_RECOMMENDATION_PATTERN = Pattern.compile(
            "\\b(?:dich vu nao phu hop|nen dung dich vu|nen lam dich vu|"
                    + "can lam dich vu|nen kham dich vu)\\b"
    );
    private static final Pattern APPOINTMENT_GUIDANCE_PATTERN = Pattern.compile(
            "\\b(?:cach dat lich|huong dan dat lich|dat lich nhu the nao|"
                    + "lam sao de dat lich|muon dat lich)\\b"
    );
    private static final Pattern CLINIC_INFORMATION_PATTERN = Pattern.compile(
            "\\b(?:thong tin phong kham|phong kham ho tro gi|nha khoa ho tro gi|"
                    + "phong kham co gi)\\b"
    );
    private static final Pattern EXPLICIT_DATE_PATTERN = Pattern.compile(
            "\\b(?:ngay\\s+)?(?:\\d{4}-\\d{1,2}-\\d{1,2}|"
                    + "\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{4})?)\\b"
    );
    private static final Pattern UPCOMING_DAYS_PATTERN = Pattern.compile(
            "\\b(?:trong\\s+)?\\d{1,2}\\s+ngay\\s+(?:toi|sap toi)\\b"
    );
    private static final List<String> RELATIVE_DATE_EXPRESSIONS = List.of(
            "cuoi tuan sau",
            "cuoi tuan nay",
            "ngay hom nay",
            "ngay mai",
            "ngay kia",
            "hom nay",
            "tuan sau",
            "tuan nay",
            "thang sau",
            "thang nay",
            "mai"
    );
    private static final List<String> WEEKDAY_EXPRESSIONS = List.of(
            "thu hai",
            "thu ba",
            "thu tu",
            "thu nam",
            "thu sau",
            "thu bay",
            "chu nhat"
    );
    private static final Pattern COMPLEX_OPERATION_PATTERN = Pattern.compile(
            "\\b(?:bac si|lich|slot|ca kham|ngay mai|hom nay|tuan sau)\\b"
    );
    private static final List<SymptomRule> SYMPTOM_RULES = List.of(
            new SymptomRule("dau rang", "đau răng"),
            new SymptomRule("nhuc rang", "nhức răng"),
            new SymptomRule("e buot", "ê buốt răng"),
            new SymptomRule("sau rang", "sâu răng"),
            new SymptomRule("sung loi", "sưng lợi"),
            new SymptomRule("sung nuou", "sưng nướu"),
            new SymptomRule("viem loi", "viêm lợi"),
            new SymptomRule("viem nuou", "viêm nướu"),
            new SymptomRule("chay mau", "chảy máu nướu"),
            new SymptomRule("hoi mieng", "hôi miệng"),
            new SymptomRule("kho mieng", "khô miệng"),
            new SymptomRule("rang lung lay", "răng lung lay"),
            new SymptomRule("loet mieng", "loét miệng"),
            new SymptomRule("dau ham", "đau hàm"),
            new SymptomRule("sung mat", "sưng mặt"),
            new SymptomRule("dau vung cam", "đau vùng cằm")
    );

    private final DentalServiceManagementService dentalServiceManagementService;
    private final DoctorManagementService doctorManagementService;
    private final VietnameseRelativeDateResolver dateResolver;

    public DeterministicConversationUnderstandingService(
            DentalServiceManagementService dentalServiceManagementService,
            DoctorManagementService doctorManagementService,
            VietnameseRelativeDateResolver dateResolver
    ) {
        this.dentalServiceManagementService = dentalServiceManagementService;
        this.doctorManagementService = doctorManagementService;
        this.dateResolver = dateResolver;
    }

    public Optional<ChatUnderstanding> analyze(String message) {
        String normalizedMessage = normalize(message);
        List<String> symptoms = detectSymptoms(normalizedMessage);
        boolean priceQuestion = PRICE_QUESTION_PATTERN.matcher(normalizedMessage).find();
        boolean serviceCatalogQuestion = SERVICE_CATALOG_PATTERN.matcher(normalizedMessage).find();
        boolean availabilityQuestion = AVAILABILITY_PATTERN.matcher(normalizedMessage).find();
        boolean doctorByServiceQuestion = DOCTOR_BY_SERVICE_PATTERN.matcher(normalizedMessage).find();
        boolean doctorRecommendation = !symptoms.isEmpty()
                && DOCTOR_RECOMMENDATION_PATTERN.matcher(normalizedMessage).find();
        boolean serviceRecommendation = !symptoms.isEmpty()
                && SERVICE_RECOMMENDATION_PATTERN.matcher(normalizedMessage).find();

        if (availabilityQuestion && hasMultipleExplicitDates(normalizedMessage)) {
            return Optional.empty();
        }

        Optional<DentalServiceResponse> selectedService =
                priceQuestion || availabilityQuestion || doctorByServiceQuestion
                        ? findActiveService(normalizedMessage)
                        : Optional.empty();
        Optional<DoctorResponse> selectedDoctor = availabilityQuestion
                ? findActiveDoctor(normalizedMessage)
                : Optional.empty();
        Optional<ChatDateRange> dateRange = availabilityQuestion
                ? resolveDateRange(normalizedMessage)
                : Optional.empty();

        LinkedHashSet<ChatIntent> intents = new LinkedHashSet<>();
        if (priceQuestion || serviceCatalogQuestion) {
            intents.add(ChatIntent.SERVICE_CATALOG);
        }
        if (doctorByServiceQuestion && selectedService.isPresent()) {
            intents.add(ChatIntent.SERVICE_AVAILABLE_DOCTORS);
        }
        if (availabilityQuestion) {
            if (selectedDoctor.isPresent()) {
                intents.add(ChatIntent.DOCTOR_AVAILABLE_SLOTS);
            } else if (selectedService.isPresent() || isGenericAvailabilityQuestion(normalizedMessage)) {
                intents.add(ChatIntent.AVAILABLE_SLOTS);
            }
        }
        if (!symptoms.isEmpty()) {
            intents.add(ChatIntent.SYMPTOM_CONSULTATION);
            if (doctorRecommendation) {
                intents.add(ChatIntent.DOCTOR_RECOMMENDATION);
            }
            if (serviceRecommendation) {
                intents.add(ChatIntent.SERVICE_RECOMMENDATION);
            }
        }
        if (APPOINTMENT_GUIDANCE_PATTERN.matcher(normalizedMessage).find()) {
            intents.add(ChatIntent.APPOINTMENT_GUIDANCE);
        }
        if (CLINIC_INFORMATION_PATTERN.matcher(normalizedMessage).find()) {
            intents.add(ChatIntent.CLINIC_INFORMATION);
        }

        if (intents.isEmpty()) {
            return Optional.empty();
        }
        if (requiresModelForUnresolvedOperation(normalizedMessage, intents)) {
            return Optional.empty();
        }
        return Optional.of(understanding(
                List.copyOf(intents),
                symptoms,
                selectedDoctor.orElse(null),
                selectedService.orElse(null),
                dateRange.orElse(null),
                availabilityQuestion ? sessionPreference(normalizedMessage) : null
        ));
    }

    private boolean requiresModelForUnresolvedOperation(
            String normalizedMessage,
            LinkedHashSet<ChatIntent> intents
    ) {
        return COMPLEX_OPERATION_PATTERN.matcher(normalizedMessage).find()
                && intents.equals(new LinkedHashSet<>(List.of(ChatIntent.SYMPTOM_CONSULTATION)));
    }

    private Optional<DentalServiceResponse> findActiveService(String normalizedMessage) {
        return dentalServiceManagementService.getActiveServices().stream()
                .filter(service -> containsServiceName(normalizedMessage, service))
                .max(Comparator.comparingInt(service -> normalize(service.name()).length()));
    }

    private Optional<DoctorResponse> findActiveDoctor(String normalizedMessage) {
        if (!containsPhrase(normalizedMessage, "bac si")) {
            return Optional.empty();
        }
        return doctorManagementService.getDoctors(
                        null,
                        null,
                        DoctorStatus.ACTIVE,
                        0,
                        100
                ).content().stream()
                .filter(candidate -> containsDoctorName(normalizedMessage, candidate))
                .max(Comparator.comparingInt(candidate -> normalize(candidate.fullName()).length()));
    }

    private ChatUnderstanding understanding(
            List<ChatIntent> intents,
            List<String> symptoms,
            DoctorResponse doctor,
            DentalServiceResponse service,
            ChatDateRange dateRange,
            ChatSessionPreference sessionPreference
    ) {
        ChatEntities entities = new ChatEntities(
                symptoms,
                doctor == null ? null : doctor.fullName(),
                doctor == null ? null : doctor.id(),
                service == null ? null : service.name(),
                service == null ? null : service.id(),
                dateRange != null && dateRange.isSingleDay() ? dateRange.from() : null,
                dateRange != null && !dateRange.isSingleDay() ? dateRange.from() : null,
                dateRange != null && !dateRange.isSingleDay() ? dateRange.to() : null,
                sessionPreference,
                null
        );
        return new ChatUnderstanding(intents, entities, ChatFollowUpType.NONE);
    }

    private boolean containsDoctorName(String normalizedMessage, DoctorResponse doctor) {
        String normalizedName = normalize(doctor.fullName());
        return !normalizedName.isBlank() && containsPhrase(normalizedMessage, normalizedName);
    }

    private Optional<String> extractDateExpression(String normalizedMessage) {
        Matcher explicitDate = EXPLICIT_DATE_PATTERN.matcher(normalizedMessage);
        if (explicitDate.find()) {
            return Optional.of(explicitDate.group());
        }
        Matcher upcomingDays = UPCOMING_DAYS_PATTERN.matcher(normalizedMessage);
        if (upcomingDays.find()) {
            return Optional.of(upcomingDays.group());
        }
        for (String weekday : WEEKDAY_EXPRESSIONS) {
            if (containsPhrase(normalizedMessage, weekday)) {
                if (containsPhrase(normalizedMessage, "tuan sau")) {
                    return Optional.of(weekday + " tuan sau");
                }
                if (containsPhrase(normalizedMessage, "tuan nay")) {
                    return Optional.of(weekday + " tuan nay");
                }
                return Optional.of(weekday);
            }
        }
        for (String expression : RELATIVE_DATE_EXPRESSIONS) {
            if (containsPhrase(normalizedMessage, expression)) {
                return Optional.of(expression);
            }
        }
        return Optional.empty();
    }

    private Optional<ChatDateRange> resolveDateRange(String normalizedMessage) {
        return extractDateExpression(normalizedMessage).flatMap(dateResolver::resolve);
    }

    private boolean hasMultipleExplicitDates(String normalizedMessage) {
        Matcher matcher = EXPLICIT_DATE_PATTERN.matcher(normalizedMessage);
        int matches = 0;
        while (matcher.find()) {
            matches++;
            if (matches > 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isGenericAvailabilityQuestion(String normalizedMessage) {
        return !containsPhrase(normalizedMessage, "bac si")
                || containsPhrase(normalizedMessage, "bac si nao")
                || containsPhrase(normalizedMessage, "lich nao")
                || containsPhrase(normalizedMessage, "ca nao");
    }

    private ChatSessionPreference sessionPreference(String normalizedMessage) {
        if (containsPhrase(normalizedMessage, "buoi sang")
                || containsPhrase(normalizedMessage, "sang mai")
                || containsPhrase(normalizedMessage, "sang nay")
                || containsPhrase(normalizedMessage, "sang")) {
            return ChatSessionPreference.MORNING;
        }
        if (containsPhrase(normalizedMessage, "buoi chieu")
                || containsPhrase(normalizedMessage, "chieu mai")
                || containsPhrase(normalizedMessage, "chieu nay")
                || containsPhrase(normalizedMessage, "chieu")) {
            return ChatSessionPreference.AFTERNOON;
        }
        return null;
    }

    private boolean containsServiceName(
            String normalizedMessage,
            DentalServiceResponse service
    ) {
        String normalizedName = normalize(service.name());
        return !normalizedName.isBlank() && normalizedMessage.contains(normalizedName);
    }

    private List<String> detectSymptoms(String normalizedMessage) {
        LinkedHashSet<String> symptoms = new LinkedHashSet<>();
        for (SymptomRule rule : SYMPTOM_RULES) {
            if (containsPhrase(normalizedMessage, rule.normalizedPhrase())) {
                symptoms.add(rule.canonicalValue());
            }
        }
        return List.copyOf(symptoms);
    }

    private boolean containsPhrase(String normalizedMessage, String phrase) {
        return (" " + normalizedMessage + " ").contains(" " + phrase + " ");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9/\\-]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private record SymptomRule(String normalizedPhrase, String canonicalValue) {
    }
}
