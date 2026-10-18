package com.dentalclinic.payment.mapper;

import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.payment.dto.PaymentItemResponse;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentItemSnapshot;
import com.dentalclinic.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

@Component
public class PaymentMapper {

    private final ClinicTimeProvider clinicTimeProvider;

    public PaymentMapper(ClinicTimeProvider clinicTimeProvider) {
        this.clinicTimeProvider = clinicTimeProvider;
    }

    public PaymentResponse toResponse(Payment payment) {
        var appointment = payment.getAppointment();
        var patient = appointment.getPatient();
        var doctorService = appointment.getDoctorService();
        var doctor = doctorService.getDoctor();
        var originalService = doctorService.getService();
        User confirmedBy = payment.getConfirmedBy();

        return new PaymentResponse(
                payment.getId(),
                appointment.getId(),
                appointment.getStatus(),
                patient.getId(),
                patient.getUser().getFullName(),
                patient.getPhone(),
                doctor.getId(),
                doctor.getUser().getFullName(),
                originalService.getId(),
                originalService.getName(),
                payment.getItems().stream().map(this::toItemResponse).toList(),
                payment.getTotalAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                confirmedBy == null ? null : confirmedBy.getId(),
                confirmedBy == null ? null : confirmedBy.getFullName(),
                toClinicOffset(payment.getPaidAt()),
                toClinicOffset(payment.getCreatedAt()),
                toClinicOffset(payment.getUpdatedAt())
        );
    }

    private PaymentItemResponse toItemResponse(PaymentItemSnapshot item) {
        return new PaymentItemResponse(
                item.serviceId(),
                item.serviceName(),
                item.unitPrice(),
                item.quantity(),
                item.subtotal(),
                item.additional()
        );
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return instant == null
                ? null
                : clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }
}
