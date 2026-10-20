package com.dentalclinic.payment.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.payment.dto.ConfirmPaymentRequest;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/staff/payments")
@PreAuthorize("hasRole('STAFF')")
public class StaffPaymentController {

    private final PaymentService paymentService;

    public StaffPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> getPayments(
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        return ApiResponse.success(paymentService.getStaffPayments(
                search,
                status,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                direction
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ApiResponse.success(paymentService.getStaffPayment(id));
    }

    @PatchMapping("/{id}/confirm")
    public ApiResponse<PaymentResponse> confirmPayment(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return ApiResponse.success(
                "Xác nhận thanh toán thành công",
                paymentService.confirmPayment(currentUser.getId(), id, request.paymentMethod())
        );
    }
}
