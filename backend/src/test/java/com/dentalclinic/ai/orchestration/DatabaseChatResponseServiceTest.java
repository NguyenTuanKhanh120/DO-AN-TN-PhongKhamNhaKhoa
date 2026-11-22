package com.dentalclinic.ai.orchestration;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.service.ChatQueryPlanner;
import com.dentalclinic.ai.orchestration.service.DatabaseChatResponseService;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.service.dto.DentalServiceResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseChatResponseServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");
    private final DatabaseChatResponseService responseService =
            new DatabaseChatResponseService(new ChatQueryPlanner());

    @Test
    void shouldReturnAssignedDoctorsFromDatabaseGrounding() {
        DentalServiceResponse service = dentalService("Cạo vôi răng");
        DoctorResponse doctorB = doctor("Bác sĩ Bình", "Nha khoa tổng quát");
        DoctorResponse doctorA = doctor("Bác sĩ An", "Nha chu");
        ChatSemanticContext context = context(
                List.of(ChatIntent.SERVICE_AVAILABLE_DOCTORS),
                null,
                new ChatEntityReference(service.id(), service.name()),
                null
        );
        ChatGroundingResult grounding = grounding(
                context,
                List.of(service),
                List.of(doctorB, doctorA),
                List.of()
        );

        assertThat(responseService.respond(resolution(context), grounding)).contains("""
                Các bác sĩ đang hoạt động được phân công dịch vụ Cạo vôi răng:
                - Bác sĩ An — Nha chu
                - Bác sĩ Bình — Nha khoa tổng quát""");
    }

    @Test
    void shouldReturnAvailableCasesWithVerifiedDoctorNames() {
        LocalDate date = LocalDate.of(2026, 11, 20);
        DoctorResponse doctor = doctor("Bác sĩ Demo", "Nha khoa tổng quát");
        ChatSemanticContext context = context(
                List.of(ChatIntent.AVAILABLE_SLOTS),
                null,
                null,
                new ChatDateRange(date, date)
        );
        ChatSlotSuggestion slot = new ChatSlotSuggestion(
                doctor.id(),
                null,
                date,
                ChatSessionPreference.MORNING,
                2,
                LocalTime.of(8, 0),
                LocalTime.of(11, 0),
                LocalTime.of(9, 30),
                OffsetDateTime.of(date, LocalTime.of(9, 30), ZoneOffset.ofHours(7))
        );
        ChatGroundingResult grounding = grounding(
                context,
                List.of(),
                List.of(doctor),
                List.of(slot)
        );

        assertThat(responseService.respond(resolution(context), grounding)).contains("""
                Các ca khám còn trống phù hợp:
                - Bác sĩ Demo · 20/11/2026 · Buổi sáng, Ca 2 — dự kiến 09:30""");
    }

    @Test
    void shouldReturnStaticOperationalGuidanceWithoutGemini() {
        ChatSemanticContext context = context(
                List.of(ChatIntent.CLINIC_INFORMATION, ChatIntent.APPOINTMENT_GUIDANCE),
                null,
                null,
                null
        );

        assertThat(responseService.respond(
                resolution(context),
                ChatGroundingResult.empty(context)
        )).contains("""
                Nha Khoa An Tâm hỗ trợ tra cứu dịch vụ, bác sĩ, giá dịch vụ và ca khám còn trống trên hệ thống.

                Để đặt lịch, bạn chọn dịch vụ, bác sĩ, ngày khám, buổi khám và ca còn trống, sau đó kiểm tra lại thông tin và xác nhận đặt lịch.""");
    }

    @Test
    void shouldNotBypassRagForHybridQuestion() {
        DentalServiceResponse service = dentalService("Khám răng tổng quát");
        ChatSemanticContext context = context(
                List.of(ChatIntent.SERVICE_CATALOG, ChatIntent.SYMPTOM_CONSULTATION),
                null,
                new ChatEntityReference(service.id(), service.name()),
                null
        );
        ChatGroundingResult grounding = new ChatGroundingResult(
                context,
                true,
                true,
                List.of(),
                List.of(service),
                List.of(),
                List.of()
        );

        assertThat(responseService.respond(resolution(context), grounding)).isEmpty();
    }

    private ChatContextResolution resolution(ChatSemanticContext context) {
        return new ChatContextResolution(
                context,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
    }

    private ChatSemanticContext context(
            List<ChatIntent> intents,
            ChatEntityReference doctor,
            ChatEntityReference service,
            ChatDateRange dateRange
    ) {
        return new ChatSemanticContext(
                intents,
                List.of(),
                doctor,
                service,
                dateRange,
                null,
                null
        );
    }

    private ChatGroundingResult grounding(
            ChatSemanticContext context,
            List<DentalServiceResponse> services,
            List<DoctorResponse> doctors,
            List<ChatSlotSuggestion> slots
    ) {
        return new ChatGroundingResult(
                context,
                false,
                true,
                List.of(),
                services,
                doctors,
                slots
        );
    }

    private DentalServiceResponse dentalService(String name) {
        return new DentalServiceResponse(
                UUID.randomUUID(),
                name,
                "Dịch vụ nha khoa",
                new BigDecimal("200000"),
                null,
                true,
                NOW,
                NOW
        );
    }

    private DoctorResponse doctor(String name, String specialization) {
        return new DoctorResponse(
                UUID.randomUUID(),
                name,
                specialization,
                "LICENSE-" + UUID.randomUUID(),
                null,
                new BigDecimal("4.5"),
                5,
                DoctorStatus.ACTIVE,
                NOW,
                NOW
        );
    }
}
