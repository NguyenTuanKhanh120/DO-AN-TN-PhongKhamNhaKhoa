package com.dentalclinic.doctor.dto;

import com.dentalclinic.doctor.entity.DoctorStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDoctorStatusRequest(
        @NotNull(message = "Trạng thái làm việc không được để trống")
        DoctorStatus status
) {
}
