package com.dentalclinic.ai.slot.service;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.slot.model.ChatSlotLookupRequest;
import com.dentalclinic.ai.slot.model.ChatSlotLookupResult;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.ai.slot.model.DoctorSlotAvailability;
import com.dentalclinic.ai.slot.model.SlotLookupTarget;
import com.dentalclinic.schedule.dto.CalendarSlotResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarSessionResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.dentalclinic.schedule.service.SlotService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AiSlotLookupService {

    private final SlotService slotService;
    private final SchedulingPolicy schedulingPolicy;

    public AiSlotLookupService(
            SlotService slotService,
            SchedulingPolicy schedulingPolicy
    ) {
        this.slotService = slotService;
        this.schedulingPolicy = schedulingPolicy;
    }

    public ChatSlotLookupResult lookup(ChatSlotLookupRequest request) {
        List<DoctorSlotAvailability> availability = request.targets().stream()
                .map(target -> lookupTarget(target, request))
                .toList();
        return new ChatSlotLookupResult(
                request.fromDate(),
                request.toDate(),
                request.sessionPreference(),
                availability
        );
    }

    private DoctorSlotAvailability lookupTarget(
            SlotLookupTarget target,
            ChatSlotLookupRequest request
    ) {
        DoctorCalendarResponse calendar = target.serviceId() == null
                ? slotService.getDoctorCalendar(
                        target.doctorId(),
                        request.fromDate(),
                        request.toDate()
                )
                : slotService.getCalendar(
                        target.doctorId(),
                        target.serviceId(),
                        request.fromDate(),
                        request.toDate()
                );
        List<ChatSlotSuggestion> slots = calendar.days().stream()
                .flatMap(day -> day.sessions().stream()
                        .filter(session -> matchesPreference(
                                session,
                                request.sessionPreference()
                        ))
                        .flatMap(session -> session.availableSlots().stream()
                                .map(availableCase -> toSuggestion(
                                        target,
                                        day.date(),
                                        session.session(),
                                        availableCase
                                ))))
                .toList();
        return new DoctorSlotAvailability(
                target.doctorId(),
                target.serviceId(),
                slots
        );
    }

    private ChatSlotSuggestion toSuggestion(
            SlotLookupTarget target,
            LocalDate date,
            ScheduleSession session,
            CalendarSlotResponse availableCase
    ) {
        return new ChatSlotSuggestion(
                target.doctorId(),
                target.serviceId(),
                date,
                toChatPreference(session),
                availableCase.queueNumber(),
                schedulingPolicy.sessionStart(session),
                schedulingPolicy.sessionEnd(session),
                availableCase.estimatedStartTime(),
                availableCase.startAt()
        );
    }

    private boolean matchesPreference(
            DoctorCalendarSessionResponse session,
            ChatSessionPreference preference
    ) {
        return preference == null || session.session().name().equals(preference.name());
    }

    private ChatSessionPreference toChatPreference(ScheduleSession session) {
        return ChatSessionPreference.valueOf(session.name());
    }
}
