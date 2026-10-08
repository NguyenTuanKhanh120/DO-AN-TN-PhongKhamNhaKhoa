package com.dentalclinic.patient.dto;

import com.dentalclinic.patient.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePatientProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String fullName,

        LocalDate dateOfBirth,

        @NotNull(message = "Giới tính không được để trống")
        Gender gender,

        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
        String phone,

        String address,

        String medicalHistory,

        String allergies
) {
}
