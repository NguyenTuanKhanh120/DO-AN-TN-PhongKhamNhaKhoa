package com.dentalclinic.ai.knowledge.model;

import java.net.URI;
import java.time.LocalDate;

public record DentalKnowledgeSource(
        String publisher,
        String organization,
        String title,
        URI url,
        LocalDate retrievedAt,
        String contentType
) {
}
