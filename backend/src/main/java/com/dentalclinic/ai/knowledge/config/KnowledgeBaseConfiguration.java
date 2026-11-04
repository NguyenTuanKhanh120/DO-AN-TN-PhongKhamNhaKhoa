package com.dentalclinic.ai.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        KnowledgeBaseProperties.class,
        KnowledgeRetrievalProperties.class
})
public class KnowledgeBaseConfiguration {
}
