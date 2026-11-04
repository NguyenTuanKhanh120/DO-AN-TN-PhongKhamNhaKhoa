package com.dentalclinic.ai.knowledge.repository;

import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCandidate;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCriteria;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalFilter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KnowledgeRetrievalRepository {

    private static final String BASE_QUERY = """
            select vector.id,
                   vector.content,
                   vector.metadata::text as metadata_json,
                   1 - (vector.embedding <=> cast(:queryEmbedding as vector)) as score
            from vector_store vector
            where vector_dims(vector.embedding) = :embeddingDimension
              and vector.metadata ->> 'embeddingModel' = :embeddingModel
              and vector.metadata ->> 'embeddingDimension' = :embeddingDimensionText
              and exists (
                  select 1
                  from knowledge_sources source
                  where source.source_key = vector.metadata ->> 'sourceKey'
                    and source.content_hash is not null
                    and source.content_hash = vector.metadata ->> 'contentHash'
                    and source.embedding_model = :embeddingModel
                    and source.embedding_dimension = :embeddingDimension
              )
            """;

    private final JdbcClient jdbcClient;

    public KnowledgeRetrievalRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<KnowledgeRetrievalCandidate> search(KnowledgeRetrievalCriteria criteria) {
        KnowledgeRetrievalFilter filter = criteria.filter();
        StringBuilder sql = new StringBuilder(BASE_QUERY);
        appendMetadataFilter(sql, "sourceKey", filter.sourceKey());
        appendMetadataFilter(sql, "category", filter.category());
        appendMetadataFilter(sql, "language", filter.language());
        appendMetadataFilter(sql, "clinicalReviewStatus", filter.clinicalReviewStatus());
        sql.append("""
                  and (vector.embedding <=> cast(:queryEmbedding as vector)) <= :maximumDistance
                order by vector.embedding <=> cast(:queryEmbedding as vector), vector.id
                limit :topK
                """);

        JdbcClient.StatementSpec statement = jdbcClient.sql(sql.toString())
                .param("queryEmbedding", toVectorLiteral(criteria.queryEmbedding()))
                .param("embeddingModel", criteria.embeddingModel())
                .param("embeddingDimension", criteria.embeddingDimension())
                .param("embeddingDimensionText", Integer.toString(criteria.embeddingDimension()))
                .param("maximumDistance", 1.0 - criteria.minimumSimilarity())
                .param("topK", criteria.topK());
        statement = bindMetadataFilter(statement, "sourceKey", filter.sourceKey());
        statement = bindMetadataFilter(statement, "category", filter.category());
        statement = bindMetadataFilter(statement, "language", filter.language());
        statement = bindMetadataFilter(statement, "clinicalReviewStatus", filter.clinicalReviewStatus());

        return statement.query((resultSet, rowNumber) -> new KnowledgeRetrievalCandidate(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                resultSet.getDouble("score")
        )).list();
    }

    private void appendMetadataFilter(StringBuilder sql, String metadataKey, String value) {
        if (value != null) {
            sql.append(" and vector.metadata ->> '")
                    .append(metadataKey)
                    .append("' = :")
                    .append(metadataKey)
                    .append(System.lineSeparator());
        }
    }

    private JdbcClient.StatementSpec bindMetadataFilter(
            JdbcClient.StatementSpec statement,
            String parameterName,
            String value
    ) {
        return value == null ? statement : statement.param(parameterName, value);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder(embedding.length * 12).append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[index]));
        }
        return literal.append(']').toString();
    }
}
