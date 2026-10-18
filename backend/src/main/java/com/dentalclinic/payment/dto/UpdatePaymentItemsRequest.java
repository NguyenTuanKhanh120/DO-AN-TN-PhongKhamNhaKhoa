package com.dentalclinic.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePaymentItemsRequest(
        @NotNull(message = "Danh sách dịch vụ không được để trống")
        @Size(max = 100, message = "Phiếu thanh toán không được vượt quá 100 dịch vụ")
        List<@Valid PaymentItemRequest> items
) {
}
