package com.dentalclinic.ai.orchestration.service;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.orchestration.model.ChatQueryPlan;
import com.dentalclinic.ai.orchestration.model.ChatQueryRoute;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ChatQueryPlanner {

    private static final Set<ChatIntent> DATABASE_INTENTS = Set.of(
            ChatIntent.SERVICE_CATALOG,
            ChatIntent.SERVICE_RECOMMENDATION,
            ChatIntent.DOCTOR_RECOMMENDATION,
            ChatIntent.DOCTOR_AVAILABLE_SLOTS,
            ChatIntent.SERVICE_AVAILABLE_DOCTORS,
            ChatIntent.AVAILABLE_SLOTS,
            ChatIntent.CLINIC_INFORMATION,
            ChatIntent.APPOINTMENT_GUIDANCE
    );

    public ChatQueryPlan plan(ChatContextResolution resolution) {
        Objects.requireNonNull(resolution, "resolution must not be null");
        ChatSemanticContext context = resolution.context();
        List<ChatIntent> intents = context.lastIntents();
        if (resolution.needsClarification()
                || intents.isEmpty()
                || intents.equals(List.of(ChatIntent.OUT_OF_SCOPE))) {
            return new ChatQueryPlan(ChatQueryRoute.CLARIFY, intents);
        }

        boolean databaseRequired = intents.stream().anyMatch(DATABASE_INTENTS::contains);
        boolean ragRequired = requiresRag(context);
        ChatQueryRoute route = route(databaseRequired, ragRequired);
        return new ChatQueryPlan(route, intents);
    }

    private boolean requiresRag(ChatSemanticContext context) {
        Set<ChatIntent> intents = Set.copyOf(context.lastIntents());
        return intents.contains(ChatIntent.SYMPTOM_CONSULTATION)
                || intents.contains(ChatIntent.SERVICE_RECOMMENDATION)
                || (intents.contains(ChatIntent.DOCTOR_RECOMMENDATION)
                && context.selectedService() == null);
    }

    private ChatQueryRoute route(boolean databaseRequired, boolean ragRequired) {
        if (databaseRequired && ragRequired) {
            return ChatQueryRoute.HYBRID;
        }
        if (ragRequired) {
            return ChatQueryRoute.RAG_ONLY;
        }
        return ChatQueryRoute.DB_ONLY;
    }
}
