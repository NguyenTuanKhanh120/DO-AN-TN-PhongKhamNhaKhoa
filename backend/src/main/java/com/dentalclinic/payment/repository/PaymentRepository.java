package com.dentalclinic.payment.repository;

import com.dentalclinic.payment.entity.Payment;
import com.dentalclinic.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>,
        JpaSpecificationExecutor<Payment> {

    @Override
    @EntityGraph(attributePaths = {
            "appointment.patient.user",
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service",
            "confirmedBy"
    })
    Page<Payment> findAll(Specification<Payment> specification, Pageable pageable);

    Optional<Payment> findByAppointmentId(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.appointment.id = :appointmentId")
    Optional<Payment> findForUpdateByAppointmentId(
            @Param("appointmentId") UUID appointmentId
    );

    @EntityGraph(attributePaths = {
            "appointment.patient.user",
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service",
            "confirmedBy"
    })
    @Query("""
            select payment
            from Payment payment
            where payment.appointment.id = :appointmentId
              and payment.appointment.doctorService.doctor.id = :doctorId
            """)
    Optional<Payment> findDetailedByAppointmentIdAndDoctorId(
            @Param("appointmentId") UUID appointmentId,
            @Param("doctorId") UUID doctorId
    );

    @EntityGraph(attributePaths = {
            "appointment.patient.user",
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service",
            "confirmedBy"
    })
    @Query("select payment from Payment payment where payment.id = :paymentId")
    Optional<Payment> findDetailedById(@Param("paymentId") UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :paymentId")
    Optional<Payment> findForUpdateDetailedById(@Param("paymentId") UUID paymentId);

    @EntityGraph(attributePaths = {
            "appointment.patient.user",
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service",
            "confirmedBy"
    })
    @Query(
            value = """
                    select payment
                    from Payment payment
                    where payment.appointment.patient.id = :patientId
                      and payment.status <> :excludedStatus
                    """,
            countQuery = """
                    select count(payment)
                    from Payment payment
                    where payment.appointment.patient.id = :patientId
                      and payment.status <> :excludedStatus
                    """
    )
    Page<Payment> findPatientPayments(
            @Param("patientId") UUID patientId,
            @Param("excludedStatus") PaymentStatus excludedStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "appointment.patient.user",
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service",
            "confirmedBy"
    })
    @Query("""
            select payment
            from Payment payment
            where payment.id = :paymentId
              and payment.appointment.patient.id = :patientId
              and payment.status <> :excludedStatus
            """)
    Optional<Payment> findPatientPaymentDetail(
            @Param("paymentId") UUID paymentId,
            @Param("patientId") UUID patientId,
            @Param("excludedStatus") PaymentStatus excludedStatus
    );

    long countByStatus(PaymentStatus status);

    @Query("""
            select coalesce(sum(payment.totalAmount), 0)
            from Payment payment
            where payment.status = :status
              and payment.paidAt >= :rangeStart
              and payment.paidAt < :rangeEnd
            """)
    BigDecimal sumPaidAmountInRange(
            @Param("status") PaymentStatus status,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

    @Query("""
            select coalesce(sum(payment.totalAmount), 0)
            from Payment payment
            where payment.status = :status
            """)
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
}
