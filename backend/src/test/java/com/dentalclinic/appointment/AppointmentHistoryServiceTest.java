package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.AppointmentHistoryResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentHistoryService;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentHistoryServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private AppointmentHistoryService appointmentHistoryService;

    @BeforeEach
    void setUp() {
        appointmentHistoryService = new AppointmentHistoryService(
                appointmentRepository,
                appointmentHistoryRepository
        );
    }

    @Test
    void shouldReturnChronologicalTimelineWithActorAndCopiedDetails() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        User patient = user("Bệnh nhân Nguyễn", Role.PATIENT);
        AppointmentHistory booked = history(
                appointment,
                AppointmentAction.BOOK,
                patient,
                null,
                "2026-10-12T01:00:00Z"
        );
        AppointmentHistory cancelled = history(
                appointment,
                AppointmentAction.CANCEL,
                patient,
                "PATIENT_REQUEST",
                "2026-10-12T02:00:00Z"
        );
        when(appointmentRepository.existsById(appointmentId)).thenReturn(true);
        when(appointmentHistoryRepository.findTimelineByAppointmentId(appointmentId))
                .thenReturn(List.of(booked, cancelled));

        List<AppointmentHistoryResponse> result = appointmentHistoryService
                .getHistory(appointmentId);

        assertThat(result).extracting(AppointmentHistoryResponse::action)
                .containsExactly(AppointmentAction.BOOK, AppointmentAction.CANCEL);
        assertThat(result.getFirst().appointmentId()).isEqualTo(appointmentId);
        assertThat(result.getFirst().changedByUserId()).isEqualTo(patient.getId());
        assertThat(result.getFirst().changedByFullName()).isEqualTo("Bệnh nhân Nguyễn");
        assertThat(result.getFirst().changedByRole()).isEqualTo(Role.PATIENT);
        assertThat(result.get(1).reason()).isEqualTo("PATIENT_REQUEST");
        ((ObjectNode) result.getFirst().details()).put("new_status", "CHANGED");
        assertThat(booked.getDetails().path("new_status").asText()).isEqualTo("BOOKED");
    }

    @Test
    void shouldSupportSystemHistoryWithoutChangedByUser() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        AppointmentHistory history = history(
                appointment,
                AppointmentAction.NO_SHOW,
                null,
                null,
                "2026-10-12T03:00:00Z"
        );
        history.setDetails(null);
        when(appointmentRepository.existsById(appointmentId)).thenReturn(true);
        when(appointmentHistoryRepository.findTimelineByAppointmentId(appointmentId))
                .thenReturn(List.of(history));

        AppointmentHistoryResponse response = appointmentHistoryService
                .getHistory(appointmentId)
                .getFirst();

        assertThat(response.changedByUserId()).isNull();
        assertThat(response.changedByFullName()).isNull();
        assertThat(response.changedByRole()).isNull();
        assertThat(response.details().isObject()).isTrue();
        assertThat(response.details()).isEmpty();
    }

    @Test
    void shouldReturnEmptyTimelineForExistingAppointmentWithoutHistory() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.existsById(appointmentId)).thenReturn(true);
        when(appointmentHistoryRepository.findTimelineByAppointmentId(appointmentId))
                .thenReturn(List.of());

        assertThat(appointmentHistoryService.getHistory(appointmentId)).isEmpty();
    }

    @Test
    void shouldRejectUnknownAppointment() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.existsById(appointmentId)).thenReturn(false);

        assertThatThrownBy(() -> appointmentHistoryService.getHistory(appointmentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");

        verify(appointmentHistoryRepository, never())
                .findTimelineByAppointmentId(appointmentId);
    }

    private User user(String fullName, Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(fullName);
        user.setRole(role);
        return user;
    }

    private AppointmentHistory history(
            Appointment appointment,
            AppointmentAction action,
            User changedBy,
            String reason,
            String createdAt
    ) {
        AppointmentHistory history = new AppointmentHistory();
        history.setId(UUID.randomUUID());
        history.setAppointment(appointment);
        history.setAction(action);
        history.setChangedBy(changedBy);
        history.setReason(reason);
        history.setDetails(JsonNodeFactory.instance.objectNode()
                .put("old_status", "NONE")
                .put("new_status", action == AppointmentAction.BOOK
                        ? "BOOKED"
                        : action.name()));
        history.setCreatedAt(Instant.parse(createdAt));
        return history;
    }
}
