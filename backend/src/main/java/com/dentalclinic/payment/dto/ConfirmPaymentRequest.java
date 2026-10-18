package com.dentalclinic.payment.dto;

import com.dentalclinic.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
        @NotNull(message = "Phương thức thanh toán không được để trống")
        PaymentMethod paymentMethod
) {
}
