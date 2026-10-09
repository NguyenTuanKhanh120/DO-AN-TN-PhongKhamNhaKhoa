package com.dentalclinic.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateDentalServiceRequest(
        @NotBlank(message = "Tên dịch vụ không được để trống")
        @Size(max = 200, message = "Tên dịch vụ không được vượt quá 200 ký tự")
        String name,

        String description,

        @NotNull(message = "Giá dịch vụ không được để trống")
        @DecimalMin(value = "0.00", message = "Giá dịch vụ không được âm")
        @Digits(integer = 12, fraction = 2, message = "Giá dịch vụ vượt quá định dạng NUMERIC(14,2)")
        BigDecimal price
) {
}
