package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.service.DeterministicConversationUnderstandingService;
import com.dentalclinic.ai.conversation.service.VietnameseRelativeDateResolver;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorManagementService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeterministicConversationUnderstandingServiceTest {

    private final DentalServiceManagementService dentalServiceManagementService =
            mock(DentalServiceManagementService.class);
    private final DoctorManagementService doctorManagementService =
            mock(DoctorManagementService.class);
    private final VietnameseRelativeDateResolver dateResolver =
            mock(VietnameseRelativeDateResolver.class);
    private final DeterministicConversationUnderstandingService service =
            new DeterministicConversationUnderstandingService(
                    dentalServiceManagementService,
                    doctorManagementService,
                    dateResolver
            );

    @Test
    void shouldResolveNamedDoctorAvailabilityWithoutGemini() {
        LocalDate tomorrow = LocalDate.of(2026, 11, 19);
        DoctorResponse doctor = doctor("Bác sĩ Demo");
        when(dateResolver.resolve("ngay mai"))
                .thenReturn(Optional.of(ChatDateRange.single(tomorrow)));
        when(doctorManagementService.getDoctors(
                null,
                null,
                DoctorStatus.ACTIVE,
                0,
                100
        )).thenReturn(pageOf(doctor));

        var result = service.analyze(
                "Bác sĩ Demo ngày mai có trống ca không?"
        ).orElseThrow();

        assertThat(result.intents()).containsExactly(ChatIntent.DOCTOR_AVAILABLE_SLOTS);
        assertThat(result.entities().doctorId()).isEqualTo(doctor.id());
        assertThat(result.entities().doctorName()).isEqualTo(doctor.fullName());
        assertThat(result.entities().date()).isEqualTo(tomorrow);
    }

    @Test
    void shouldMatchActiveServicePriceQuestionWithoutGemini() {
        DentalServiceResponse examination = dentalService("Khám răng tổng quát", "200000");
        DentalServiceResponse cleaning = dentalService("Cạo vôi răng", "350000");
        when(dentalServiceManagementService.getActiveServices())
                .thenReturn(List.of(cleaning, examination));

        var result = service.analyze(
                "Dịch vụ KHAM RANG tong quat giá báo nhiêu?"
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().intents())
                .containsExactly(ChatIntent.SERVICE_CATALOG);
        assertThat(result.orElseThrow().entities().serviceId())
                .isEqualTo(examination.id());
        assertThat(result.orElseThrow().entities().serviceName())
                .isEqualTo(examination.name());
    }

    @Test
    void shouldClassifySimpleSymptomQuestionWithoutQueryingServices() {
        var result = service.analyze("Tôi bị đau răng và ê buốt").orElseThrow();

        assertThat(result.intents()).containsExactly(ChatIntent.SYMPTOM_CONSULTATION);
        assertThat(result.entities().symptoms())
                .containsExactly("đau răng", "ê buốt răng");

        verify(dentalServiceManagementService, never()).getActiveServices();
    }

    @Test
    void shouldClassifySimpleServiceCatalogQuestionWithoutGemini() {
        var result = service.analyze(
                "Hiện tại nha khoa có những dịch vụ nào?"
        ).orElseThrow();

        assertThat(result.intents()).containsExactly(ChatIntent.SERVICE_CATALOG);
        assertThat(result.entities()).isEqualTo(ChatEntities.empty());
        verify(dentalServiceManagementService, never()).getActiveServices();
    }

    @Test
    void shouldKeepBothPriceAndSymptomIntentsForMixedQuestion() {
        DentalServiceResponse examination = dentalService("Khám răng tổng quát", "200000");
        when(dentalServiceManagementService.getActiveServices())
                .thenReturn(List.of(examination));

        var result = service.analyze(
                "Tôi bị đau răng và ê buốt, khám răng tổng quát giá bao nhiêu?"
        ).orElseThrow();

        assertThat(result.intents()).containsExactly(
                ChatIntent.SERVICE_CATALOG,
                ChatIntent.SYMPTOM_CONSULTATION
        );
        assertThat(result.entities().symptoms())
                .containsExactly("đau răng", "ê buốt răng");
        assertThat(result.entities().serviceId()).isEqualTo(examination.id());
    }

    @Test
    void shouldResolveServiceAvailabilityWithoutGemini() {
        LocalDate tomorrow = LocalDate.of(2026, 11, 19);
        DentalServiceResponse cleaning = dentalService("Cạo vôi răng", "350000");
        when(dentalServiceManagementService.getActiveServices()).thenReturn(List.of(cleaning));
        when(dateResolver.resolve("ngay mai"))
                .thenReturn(Optional.of(ChatDateRange.single(tomorrow)));

        var result = service.analyze(
                "Cạo vôi răng chiều ngày mai còn ca không?"
        ).orElseThrow();

        assertThat(result.intents()).containsExactly(ChatIntent.AVAILABLE_SLOTS);
        assertThat(result.entities().serviceId()).isEqualTo(cleaning.id());
        assertThat(result.entities().date()).isEqualTo(tomorrow);
        assertThat(result.entities().sessionPreference())
                .isEqualTo(ChatSessionPreference.AFTERNOON);
    }

    @Test
    void shouldResolveDoctorsAssignedToServiceWithoutGemini() {
        DentalServiceResponse examination = dentalService("Khám răng tổng quát", "200000");
        when(dentalServiceManagementService.getActiveServices())
                .thenReturn(List.of(examination));

        var result = service.analyze(
                "Khám răng tổng quát do bác sĩ nào thực hiện?"
        ).orElseThrow();

        assertThat(result.intents()).containsExactly(ChatIntent.SERVICE_AVAILABLE_DOCTORS);
        assertThat(result.entities().serviceId()).isEqualTo(examination.id());
    }

    @Test
    void shouldResolveGenericAvailabilityAndAskForMissingDoctorOrServiceLater() {
        LocalDate tomorrow = LocalDate.of(2026, 11, 19);
        when(dateResolver.resolve("ngay mai"))
                .thenReturn(Optional.of(ChatDateRange.single(tomorrow)));

        var result = service.analyze("Ngày mai còn lịch không?").orElseThrow();

        assertThat(result.intents()).containsExactly(ChatIntent.AVAILABLE_SLOTS);
        assertThat(result.entities().date()).isEqualTo(tomorrow);
        assertThat(result.entities().doctorId()).isNull();
        assertThat(result.entities().serviceId()).isNull();
    }

    @Test
    void shouldKeepSymptomAndDoctorRecommendationAsHybridIntents() {
        var result = service.analyze(
                "Tôi bị đau răng thì bác sĩ nào phù hợp?"
        ).orElseThrow();

        assertThat(result.intents()).containsExactly(
                ChatIntent.SYMPTOM_CONSULTATION,
                ChatIntent.DOCTOR_RECOMMENDATION
        );
        assertThat(result.entities().symptoms()).containsExactly("đau răng");
    }

    @Test
    void shouldRecognizeOperationalGuidanceWithoutGemini() {
        assertThat(service.analyze("Hướng dẫn đặt lịch như thế nào?").orElseThrow().intents())
                .containsExactly(ChatIntent.APPOINTMENT_GUIDANCE);
        assertThat(service.analyze("Phòng khám hỗ trợ gì?").orElseThrow().intents())
                .containsExactly(ChatIntent.CLINIC_INFORMATION);
    }

    @Test
    void shouldDelegateComplexExplicitDateRangeToGemini() {
        var result = service.analyze(
                "Khám răng tổng quát còn lịch từ 20/11/2026 đến 22/11/2026 không?"
        );

        assertThat(result).isEmpty();
        verify(dentalServiceManagementService, never()).getActiveServices();
        verify(doctorManagementService, never()).getDoctors(
                null,
                null,
                DoctorStatus.ACTIVE,
                0,
                100
        );
    }

    @Test
    void shouldRecognizePriceWordingVariantsWithoutGemini() {
        DentalServiceResponse cleaning = dentalService("Cạo vôi răng", "350000");
        when(dentalServiceManagementService.getActiveServices()).thenReturn(List.of(cleaning));

        for (String message : List.of(
                "Cạo vôi răng bao tiền?",
                "Chi phí cạo vôi răng là bao nhiêu?",
                "Mức giá cạo vôi răng",
                "Cạo vôi răng hết mấy tiền?"
        )) {
            var result = service.analyze(message).orElseThrow();
            assertThat(result.intents()).contains(ChatIntent.SERVICE_CATALOG);
            assertThat(result.entities().serviceId()).isEqualTo(cleaning.id());
        }
    }

    private DentalServiceResponse dentalService(String name, String price) {
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        return new DentalServiceResponse(
                UUID.randomUUID(),
                name,
                "Dịch vụ nha khoa",
                new BigDecimal(price),
                null,
                true,
                now,
                now
        );
    }

    private DoctorResponse doctor(String fullName) {
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        return new DoctorResponse(
                UUID.randomUUID(),
                fullName,
                "Nha khoa tổng quát",
                "DENTAL-DEMO-001",
                null,
                new BigDecimal("4.4"),
                5,
                DoctorStatus.ACTIVE,
                now,
                now
        );
    }

    private PageResponse<DoctorResponse> pageOf(DoctorResponse doctor) {
        return new PageResponse<>(
                List.of(doctor),
                0,
                100,
                1,
                1,
                true,
                true,
                false,
                false
        );
    }
}
