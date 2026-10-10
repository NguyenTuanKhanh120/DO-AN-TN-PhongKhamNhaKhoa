package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.AppointmentHistoryResponse;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AppointmentHistoryService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;

    public AppointmentHistoryService(
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentHistoryResponse> getHistory(UUID appointmentId) {
        if (!appointmentRepository.existsById(appointmentId)) {
            throw new NotFoundException("Không tìm thấy lịch hẹn");
        }
        return appointmentHistoryRepository.findTimelineByAppointmentId(appointmentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AppointmentHistoryResponse toResponse(AppointmentHistory history) {
        User changedBy = history.getChangedBy();
        return new AppointmentHistoryResponse(
                history.getId(),
                history.getAppointment().getId(),
                history.getAction(),
                changedBy == null ? null : changedBy.getId(),
                changedBy == null ? null : changedBy.getFullName(),
                changedBy == null ? null : changedBy.getRole(),
                history.getReason(),
                copyDetails(history.getDetails()),
                history.getCreatedAt()
        );
    }

    private JsonNode copyDetails(JsonNode details) {
        return details == null
                ? JsonNodeFactory.instance.objectNode()
                : details.deepCopy();
    }
}
