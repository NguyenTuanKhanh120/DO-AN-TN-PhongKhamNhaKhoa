package com.dentalclinic.ai.orchestration.service;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatQueryPlan;
import com.dentalclinic.ai.orchestration.model.ChatQueryRoute;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DatabaseChatResponseService {

    private static final int MAX_SLOT_LINES = 12;
    private static final DateTimeFormatter VIETNAMESE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String CLINIC_INFORMATION_ANSWER = "Nha Khoa An Tâm hỗ trợ tra cứu "
            + "dịch vụ, bác sĩ, giá dịch vụ và ca khám còn trống trên hệ thống.";
    private static final String APPOINTMENT_GUIDANCE_ANSWER = "Để đặt lịch, bạn chọn dịch vụ, "
            + "bác sĩ, ngày khám, buổi khám và ca còn trống, sau đó kiểm tra lại thông tin "
            + "và xác nhận đặt lịch.";

    private final ChatQueryPlanner queryPlanner;

    public DatabaseChatResponseService(ChatQueryPlanner queryPlanner) {
        this.queryPlanner = queryPlanner;
    }

    public Optional<String> respond(
            ChatContextResolution resolution,
            ChatGroundingResult grounding
    ) {
        ChatQueryPlan plan = queryPlanner.plan(resolution);
        if (plan.route() != ChatQueryRoute.DB_ONLY) {
            return Optional.empty();
        }

        List<String> sections = new ArrayList<>();
        List<ChatIntent> intents = plan.intents();
        if (intents.contains(ChatIntent.SERVICE_CATALOG)) {
            sections.add(serviceCatalogAnswer(grounding));
        }
        if (intents.contains(ChatIntent.SERVICE_AVAILABLE_DOCTORS)
                || intents.contains(ChatIntent.DOCTOR_RECOMMENDATION)) {
            sections.add(availableDoctorsAnswer(grounding));
        }
        if (intents.contains(ChatIntent.DOCTOR_AVAILABLE_SLOTS)) {
            sections.add(doctorAvailabilityAnswer(grounding));
        } else if (intents.contains(ChatIntent.AVAILABLE_SLOTS)) {
            sections.add(availableSlotsAnswer(grounding));
        }
        if (intents.contains(ChatIntent.CLINIC_INFORMATION)) {
            sections.add(CLINIC_INFORMATION_ANSWER);
        }
        if (intents.contains(ChatIntent.APPOINTMENT_GUIDANCE)) {
            sections.add(APPOINTMENT_GUIDANCE_ANSWER);
        }

        String answer = sections.stream()
                .filter(section -> section != null && !section.isBlank())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        return answer.isBlank() ? Optional.empty() : Optional.of(answer);
    }

    private String serviceCatalogAnswer(ChatGroundingResult grounding) {
        if (grounding.services().isEmpty()) {
            return "Hiện tại phòng khám chưa có dịch vụ nha khoa đang hoạt động.";
        }
        String verifiedPrice = verifiedServicePriceAnswer(grounding);
        if (verifiedPrice != null) {
            return verifiedPrice;
        }
        String serviceNames = grounding.services().stream()
                .map(service -> "- " + service.name())
                .collect(Collectors.joining("\n"));
        return "Hiện tại Nha Khoa An Tâm có các dịch vụ đang hoạt động:\n" + serviceNames
                + "\nBạn có thể xem thông tin và mức giá ở các thẻ dịch vụ bên dưới.";
    }

    private String verifiedServicePriceAnswer(ChatGroundingResult grounding) {
        if (grounding.context().selectedService() == null
                || grounding.services().size() != 1) {
            return null;
        }
        DentalServiceResponse service = grounding.services().getFirst();
        String formattedPrice = NumberFormat
                .getNumberInstance(Locale.forLanguageTag("vi-VN"))
                .format(service.price());
        return "Dịch vụ %s hiện có giá %s đ."
                .formatted(service.name(), formattedPrice);
    }

    private String availableDoctorsAnswer(ChatGroundingResult grounding) {
        String serviceName = selectedServiceName(grounding);
        if (grounding.doctors().isEmpty()) {
            return serviceName == null
                    ? "Hiện tại chưa có bác sĩ đang hoạt động phù hợp với yêu cầu này."
                    : "Hiện tại chưa có bác sĩ đang hoạt động được phân công dịch vụ %s."
                            .formatted(serviceName);
        }
        String header = serviceName == null
                ? "Các bác sĩ đang hoạt động phù hợp:"
                : "Các bác sĩ đang hoạt động được phân công dịch vụ %s:"
                        .formatted(serviceName);
        String doctors = grounding.doctors().stream()
                .sorted(Comparator.comparing(DoctorResponse::fullName))
                .map(this::formatDoctor)
                .collect(Collectors.joining("\n"));
        return header + "\n" + doctors;
    }

    private String formatDoctor(DoctorResponse doctor) {
        if (doctor.specialization() == null || doctor.specialization().isBlank()) {
            return "- " + doctor.fullName();
        }
        return "- %s — %s".formatted(doctor.fullName(), doctor.specialization());
    }

    private String doctorAvailabilityAnswer(ChatGroundingResult grounding) {
        String doctorName = grounding.context().selectedDoctor() == null
                || grounding.context().selectedDoctor().name() == null
                ? "Bác sĩ đã chọn"
                : grounding.context().selectedDoctor().name();
        var dateRange = grounding.context().dateRange();
        if (grounding.slots().isEmpty()) {
            if (dateRange != null && dateRange.isSingleDay()) {
                return "%s không còn ca trống vào ngày %s."
                        .formatted(doctorName, dateRange.from().format(VIETNAMESE_DATE_FORMAT));
            }
            if (dateRange != null) {
                return "%s không còn ca trống từ ngày %s đến ngày %s."
                        .formatted(
                                doctorName,
                                dateRange.from().format(VIETNAMESE_DATE_FORMAT),
                                dateRange.to().format(VIETNAMESE_DATE_FORMAT)
                        );
            }
            return doctorName + " hiện không còn ca trống phù hợp.";
        }

        List<ChatSlotSuggestion> slots = sortedSlots(grounding.slots());
        boolean singleDay = dateRange != null && dateRange.isSingleDay();
        String header = singleDay
                ? "%s còn các ca trống ngày %s:"
                        .formatted(doctorName, dateRange.from().format(VIETNAMESE_DATE_FORMAT))
                : doctorName + " còn các ca trống sau:";
        return header + "\n" + formatSlotLines(slots, singleDay, Map.of());
    }

    private String availableSlotsAnswer(ChatGroundingResult grounding) {
        if (grounding.slots().isEmpty()) {
            return "Hiện không còn ca khám trống phù hợp với yêu cầu của bạn.";
        }
        Map<UUID, String> doctorNames = new LinkedHashMap<>();
        grounding.doctors().forEach(doctor -> doctorNames.put(doctor.id(), doctor.fullName()));
        List<ChatSlotSuggestion> slots = sortedSlots(grounding.slots());
        return "Các ca khám còn trống phù hợp:\n"
                + formatSlotLines(slots, false, doctorNames);
    }

    private List<ChatSlotSuggestion> sortedSlots(List<ChatSlotSuggestion> slots) {
        return slots.stream()
                .sorted(Comparator
                        .comparing(ChatSlotSuggestion::date)
                        .thenComparing(slot -> slot.session().ordinal())
                        .thenComparingInt(ChatSlotSuggestion::queueNumber)
                        .thenComparing(ChatSlotSuggestion::doctorId))
                .toList();
    }

    private String formatSlotLines(
            List<ChatSlotSuggestion> slots,
            boolean omitDate,
            Map<UUID, String> doctorNames
    ) {
        String details = slots.stream()
                .limit(MAX_SLOT_LINES)
                .map(slot -> formatSlot(slot, omitDate, doctorNames.get(slot.doctorId())))
                .collect(Collectors.joining("\n"));
        if (slots.size() <= MAX_SLOT_LINES) {
            return details;
        }
        return details + "\n- Và %d ca trống khác.".formatted(slots.size() - MAX_SLOT_LINES);
    }

    private String formatSlot(
            ChatSlotSuggestion slot,
            boolean omitDate,
            String doctorName
    ) {
        String session = switch (slot.session()) {
            case MORNING -> "Buổi sáng";
            case AFTERNOON -> "Buổi chiều";
        };
        String doctor = doctorName == null || doctorName.isBlank()
                ? ""
                : doctorName + " · ";
        String date = omitDate
                ? ""
                : slot.date().format(VIETNAMESE_DATE_FORMAT) + " · ";
        return "- %s%s%s, Ca %d — dự kiến %s".formatted(
                doctor,
                date,
                session,
                slot.queueNumber(),
                slot.estimatedStartTime().format(TIME_FORMAT)
        );
    }

    private String selectedServiceName(ChatGroundingResult grounding) {
        if (grounding.context().selectedService() != null
                && grounding.context().selectedService().name() != null) {
            return grounding.context().selectedService().name();
        }
        return grounding.services().size() == 1
                ? grounding.services().getFirst().name()
                : null;
    }
}
