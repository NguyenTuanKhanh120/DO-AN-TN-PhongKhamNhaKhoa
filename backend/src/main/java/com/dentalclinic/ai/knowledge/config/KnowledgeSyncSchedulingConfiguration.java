package com.dentalclinic.ai.knowledge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class KnowledgeSyncSchedulingConfiguration {
}
