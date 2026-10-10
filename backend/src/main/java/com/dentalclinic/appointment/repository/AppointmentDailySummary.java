package com.dentalclinic.appointment.repository;

public interface AppointmentDailySummary {

    long getTodayAppointments();

    long getCompletedToday();

    long getCancelledToday();

    long getNoShowToday();
}
