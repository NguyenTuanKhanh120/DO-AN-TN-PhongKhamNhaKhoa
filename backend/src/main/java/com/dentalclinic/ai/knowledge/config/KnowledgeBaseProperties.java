package com.dentalclinic.ai.knowledge.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.ai.knowledge-base")
public record KnowledgeBaseProperties(
        @NotNull Path directory,
        @Positive long maxFileSizeBytes
) {
}
