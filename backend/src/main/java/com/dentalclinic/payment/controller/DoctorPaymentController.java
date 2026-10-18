package com.dentalclinic.payment.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.dto.UpdatePaymentItemsRequest;
import com.dentalclinic.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/doctor/appointments")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorPaymentController {

    private final PaymentService paymentService;

    public DoctorPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}/payment")
    public ApiResponse<PaymentResponse> getPayment(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(paymentService.getDoctorPayment(currentUser.getId(), id));
    }

    @PutMapping("/{id}/payment/items")
    public ApiResponse<PaymentResponse> updateItems(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentItemsRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật dịch vụ thực tế thành công",
                paymentService.updateDoctorItems(currentUser.getId(), id, request)
        );
    }
}
