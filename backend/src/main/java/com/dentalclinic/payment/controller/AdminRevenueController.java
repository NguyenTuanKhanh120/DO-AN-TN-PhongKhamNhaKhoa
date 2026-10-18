package com.dentalclinic.payment.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.payment.dto.RevenueSummaryResponse;
import com.dentalclinic.payment.service.PaymentRevenueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRevenueController {

    private final PaymentRevenueService paymentRevenueService;

    public AdminRevenueController(PaymentRevenueService paymentRevenueService) {
        this.paymentRevenueService = paymentRevenueService;
    }

    @GetMapping("/revenue")
    public ApiResponse<RevenueSummaryResponse> getRevenue(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month
    ) {
        return ApiResponse.success(paymentRevenueService.getRevenue(fromDate, toDate, month));
    }
}
