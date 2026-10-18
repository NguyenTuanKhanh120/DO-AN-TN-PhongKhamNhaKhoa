package com.dentalclinic.payment;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.payment.controller.AdminPaymentController;
import com.dentalclinic.payment.controller.AdminRevenueController;
import com.dentalclinic.payment.controller.DoctorPaymentController;
import com.dentalclinic.payment.controller.PatientPaymentController;
import com.dentalclinic.payment.dto.PaymentResponse;
import com.dentalclinic.payment.dto.RevenueSummaryResponse;
import com.dentalclinic.payment.dto.UpdatePaymentItemsRequest;
import com.dentalclinic.payment.entity.PaymentMethod;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.service.PaymentRevenueService;
import com.dentalclinic.payment.service.PaymentService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        DoctorPaymentController.class,
        AdminPaymentController.class,
        PatientPaymentController.class,
        AdminRevenueController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class PaymentControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private PaymentRevenueService paymentRevenueService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "payment-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthenticationForPaymentApis() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/doctor/appointments/{id}/payment", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/patient/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard/revenue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWrongRoles() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/doctor/appointments/{id}/payment", id)
                        .with(user(principal(Role.PATIENT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/payments")
                        .with(user(principal(Role.DOCTOR))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patient/payments")
                        .with(user(principal(Role.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowDoctorToUpdateActualServices() throws Exception {
        UserPrincipal doctor = principal(Role.DOCTOR);
        UUID appointmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        PaymentResponse response = response(PaymentStatus.DRAFT);
        when(paymentService.updateDoctorItems(
                eq(doctor.getId()),
                eq(appointmentId),
                any(UpdatePaymentItemsRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/doctor/appointments/{id}/payment/items", appointmentId)
                        .with(user(doctor))
                        .contentType("application/json")
                        .content("""
                                {
                                  "items": [
                                    {"serviceId": "%s", "quantity": 1}
                                  ]
                                }
                                """.formatted(serviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.message").value("Cập nhật dịch vụ thực tế thành công"));

        verify(paymentService).updateDoctorItems(
                eq(doctor.getId()),
                eq(appointmentId),
                any(UpdatePaymentItemsRequest.class)
        );
    }

    @Test
    void shouldValidateDoctorPaymentItems() throws Exception {
        mockMvc.perform(put(
                        "/api/doctor/appointments/{id}/payment/items",
                        UUID.randomUUID()
                ).with(user(principal(Role.DOCTOR)))
                        .contentType("application/json")
                        .content("""
                                {
                                  "items": [
                                    {"serviceId": "%s", "quantity": 0}
                                  ]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldAllowAdminToConfirmPayment() throws Exception {
        UserPrincipal admin = principal(Role.ADMIN);
        PaymentResponse response = response(PaymentStatus.PAID);
        when(paymentService.confirmPayment(
                admin.getId(),
                response.id(),
                PaymentMethod.CASH
        )).thenReturn(response);

        mockMvc.perform(patch("/api/admin/payments/{id}/confirm", response.id())
                        .with(user(admin))
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.message").value("Xác nhận thanh toán thành công"));
    }

    @Test
    void shouldValidateConfirmationMethod() throws Exception {
        mockMvc.perform(patch(
                        "/api/admin/payments/{id}/confirm",
                        UUID.randomUUID()
                ).with(user(principal(Role.ADMIN)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturnOnlyAuthenticatedPatientPayments() throws Exception {
        UserPrincipal patient = principal(Role.PATIENT);
        PaymentResponse payment = response(PaymentStatus.UNPAID);
        PageResponse<PaymentResponse> page = new PageResponse<>(
                List.of(payment), 0, 20, 1, 1, true, true, false, false
        );
        when(paymentService.getPatientPayments(patient.getId(), 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/patient/payments").with(user(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(payment.id().toString()));
    }

    @Test
    void shouldAllowAdminToReadRevenue() throws Exception {
        RevenueSummaryResponse response = new RevenueSummaryResponse(
                new BigDecimal("400000.00"),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 20),
                new BigDecimal("900000.00"),
                YearMonth.of(2026, 10),
                new BigDecimal("1500000.00"),
                8,
                3,
                new BigDecimal("650000.00")
        );
        when(paymentRevenueService.getRevenue(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 20),
                YearMonth.of(2026, 10)
        )).thenReturn(response);

        mockMvc.perform(get("/api/admin/dashboard/revenue")
                        .with(user(principal(Role.ADMIN)))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-20")
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayRevenue").value(400000.00))
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.totalUnpaidPayments").value(3));
    }

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                status == PaymentStatus.DRAFT
                        ? AppointmentStatus.IN_PROGRESS
                        : AppointmentStatus.COMPLETED,
                UUID.randomUUID(),
                "Bệnh nhân kiểm thử",
                "0901000001",
                UUID.randomUUID(),
                "Bác sĩ kiểm thử",
                UUID.randomUUID(),
                "Khám tổng quát",
                List.of(),
                new BigDecimal("400000.00"),
                status,
                status == PaymentStatus.PAID ? PaymentMethod.CASH : null,
                status == PaymentStatus.PAID ? UUID.randomUUID() : null,
                status == PaymentStatus.PAID ? "Quản trị viên" : null,
                status == PaymentStatus.PAID
                        ? OffsetDateTime.parse("2026-10-20T10:00:00+07:00")
                        : null,
                OffsetDateTime.parse("2026-10-20T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-20T10:00:00+07:00")
        );
    }

    private UserPrincipal principal(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "-" + user.getId() + "@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName(role.name());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
