package com.dentalclinic.common.response;

public record FieldViolation(
        String field,
        String message
) {
}
