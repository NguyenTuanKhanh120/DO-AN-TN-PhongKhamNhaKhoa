package com.dentalclinic.payment.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentItemSnapshot(
        UUID serviceId,
        String serviceName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        boolean additional
) {
}
