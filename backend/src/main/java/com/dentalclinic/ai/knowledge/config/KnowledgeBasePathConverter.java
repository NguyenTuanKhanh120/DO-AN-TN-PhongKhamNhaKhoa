package com.dentalclinic.ai.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationPropertiesBinding
public final class KnowledgeBasePathConverter implements Converter<String, Path> {

    @Override
    public Path convert(String source) {
        return Path.of(source);
    }
}
