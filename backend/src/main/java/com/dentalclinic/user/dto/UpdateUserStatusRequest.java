package com.dentalclinic.user.dto;

import com.dentalclinic.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái tài khoản không được để trống")
        UserStatus status
) {
}
