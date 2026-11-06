package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseConfiguration;
import com.dentalclinic.ai.knowledge.config.KnowledgeBasePathConverter;
import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.config.KnowledgeRetrievalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    KnowledgeBaseConfiguration.class,
                    KnowledgeBasePathConverter.class
            )
            .withPropertyValues(
                    "app.ai.retrieval.default-top-k=3",
                    "app.ai.retrieval.max-top-k=20",
                    "app.ai.retrieval.similarity-threshold=0.55"
            );

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(
                    KnowledgeBaseConfiguration.class,
                    KnowledgeBasePathConverter.class
            )
            .withPropertyValues(
                    "app.ai.knowledge-base.directory=../knowledge-base",
                    "app.ai.knowledge-base.max-file-size-bytes=5242880",
                    "app.ai.retrieval.default-top-k=3",
                    "app.ai.retrieval.max-top-k=20",
                    "app.ai.retrieval.similarity-threshold=0.55"
            );

    @Test
    void shouldBindConfiguredExternalDirectory() {
        contextRunner
                .withPropertyValues(
                        "app.ai.knowledge-base.directory=../knowledge-base",
                        "app.ai.knowledge-base.max-file-size-bytes=5242880"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeBaseProperties.class);
                    assertThat(context).hasSingleBean(KnowledgeRetrievalProperties.class);
                    assertThat(context.getBean(KnowledgeBaseProperties.class).directory())
                            .isEqualTo(Path.of("../knowledge-base"));
                    assertThat(context.getBean(KnowledgeBaseProperties.class).maxFileSizeBytes())
                            .isEqualTo(5_242_880L);
                });
    }

    @Test
    void shouldBindRelativeDirectoryInWebApplicationContext() {
        webContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KnowledgeBaseProperties.class).directory())
                    .isEqualTo(Path.of("../knowledge-base"));
        });
    }

    @Test
    void shouldBindRetrievalConfiguration() {
        contextRunner
                .withPropertyValues(
                        "app.ai.knowledge-base.directory=../knowledge-base",
                        "app.ai.knowledge-base.max-file-size-bytes=5242880"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KnowledgeRetrievalProperties properties = context.getBean(
                            KnowledgeRetrievalProperties.class
                    );
                    assertThat(properties.defaultTopK()).isEqualTo(3);
                    assertThat(properties.maxTopK()).isEqualTo(20);
                    assertThat(properties.similarityThreshold()).isEqualTo(0.55);
                });
    }

    @Test
    void shouldRejectDefaultTopKGreaterThanMaximum() {
        contextRunner
                .withPropertyValues(
                        "app.ai.knowledge-base.directory=../knowledge-base",
                        "app.ai.knowledge-base.max-file-size-bytes=5242880",
                        "app.ai.retrieval.default-top-k=21"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("must not exceed");
                });
    }

    @Test
    void shouldFailWhenDirectoryConfigurationIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.knowledge-base.max-file-size-bytes=5242880")
                .run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("must not be null");
                });
    }

    @Test
    void shouldFailWhenMaximumFileSizeIsNotPositive() {
        contextRunner
                .withPropertyValues(
                        "app.ai.knowledge-base.directory=../knowledge-base",
                        "app.ai.knowledge-base.max-file-size-bytes=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("must be greater than 0");
                });
    }
}
