package com.dentalclinic.ai.embedding;

import java.util.List;

public interface EmbeddingGateway {

    List<float[]> embedAll(List<String> texts);

    String modelName();

    int dimension();
}
