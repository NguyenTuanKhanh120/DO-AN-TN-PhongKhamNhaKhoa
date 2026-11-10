package com.dentalclinic.patient.dto;

import com.dentalclinic.patient.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateStaffPatientRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String fullName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
        String phone,

        LocalDate dateOfBirth,

        Gender gender,

        String address,

        String medicalHistory,

        String allergies
) {
}
