package com.dentalclinic.ai.conversation.service;

import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FallbackConversationUnderstandingService {

    private static final Pattern AVAILABILITY_PATTERN = Pattern.compile(
            "\\b(?:lich|ca kham|ca trong|con ca|con trong|ranh|slot)\\b"
    );
    private static final Pattern SERVICE_CATALOG_PATTERN = Pattern.compile(
            "\\b(?:gia|bao nhieu|bao tien|chi phi|dich vu|bang gia)\\b"
    );
    private static final Pattern DOCTOR_RECOMMENDATION_PATTERN = Pattern.compile(
            "\\b(?:bac si nao|bac si phu hop|goi y bac si|nen gap bac si|"
                    + "nen kham bac si)\\b"
    );
    private static final Pattern SERVICE_RECOMMENDATION_PATTERN = Pattern.compile(
            "\\b(?:dich vu nao phu hop|goi y dich vu|nen lam dich vu|"
                    + "can lam dich vu)\\b"
    );
    private static final Pattern APPOINTMENT_GUIDANCE_PATTERN = Pattern.compile(
            "\\b(?:dat lich|cach dat lich|huong dan dat lich)\\b"
    );
    private static final Pattern CLINIC_INFORMATION_PATTERN = Pattern.compile(
            "\\b(?:phong kham|nha khoa an tam)\\b"
    );
    private static final Pattern DENTAL_VOCABULARY_PATTERN = Pattern.compile(
            "\\b(?:rang|nuou|loi|ham|mieng|nha khoa|dau|nhuc|sung|e buot|"
                    + "chay mau|hoi mieng|kho mieng)\\b"
    );
    private static final Pattern FOLLOW_UP_REMAINDER_PATTERN = Pattern.compile(
            "^(?:(?:vay|the|con|co|tim|chon|goi|y|cho|toi|giup|minh|biet|nua|khong|thi|sao|"
                    + "ngay|buoi|sang|chieu|mai|hom|nay|kia|tuan|sau|cuoi|thu|hai|ba|tu|nam|bay|"
                    + "chu|nhat|thang|sap|trong|vao|luc|lich|ca|kham|nao|duoc|nhe|a|"
                    + "\\d+(?:[/-]\\d+)*)(?:\\s+|$))*$"
    );
    private static final Pattern DOCTOR_FOLLOW_UP_PATTERN = Pattern.compile(
            "^(?:(?:vay|the|con) )?(?:bac si nao(?: phu hop| kham| dieu tri)?|bac si phu hop|"
                    + "nen gap bac si(?: nao)?|nen kham bac si(?: nao)?|goi y bac si)"
                    + "(?: (?:voi|cho|toi|truong|hop|nay|do|giup|minh|nhe|a|khong|phu|hop))*$"
    );
    private static final Pattern SERVICE_FOLLOW_UP_PATTERN = Pattern.compile(
            "^(?:(?:vay|the|con) )?(?:dich vu nao(?: phu hop)?|goi y dich vu|nen lam dich vu|can lam dich vu)"
                    + "(?: (?:voi|cho|toi|truong|hop|nay|do|giup|minh|nhe|a|khong|phu|hop))*$"
    );
    private static final Pattern PRICE_REFERENCE_PATTERN = Pattern.compile(
            "^(?:(?:vay|the|con) )?(?:(?:dich vu|goi|cai) (?:do|nay) "
                    + "(?:gia|chi phi|bao nhieu|bao tien|het bao nhieu)|"
                    + "(?:gia|chi phi) (?:cua )?dich vu (?:do|nay))"
                    + "(?: (?:la|bao|nhieu|tien|vay|a|nhe|cho|toi|biet|giup|minh|hien|tai|co|gia|khong))*$"
    );
    private static final Pattern ADDED_SYMPTOMS_PATTERN = Pattern.compile(
            "^(?:ngoai ra|toi con|con bi|them trieu chung)\\b.*"
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
    private static final List<SymptomRule> SYMPTOM_RULES = List.of(
            new SymptomRule("dau rang", "đau răng"),
            new SymptomRule("rang bi dau", "đau răng"),
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

    private final VietnameseRelativeDateResolver dateResolver;

    public FallbackConversationUnderstandingService(VietnameseRelativeDateResolver dateResolver) {
        this.dateResolver = dateResolver;
    }

    public ChatUnderstanding analyze(String message, ChatSemanticContext previousContext) {
        Optional<ChatUnderstanding> followUp = analyzeFollowUp(message, previousContext);
        if (followUp.isPresent()) {
            return followUp.orElseThrow();
        }
        String normalized = normalize(message);
        Optional<ChatDateRange> dateRange = resolveDateRange(normalized);
        ChatSessionPreference session = sessionPreference(normalized);

        List<String> symptoms = detectSymptoms(normalized);
        LinkedHashSet<ChatIntent> intents = new LinkedHashSet<>();
        if (AVAILABILITY_PATTERN.matcher(normalized).find()) {
            intents.add(ChatIntent.AVAILABLE_SLOTS);
        }
        if (SERVICE_CATALOG_PATTERN.matcher(normalized).find()) {
            intents.add(ChatIntent.SERVICE_CATALOG);
        }
        if (!symptoms.isEmpty()) {
            intents.add(ChatIntent.SYMPTOM_CONSULTATION);
        }
        if (DOCTOR_RECOMMENDATION_PATTERN.matcher(normalized).find()) {
            intents.add(ChatIntent.DOCTOR_RECOMMENDATION);
        }
        if (SERVICE_RECOMMENDATION_PATTERN.matcher(normalized).find()) {
            intents.add(ChatIntent.SERVICE_RECOMMENDATION);
        }
        if (APPOINTMENT_GUIDANCE_PATTERN.matcher(normalized).find()) {
            intents.add(ChatIntent.APPOINTMENT_GUIDANCE);
        }
        if (CLINIC_INFORMATION_PATTERN.matcher(normalized).find()
                && intents.isEmpty()) {
            intents.add(ChatIntent.CLINIC_INFORMATION);
        }
        if (intents.isEmpty() && DENTAL_VOCABULARY_PATTERN.matcher(normalized).find()) {
            intents.add(ChatIntent.SYMPTOM_CONSULTATION);
        }
        if (intents.isEmpty()) {
            intents.add(ChatIntent.OUT_OF_SCOPE);
        }

        return understanding(
                List.copyOf(intents),
                symptoms,
                dateRange.orElse(null),
                session,
                ChatFollowUpType.NONE
        );
    }

    public Optional<ChatUnderstanding> analyzeFollowUp(String message, ChatSemanticContext previous) {
        if (previous == null || previous.lastIntents().isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalize(message);
        Optional<ChatDateRange> dateRange = resolveDateRange(normalized);
        ChatSessionPreference session = sessionPreference(normalized);
        if (referenceOnly(normalized, "bac si khac")) {
            return Optional.of(understanding(List.of(ChatIntent.OUT_OF_SCOPE), List.of(),
                    dateRange.orElse(null), session, ChatFollowUpType.ALTERNATIVE_DOCTOR));
        }
        if (referenceOnly(normalized, "dich vu khac")) {
            return Optional.of(understanding(List.of(ChatIntent.OUT_OF_SCOPE), List.of(),
                    dateRange.orElse(null), session, ChatFollowUpType.ALTERNATIVE_SERVICE));
        }
        if ((dateRange.isPresent() || session != null)
                && FOLLOW_UP_REMAINDER_PATTERN.matcher(normalized).matches()) {
            return Optional.of(understanding(List.of(ChatIntent.OUT_OF_SCOPE), List.of(),
                    dateRange.orElse(null), session, ChatFollowUpType.TEMPORAL_REFINEMENT));
        }
        if (DOCTOR_FOLLOW_UP_PATTERN.matcher(normalized).matches()
                && (!previous.symptoms().isEmpty() || previous.selectedService() != null)) {
            return Optional.of(understanding(List.of(ChatIntent.DOCTOR_RECOMMENDATION), List.of(),
                    null, null, ChatFollowUpType.CONTEXT_CONTINUATION));
        }
        if (SERVICE_FOLLOW_UP_PATTERN.matcher(normalized).matches() && !previous.symptoms().isEmpty()) {
            return Optional.of(understanding(List.of(ChatIntent.SERVICE_RECOMMENDATION), List.of(),
                    null, null, ChatFollowUpType.CONTEXT_CONTINUATION));
        }
        if (PRICE_REFERENCE_PATTERN.matcher(normalized).matches() && previous.selectedService() != null) {
            return Optional.of(understanding(List.of(ChatIntent.SERVICE_CATALOG), List.of(),
                    null, null, ChatFollowUpType.CONTEXT_CONTINUATION));
        }
        List<String> symptoms = detectSymptoms(normalized);
        if (!previous.symptoms().isEmpty() && !symptoms.isEmpty()
                && ADDED_SYMPTOMS_PATTERN.matcher(normalized).matches()
                && !SERVICE_CATALOG_PATTERN.matcher(normalized).find()
                && !AVAILABILITY_PATTERN.matcher(normalized).find()) {
            return Optional.of(understanding(List.of(ChatIntent.SYMPTOM_CONSULTATION), symptoms,
                    null, null, ChatFollowUpType.CONTEXT_CONTINUATION));
        }
        return Optional.empty();
    }

    private boolean referenceOnly(String message, String reference) {
        return containsPhrase(message, reference)
                && FOLLOW_UP_REMAINDER_PATTERN.matcher(message.replace(reference, " ").strip()).matches();
    }

    private ChatUnderstanding understanding(
            List<ChatIntent> intents,
            List<String> symptoms,
            ChatDateRange dateRange,
            ChatSessionPreference session,
            ChatFollowUpType followUpType
    ) {
        ChatEntities entities = new ChatEntities(
                symptoms,
                null,
                null,
                null,
                null,
                dateRange != null && dateRange.isSingleDay() ? dateRange.from() : null,
                dateRange != null && !dateRange.isSingleDay() ? dateRange.from() : null,
                dateRange != null && !dateRange.isSingleDay() ? dateRange.to() : null,
                session,
                null
        );
        return new ChatUnderstanding(intents, entities, followUpType);
    }

    private Optional<ChatDateRange> resolveDateRange(String normalized) {
        Matcher explicitDate = EXPLICIT_DATE_PATTERN.matcher(normalized);
        if (explicitDate.find()) {
            String first = explicitDate.group();
            if (explicitDate.find()) {
                return Optional.empty();
            }
            return dateResolver.resolve(first);
        }
        Matcher upcomingDays = UPCOMING_DAYS_PATTERN.matcher(normalized);
        if (upcomingDays.find()) {
            return dateResolver.resolve(upcomingDays.group());
        }
        for (String weekday : WEEKDAY_EXPRESSIONS) {
            if (containsPhrase(normalized, weekday)) {
                String expression = containsPhrase(normalized, "tuan sau")
                        ? weekday + " tuan sau"
                        : containsPhrase(normalized, "tuan nay")
                                ? weekday + " tuan nay"
                                : weekday;
                return dateResolver.resolve(expression);
            }
        }
        for (String expression : RELATIVE_DATE_EXPRESSIONS) {
            if (containsPhrase(normalized, expression)) {
                return dateResolver.resolve(expression);
            }
        }
        return Optional.empty();
    }

    private ChatSessionPreference sessionPreference(String normalized) {
        if (containsPhrase(normalized, "sang") || containsPhrase(normalized, "buoi sang")) {
            return ChatSessionPreference.MORNING;
        }
        if (containsPhrase(normalized, "chieu") || containsPhrase(normalized, "buoi chieu")) {
            return ChatSessionPreference.AFTERNOON;
        }
        return null;
    }

    private List<String> detectSymptoms(String normalized) {
        LinkedHashSet<String> symptoms = new LinkedHashSet<>();
        for (SymptomRule rule : SYMPTOM_RULES) {
            if (containsPhrase(normalized, rule.normalizedPhrase())) {
                symptoms.add(rule.canonicalValue());
            }
        }
        return List.copyOf(symptoms);
    }

    private boolean containsPhrase(String normalized, String phrase) {
        return (" " + normalized + " ").contains(" " + phrase + " ");
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
