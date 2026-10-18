package com.dentalclinic.payment.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.service.PaymentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/patient/payments")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPaymentController {

    private final PaymentService paymentService;

    public PatientPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> getPayments(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(paymentService.getPatientPayments(
                currentUser.getId(),
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(paymentService.getPatientPayment(currentUser.getId(), id));
    }
}
