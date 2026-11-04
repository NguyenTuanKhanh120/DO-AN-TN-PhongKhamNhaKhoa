package com.dentalclinic.ai.slot;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.slot.model.ChatSlotLookupRequest;
import com.dentalclinic.ai.slot.model.ChatSlotLookupResult;
import com.dentalclinic.ai.slot.model.SlotLookupTarget;
import com.dentalclinic.ai.slot.service.AiSlotLookupService;
import com.dentalclinic.schedule.dto.CalendarAvailabilityStatus;
import com.dentalclinic.schedule.dto.CalendarSlotResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarDayResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarSessionResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSlotLookupServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Mock
    private SlotService slotService;

    private AiSlotLookupService service;

    @BeforeEach
    void setUp() {
        service = new AiSlotLookupService(slotService);
    }

    @Test
    void shouldLookupSpecificDoctorServiceAndFilterAfternoonSlots() {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        when(slotService.getCalendar(doctorId, serviceId, MONDAY, MONDAY))
                .thenReturn(calendar(doctorId, serviceId, MONDAY));

        ChatSlotLookupResult result = service.lookup(new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(doctorId, serviceId)),
                MONDAY,
                null,
                ChatSessionPreference.AFTERNOON
        ));

        assertThat(result.fromDate()).isEqualTo(MONDAY);
        assertThat(result.toDate()).isEqualTo(MONDAY);
        assertThat(result.sessionPreference()).isEqualTo(ChatSessionPreference.AFTERNOON);
        assertThat(result.availability()).singleElement().satisfies(availability -> {
            assertThat(availability.doctorId()).isEqualTo(doctorId);
            assertThat(availability.serviceId()).isEqualTo(serviceId);
            assertThat(availability.slots()).extracting(slot -> slot.startAt().getHour())
                    .containsExactly(13, 14);
            assertThat(availability.slots()).allSatisfy(slot ->
                    assertThat(slot.session()).isEqualTo(ChatSessionPreference.AFTERNOON)
            );
        });
        verify(slotService).getCalendar(doctorId, serviceId, MONDAY, MONDAY);
        verify(slotService, never()).getDoctorCalendar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldLookupDoctorCalendarWithoutRequiringServiceSelection() {
        UUID doctorId = UUID.randomUUID();
        when(slotService.getDoctorCalendar(doctorId, MONDAY, MONDAY))
                .thenReturn(calendar(doctorId, null, MONDAY));

        ChatSlotLookupResult result = service.lookup(new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(doctorId, null)),
                MONDAY,
                MONDAY,
                ChatSessionPreference.MORNING
        ));

        assertThat(result.availability()).singleElement().satisfies(availability -> {
            assertThat(availability.serviceId()).isNull();
            assertThat(availability.slots()).singleElement().satisfies(slot -> {
                assertThat(slot.serviceId()).isNull();
                assertThat(slot.session()).isEqualTo(ChatSessionPreference.MORNING);
                assertThat(slot.startAt().getHour()).isEqualTo(8);
            });
        });
        verify(slotService).getDoctorCalendar(doctorId, MONDAY, MONDAY);
    }

    @Test
    void shouldLookupMultipleDoctorsAndDeduplicateTargets() {
        UUID serviceId = UUID.randomUUID();
        UUID firstDoctorId = UUID.randomUUID();
        UUID secondDoctorId = UUID.randomUUID();
        SlotLookupTarget first = new SlotLookupTarget(firstDoctorId, serviceId);
        SlotLookupTarget second = new SlotLookupTarget(secondDoctorId, serviceId);
        when(slotService.getCalendar(firstDoctorId, serviceId, MONDAY, MONDAY.plusDays(1)))
                .thenReturn(calendar(firstDoctorId, serviceId, MONDAY));
        when(slotService.getCalendar(secondDoctorId, serviceId, MONDAY, MONDAY.plusDays(1)))
                .thenReturn(calendar(secondDoctorId, serviceId, MONDAY));

        ChatSlotLookupResult result = service.lookup(new ChatSlotLookupRequest(
                List.of(first, second, first),
                MONDAY,
                MONDAY.plusDays(1),
                null
        ));

        assertThat(result.availability()).extracting(availability -> availability.doctorId())
                .containsExactly(firstDoctorId, secondDoctorId);
        assertThat(result.availability()).allSatisfy(availability ->
                assertThat(availability.slots()).hasSize(3)
        );
        verify(slotService).getCalendar(firstDoctorId, serviceId, MONDAY, MONDAY.plusDays(1));
        verify(slotService).getCalendar(secondDoctorId, serviceId, MONDAY, MONDAY.plusDays(1));
    }

    @Test
    void shouldKeepDoctorInResultWhenNoSlotIsAvailable() {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        when(slotService.getCalendar(doctorId, serviceId, MONDAY, MONDAY))
                .thenReturn(new DoctorCalendarResponse(
                        doctorId,
                        serviceId,
                        MONDAY,
                        MONDAY,
                        List.of()
                ));

        ChatSlotLookupResult result = service.lookup(new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(doctorId, serviceId)),
                MONDAY,
                MONDAY,
                null
        ));

        assertThat(result.availability()).singleElement().satisfies(availability ->
                assertThat(availability.slots()).isEmpty()
        );
    }

    @Test
    void shouldRejectInvalidLookupRequests() {
        assertThatThrownBy(() -> new ChatSlotLookupRequest(
                java.util.Collections.singletonList(null),
                MONDAY,
                MONDAY,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("target must not be null");
        assertThatThrownBy(() -> new ChatSlotLookupRequest(
                List.of(),
                MONDAY,
                MONDAY,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatSlotLookupRequest(
                List.of(new SlotLookupTarget(UUID.randomUUID(), null)),
                MONDAY,
                MONDAY.minusDays(1),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private DoctorCalendarResponse calendar(
            UUID doctorId,
            UUID serviceId,
            LocalDate date
    ) {
        return new DoctorCalendarResponse(
                doctorId,
                serviceId,
                date,
                date,
                List.of(new DoctorCalendarDayResponse(
                        date,
                        date.getDayOfWeek().getValue(),
                        CalendarAvailabilityStatus.AVAILABLE,
                        List.of(
                                session(ScheduleSession.MORNING, date, 8),
                                session(ScheduleSession.AFTERNOON, date, 13, 14)
                        )
                ))
        );
    }

    private DoctorCalendarSessionResponse session(
            ScheduleSession session,
            LocalDate date,
            int... hours
    ) {
        List<CalendarSlotResponse> slots = java.util.Arrays.stream(hours)
                .mapToObj(hour -> new CalendarSlotResponse(
                        OffsetDateTime.parse(date + "T" + "%02d:00:00+07:00".formatted(hour)),
                        OffsetDateTime.parse(date + "T" + "%02d:00:00+07:00".formatted(hour + 1))
                ))
                .toList();
        return new DoctorCalendarSessionResponse(
                session,
                CalendarAvailabilityStatus.AVAILABLE,
                slots.size(),
                slots
        );
    }
}
