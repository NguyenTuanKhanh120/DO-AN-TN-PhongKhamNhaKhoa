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
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.common.util.ClinicTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(
                Instant.parse("2026-11-01T00:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh")
        ));
        service = new AiSlotLookupService(slotService, new SchedulingPolicy(timeProvider));
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
            assertThat(availability.slots()).extracting(slot -> slot.queueNumber())
                    .containsExactly(1, 2);
            assertThat(availability.slots()).extracting(slot -> slot.estimatedStartTime())
                    .containsExactly(LocalTime.of(14, 0), LocalTime.of(15, 30));
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
            assertThat(availability.slots()).hasSize(2).first().satisfies(slot -> {
                assertThat(slot.serviceId()).isNull();
                assertThat(slot.session()).isEqualTo(ChatSessionPreference.MORNING);
                assertThat(slot.queueNumber()).isEqualTo(1);
                assertThat(slot.estimatedStartTime()).isEqualTo(LocalTime.of(8, 0));
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
                assertThat(availability.slots()).hasSize(4)
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
                                session(ScheduleSession.MORNING, date),
                                session(ScheduleSession.AFTERNOON, date)
                        )
                ))
        );
    }

    private DoctorCalendarSessionResponse session(
            ScheduleSession session,
            LocalDate date
    ) {
        LocalTime sessionEnd = session == ScheduleSession.MORNING
                ? LocalTime.of(11, 0)
                : LocalTime.of(17, 0);
        List<CalendarSlotResponse> slots = java.util.stream.IntStream.rangeClosed(1, 2)
                .mapToObj(queueNumber -> {
                    LocalTime estimatedStartTime = switch (session) {
                        case MORNING -> queueNumber == 1
                                ? LocalTime.of(8, 0)
                                : LocalTime.of(9, 30);
                        case AFTERNOON -> queueNumber == 1
                                ? LocalTime.of(14, 0)
                                : LocalTime.of(15, 30);
                    };
                    return new CalendarSlotResponse(
                            queueNumber,
                            estimatedStartTime,
                            OffsetDateTime.parse(date + "T" + estimatedStartTime + ":00+07:00"),
                            OffsetDateTime.parse(date + "T" + sessionEnd + ":00+07:00")
                    );
                })
                .toList();
        return new DoctorCalendarSessionResponse(
                session,
                CalendarAvailabilityStatus.AVAILABLE,
                slots.size(),
                slots
        );
    }
}
