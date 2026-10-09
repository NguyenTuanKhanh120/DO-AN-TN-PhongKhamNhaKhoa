package com.dentalclinic.service.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateDentalServiceStatusRequest(
        @NotNull(message = "Trạng thái dịch vụ không được để trống")
        Boolean active
) {
}
