package com.dentalclinic.service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.service-images")
public record DentalServiceImageProperties(
        @NotBlank String storageDirectory,
        @Positive long maxFileSizeBytes
) {
}
