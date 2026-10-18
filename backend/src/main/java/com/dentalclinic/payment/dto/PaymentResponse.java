package com.dentalclinic.payment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.payment.entity.PaymentMethod;
import com.dentalclinic.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID appointmentId,
        AppointmentStatus appointmentStatus,
        UUID patientId,
        String patientName,
        String patientPhone,
        UUID doctorId,
        String doctorName,
        UUID originalServiceId,
        String originalServiceName,
        List<PaymentItemResponse> items,
        BigDecimal totalAmount,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        UUID confirmedById,
        String confirmedByName,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public PaymentResponse {
        items = List.copyOf(items);
    }
}
