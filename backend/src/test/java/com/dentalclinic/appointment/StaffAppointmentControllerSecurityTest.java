package com.dentalclinic.appointment;

import com.dentalclinic.appointment.controller.StaffAppointmentController;
import com.dentalclinic.appointment.dto.CreateWalkInAppointmentRequest;
import com.dentalclinic.appointment.dto.CreateWalkInAppointmentResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.entity.BookingSource;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.appointment.service.AppointmentWalkInService;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.schedule.dto.CalendarAvailabilityStatus;
import com.dentalclinic.schedule.dto.WalkInAvailabilityResponse;
import com.dentalclinic.schedule.dto.WalkInCaseAvailabilityResponse;
import com.dentalclinic.schedule.dto.WalkInSessionAvailabilityResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SlotService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAppointmentController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class StaffAppointmentControllerSecurityTest {

    private static final String TEST_JWT_SECRET = Encoders.BASE64.encode(
            Jwts.SIG.HS256.key().build().getEncoded()
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private SlotService slotService;

    @MockitoBean
    private AppointmentWalkInService appointmentWalkInService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "staff-appointment-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireStaffRole() throws Exception {
        mockMvc.perform(get("/api/staff/appointments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/staff/appointments")
                        .with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStaffToViewAllDoctorAppointments() throws Exception {
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 20);
        PageResponse<com.dentalclinic.appointment.dto.AppointmentResponse> response =
                new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, false, false);
        when(appointmentQueryService.getStaffAppointments(
                "Nguyễn",
                AppointmentStatus.BOOKED,
                doctorId,
                null,
                date,
                date,
                0,
                20,
                "appointmentDate",
                Sort.Direction.ASC
        )).thenReturn(response);

        mockMvc.perform(get("/api/staff/appointments")
                        .with(user("staff@example.com").roles("STAFF"))
                        .param("search", "Nguyễn")
                        .param("status", "BOOKED")
                        .param("doctorId", doctorId.toString())
                        .param("fromDate", date.toString())
                        .param("toDate", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(appointmentQueryService).getStaffAppointments(
                "Nguyễn",
                AppointmentStatus.BOOKED,
                doctorId,
                null,
                date,
                date,
                0,
                20,
                "appointmentDate",
                Sort.Direction.ASC
        );
    }

    @Test
    void shouldAllowStaffToViewTodayWalkInAvailability() throws Exception {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 9);
        WalkInAvailabilityResponse response = new WalkInAvailabilityResponse(
                doctorId,
                serviceId,
                date,
                List.of(new WalkInSessionAvailabilityResponse(
                        ScheduleSession.MORNING,
                        CalendarAvailabilityStatus.AVAILABLE,
                        LocalTime.of(8, 0),
                        LocalTime.of(11, 0),
                        1,
                        List.of(new WalkInCaseAvailabilityResponse(
                                1,
                                LocalTime.of(8, 0),
                                OffsetDateTime.parse("2026-11-09T08:00:00+07:00"),
                                true
                        ))
                ))
        );
        when(slotService.getWalkInAvailability(doctorId, serviceId)).thenReturn(response);

        mockMvc.perform(get("/api/staff/appointments/walk-in/availability")
                        .with(user("staff@example.com").roles("STAFF"))
                        .param("doctorId", doctorId.toString())
                        .param("serviceId", serviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(date.toString()))
                .andExpect(jsonPath("$.data.sessions[0].session").value("MORNING"))
                .andExpect(jsonPath("$.data.sessions[0].cases[0].available").value(true));

        verify(slotService).getWalkInAvailability(doctorId, serviceId);
    }

    @Test
    void shouldProtectWalkInAvailabilityAndRequireSelectionContext() throws Exception {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        mockMvc.perform(get("/api/staff/appointments/walk-in/availability")
                        .param("doctorId", doctorId.toString())
                        .param("serviceId", serviceId.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/staff/appointments/walk-in/availability")
                        .with(user("patient@example.com").roles("PATIENT"))
                        .param("doctorId", doctorId.toString())
                        .param("serviceId", serviceId.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/staff/appointments/walk-in/availability")
                        .with(user("staff@example.com").roles("STAFF")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAllowStaffToCreateWalkInAppointment() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        CreateWalkInAppointmentRequest request = new CreateWalkInAppointmentRequest(
                patientId,
                doctorId,
                serviceId,
                ScheduleSession.MORNING,
                2
        );
        CreateWalkInAppointmentResponse response = new CreateWalkInAppointmentResponse(
                UUID.randomUUID(),
                patientId,
                doctorId,
                serviceId,
                LocalDate.of(2026, 11, 9),
                ScheduleSession.MORNING,
                2,
                LocalTime.of(8, 0),
                LocalTime.of(11, 0),
                LocalTime.of(9, 30),
                OffsetDateTime.parse("2026-11-09T09:30:00+07:00"),
                AppointmentStatus.BOOKED,
                BookingSource.WALK_IN,
                Instant.parse("2026-11-09T02:00:00Z")
        );
        UserPrincipal principal = staffPrincipal(staffId);
        when(appointmentWalkInService.create(staffId, request)).thenReturn(response);

        mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "%s",
                                  "doctorId": "%s",
                                  "serviceId": "%s",
                                  "session": "MORNING",
                                  "queueNumber": 2
                                }
                                """.formatted(patientId, doctorId, serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo lịch WALK_IN thành công"))
                .andExpect(jsonPath("$.data.bookingSource").value("WALK_IN"))
                .andExpect(jsonPath("$.data.appointmentDate").value("2026-11-09"))
                .andExpect(jsonPath("$.data.queueNumber").value(2));

        verify(appointmentWalkInService).create(staffId, request);
    }

    @Test
    void shouldProtectAndValidateWalkInCreation() throws Exception {
        String validBody = """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "serviceId": "%s",
                  "session": "MORNING",
                  "queueNumber": 2
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .with(user("patient@example.com").roles("PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .with(user(staffPrincipal(UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "session": "MORNING",
                                  "queueNumber": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldNotAllowStaffToOpenDoctorMedicalRecordApi() throws Exception {
        mockMvc.perform(get(
                        "/api/doctor/appointments/{id}/medical-record",
                        UUID.randomUUID()
                ).with(user("staff@example.com").roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    private UserPrincipal staffPrincipal(UUID staffId) {
        com.dentalclinic.user.entity.User staff = new com.dentalclinic.user.entity.User();
        staff.setId(staffId);
        staff.setEmail("staff@example.com");
        staff.setPasswordHash("not-used");
        staff.setFullName("Nhân viên tiếp nhận");
        staff.setRole(com.dentalclinic.user.entity.Role.STAFF);
        staff.setStatus(com.dentalclinic.user.entity.UserStatus.ACTIVE);
        return UserPrincipal.from(staff);
    }
}
