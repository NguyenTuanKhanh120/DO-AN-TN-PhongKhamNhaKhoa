package com.dentalclinic.appointment.repository;

public interface AppointmentDailySummary {

    long getPatientsVisitedToday();

    long getTodayAppointments();

    long getCompletedToday();

    long getCancelledToday();

    long getNoShowToday();
}
