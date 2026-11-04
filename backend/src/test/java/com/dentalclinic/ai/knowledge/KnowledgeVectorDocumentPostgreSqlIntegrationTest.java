package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("development")
class KnowledgeVectorDocumentPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private KnowledgeVectorDocumentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdDocumentIds = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "knowledge-vector-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAllById(createdDocumentIds);
        createdDocumentIds.clear();
    }

    @Test
    void shouldPersistVectorWithExpectedDimensionAndJsonMetadata() {
        KnowledgeVectorDocument document = new KnowledgeVectorDocument();
        document.setContent("Nội dung kiểm tra pgvector");
        document.setMetadata(JsonNodeFactory.instance.objectNode()
                .put("sourceKey", "nidcr-dental-knowledge-vi")
                .put("sourceVersion", "1.0.0")
                .put("recordId", "NIDCR-VI-TEST"));
        float[] embedding = new float[KnowledgeEmbeddingConstants.VECTOR_DIMENSION];
        embedding[0] = 1.0f;
        document.setEmbedding(embedding);

        KnowledgeVectorDocument saved = repository.saveAndFlush(document);
        createdDocumentIds.add(saved.getId());

        Integer dimensions = jdbcTemplate.queryForObject(
                "select vector_dims(embedding) from vector_store where id = ?",
                Integer.class,
                saved.getId()
        );
        String recordId = jdbcTemplate.queryForObject(
                "select metadata ->> 'recordId' from vector_store where id = ?",
                String.class,
                saved.getId()
        );
        KnowledgeVectorDocument reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(dimensions).isEqualTo(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        assertThat(recordId).isEqualTo("NIDCR-VI-TEST");
        assertThat(reloaded.getEmbedding()).hasSize(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        assertThat(reloaded.getMetadata().path("sourceKey").asText())
                .isEqualTo("nidcr-dental-knowledge-vi");
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
