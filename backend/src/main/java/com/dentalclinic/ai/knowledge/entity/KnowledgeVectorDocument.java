package com.dentalclinic.ai.knowledge.entity;

import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vector_store")
public class KnowledgeVectorDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata = JsonNodeFactory.instance.objectNode();

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = KnowledgeEmbeddingConstants.VECTOR_DIMENSION)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;
}
