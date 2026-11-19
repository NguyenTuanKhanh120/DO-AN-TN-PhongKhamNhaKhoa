package com.dentalclinic.ai.orchestration;

import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.gateway.AiCallExecutor;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.ai.orchestration.model.RagQueryResult;
import com.dentalclinic.ai.orchestration.service.RagQueryExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQueryExecutorTest {

    private final KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
    private final AiCallExecutor aiCallExecutor = new AiCallExecutor(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            100,
            Duration.ofSeconds(1)
    );
    private final RagQueryExecutor executor = new RagQueryExecutor(
            retrievalService,
            aiCallExecutor
    );

    @Test
    void shouldSkipRetrievalWhenRagIsNotRequired() {
        RagQueryResult result = executor.execute(
                "Giá khám răng tổng quát là bao nhiêu?",
                ChatSemanticContext.empty(),
                false
        );

        assertThat(result.available()).isTrue();
        assertThat(result.evidence()).isEmpty();
        verify(retrievalService, never()).retrieve(any());
    }

    @Test
    void shouldRetrieveEvidenceWithSymptomsIncludedInQuery() {
        KnowledgeRetrievalResult evidence = mock(KnowledgeRetrievalResult.class);
        when(retrievalService.retrieve(any(KnowledgeRetrievalRequest.class)))
                .thenReturn(List.of(evidence));
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(),
                List.of("đau răng", "sưng nướu"),
                null,
                null,
                null,
                null,
                null
        );

        RagQueryResult result = executor.execute("Tôi nên làm gì?", context, true);

        assertThat(result.available()).isTrue();
        assertThat(result.evidence()).containsExactly(evidence);
        ArgumentCaptor<KnowledgeRetrievalRequest> requestCaptor =
                ArgumentCaptor.forClass(KnowledgeRetrievalRequest.class);
        verify(retrievalService).retrieve(requestCaptor.capture());
        assertThat(requestCaptor.getValue().query())
                .isEqualTo("đau răng, sưng nướu. Tôi nên làm gì?");
    }

    @Test
    void shouldReturnUnavailableWhenKnowledgeBaseFails() {
        when(retrievalService.retrieve(any(KnowledgeRetrievalRequest.class)))
                .thenThrow(new KnowledgeBaseException("Knowledge base unavailable"));

        RagQueryResult result = executor.execute(
                "Tôi bị đau răng",
                ChatSemanticContext.empty(),
                true
        );

        assertThat(result.available()).isFalse();
        assertThat(result.evidence()).isEmpty();
    }
}
