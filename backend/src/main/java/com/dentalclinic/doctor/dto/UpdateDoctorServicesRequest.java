package com.dentalclinic.doctor.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateDoctorServicesRequest(
        @NotNull(message = "Danh sách dịch vụ không được để trống")
        List<@NotNull(message = "ID dịch vụ không được để trống") UUID> serviceIds
) {
}
