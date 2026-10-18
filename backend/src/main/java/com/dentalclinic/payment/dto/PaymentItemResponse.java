package com.dentalclinic.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentItemResponse(
        UUID serviceId,
        String serviceName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        boolean additional
) {
}
