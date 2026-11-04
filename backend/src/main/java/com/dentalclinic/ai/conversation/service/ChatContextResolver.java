package com.dentalclinic.ai.conversation.service;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.model.ClarificationField;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatContextResolver {

    private static final int MAX_CONTEXT_SYMPTOMS = 20;

    public ChatContextResolution resolve(
            ChatSemanticContext previous,
            ChatUnderstanding understanding
    ) {
        ChatSemanticContext safePrevious = previous == null
                ? ChatSemanticContext.empty()
                : previous;
        ChatEntities entities = understanding.entities();
        List<ChatIntent> effectiveIntents = effectiveIntents(safePrevious, understanding);
        ChatSemanticContext merged = new ChatSemanticContext(
                effectiveIntents,
                mergeSymptoms(safePrevious.symptoms(), entities.symptoms()),
                mergeReference(
                        safePrevious.selectedDoctor(),
                        entities.doctorId(),
                        entities.doctorName()
                ),
                mergeReference(
                        safePrevious.selectedService(),
                        entities.serviceId(),
                        entities.serviceName()
                ),
                entities.completeDateRange().orElse(safePrevious.dateRange()),
                entities.sessionPreference() == null
                        ? safePrevious.sessionPreference()
                        : entities.sessionPreference(),
                entities.timePreference() == null
                        ? safePrevious.timePreference()
                        : entities.timePreference()
        );

        List<ClarificationField> missingFields = missingFields(
                merged,
                entities,
                effectiveIntents
        );
        return new ChatContextResolution(
                merged,
                !missingFields.isEmpty(),
                missingFields,
                clarificationQuestion(missingFields),
                understanding.followUpType()
        );
    }

    private List<ChatIntent> effectiveIntents(
            ChatSemanticContext previous,
            ChatUnderstanding understanding
    ) {
        if (understanding.intents().equals(List.of(ChatIntent.OUT_OF_SCOPE))
                && !previous.lastIntents().isEmpty()
                && isFollowUpSignal(understanding)) {
            return previous.lastIntents();
        }
        return understanding.intents();
    }

    private boolean isFollowUpSignal(ChatUnderstanding understanding) {
        return understanding.followUpType() != ChatFollowUpType.NONE
                || understanding.entities().hasTemporalRefinement();
    }

    private List<String> mergeSymptoms(List<String> previous, List<String> current) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        previous.forEach(value -> putSymptom(merged, value));
        current.forEach(value -> putSymptom(merged, value));
        return merged.values().stream().limit(MAX_CONTEXT_SYMPTOMS).toList();
    }

    private void putSymptom(LinkedHashMap<String, String> target, String value) {
        if (value != null && !value.isBlank()) {
            String normalized = value.strip();
            target.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
    }

    private ChatEntityReference mergeReference(
            ChatEntityReference previous,
            java.util.UUID currentId,
            String currentName
    ) {
        if (currentId == null && currentName == null) {
            return previous;
        }
        if (currentId != null) {
            String name = currentName;
            if (name == null && previous != null && currentId.equals(previous.id())) {
                name = previous.name();
            }
            return new ChatEntityReference(currentId, name);
        }
        if (previous != null
                && previous.name() != null
                && previous.name().equalsIgnoreCase(currentName)) {
            return previous;
        }
        return new ChatEntityReference(null, currentName);
    }

    private List<ClarificationField> missingFields(
            ChatSemanticContext context,
            ChatEntities currentEntities,
            List<ChatIntent> intents
    ) {
        Set<ClarificationField> missing = new LinkedHashSet<>();
        if (currentEntities.hasPartialDateRange()) {
            missing.add(ClarificationField.DATE);
        }
        for (ChatIntent intent : intents) {
            switch (intent) {
                case SYMPTOM_CONSULTATION, SERVICE_RECOMMENDATION -> {
                    if (context.symptoms().isEmpty()) {
                        missing.add(ClarificationField.SYMPTOMS);
                    }
                }
                case DOCTOR_RECOMMENDATION -> {
                    if (context.symptoms().isEmpty() && context.selectedService() == null) {
                        missing.add(ClarificationField.SYMPTOMS);
                    }
                }
                case DOCTOR_AVAILABLE_SLOTS -> {
                    if (context.selectedDoctor() == null) {
                        missing.add(ClarificationField.DOCTOR);
                    }
                    if (context.dateRange() == null) {
                        missing.add(ClarificationField.DATE);
                    }
                }
                case SERVICE_AVAILABLE_DOCTORS -> {
                    if (context.selectedService() == null) {
                        missing.add(ClarificationField.SERVICE);
                    }
                }
                case AVAILABLE_SLOTS -> {
                    if (context.selectedDoctor() == null && context.selectedService() == null) {
                        missing.add(ClarificationField.SERVICE_OR_DOCTOR);
                    }
                    if (context.dateRange() == null) {
                        missing.add(ClarificationField.DATE);
                    }
                }
                case SERVICE_CATALOG, CLINIC_INFORMATION, APPOINTMENT_GUIDANCE, OUT_OF_SCOPE -> {
                }
            }
        }
        return List.copyOf(missing);
    }

    private String clarificationQuestion(List<ClarificationField> missingFields) {
        if (missingFields.isEmpty()) {
            return null;
        }
        List<String> details = new ArrayList<>(missingFields.size());
        for (ClarificationField field : missingFields) {
            details.add(switch (field) {
                case SYMPTOMS -> "triệu chứng bạn đang gặp";
                case DOCTOR -> "bác sĩ bạn muốn khám";
                case SERVICE -> "dịch vụ nha khoa bạn quan tâm";
                case SERVICE_OR_DOCTOR -> "dịch vụ hoặc bác sĩ bạn muốn tìm lịch";
                case DATE -> "ngày hoặc khoảng ngày bạn muốn khám";
            });
        }
        return "Bạn vui lòng cho biết " + String.join(" và ", details) + ".";
    }
}
