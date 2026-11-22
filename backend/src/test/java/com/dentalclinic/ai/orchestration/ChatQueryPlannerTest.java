package com.dentalclinic.ai.orchestration;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ClarificationField;
import com.dentalclinic.ai.orchestration.model.ChatQueryRoute;
import com.dentalclinic.ai.orchestration.service.ChatQueryPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatQueryPlannerTest {

    private final ChatQueryPlanner planner = new ChatQueryPlanner();

    @Test
    void shouldRouteOperationalQueriesToDatabase() {
        assertThat(plan(ChatIntent.SERVICE_CATALOG)).isEqualTo(ChatQueryRoute.DB_ONLY);
        assertThat(plan(ChatIntent.DOCTOR_AVAILABLE_SLOTS)).isEqualTo(ChatQueryRoute.DB_ONLY);
        assertThat(plan(ChatIntent.SERVICE_AVAILABLE_DOCTORS)).isEqualTo(ChatQueryRoute.DB_ONLY);
    }

    @Test
    void shouldRouteSymptomConsultationToRag() {
        assertThat(plan(ChatIntent.SYMPTOM_CONSULTATION)).isEqualTo(ChatQueryRoute.RAG_ONLY);
    }

    @Test
    void shouldRouteRecommendationsAndMixedIntentsToHybrid() {
        assertThat(plan(ChatIntent.SERVICE_RECOMMENDATION)).isEqualTo(ChatQueryRoute.HYBRID);
        assertThat(plan(ChatIntent.DOCTOR_RECOMMENDATION)).isEqualTo(ChatQueryRoute.HYBRID);

        ChatSemanticContext mixedContext = context(List.of(
                ChatIntent.SYMPTOM_CONSULTATION,
                ChatIntent.AVAILABLE_SLOTS
        ));
        assertThat(planner.plan(resolution(mixedContext)).route())
                .isEqualTo(ChatQueryRoute.HYBRID);
    }

    @Test
    void shouldRouteDoctorRecommendationWithSelectedServiceToDatabase() {
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_RECOMMENDATION),
                List.of(),
                null,
                new ChatEntityReference(null, "Khám răng tổng quát"),
                null,
                null,
                null
        );

        assertThat(planner.plan(resolution(context)).route()).isEqualTo(ChatQueryRoute.DB_ONLY);
    }

    @Test
    void shouldUseClarifyRouteWhenInformationIsMissingOrOutOfScope() {
        ChatSemanticContext missingDoctor = context(List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS));
        ChatContextResolution clarification = new ChatContextResolution(
                missingDoctor,
                true,
                List.of(ClarificationField.DOCTOR),
                "Bạn vui lòng cho biết bác sĩ bạn muốn khám.",
                ChatFollowUpType.NONE
        );

        assertThat(planner.plan(clarification).route()).isEqualTo(ChatQueryRoute.CLARIFY);
        assertThat(plan(ChatIntent.OUT_OF_SCOPE)).isEqualTo(ChatQueryRoute.CLARIFY);
    }

    private ChatQueryRoute plan(ChatIntent intent) {
        return planner.plan(resolution(context(List.of(intent)))).route();
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

    private ChatSemanticContext context(List<ChatIntent> intents) {
        return new ChatSemanticContext(
                intents,
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
