package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
@Import(StaffWalkInAvailabilityPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class StaffWalkInAvailabilityPostgreSqlIntegrationTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DentalServiceRepository dentalServiceRepository;

    @Autowired
    private DoctorServiceRepository doctorServiceRepository;

    @Autowired
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    void shouldExposeOnlyUnoccupiedCasesForTodayAfterOnlineCutoff() throws Exception {
        String marker = UUID.randomUUID().toString();
        Doctor doctor = saveDoctor(saveUser(
                "walk-in-doctor-" + marker + "@example.com",
                "Bác sĩ Walk In",
                Role.DOCTOR
        ), "WALK-IN-" + marker);
        DentalService service = saveService("Dịch vụ Walk In " + marker);
        DoctorService mapping = saveMapping(doctor, service);
        saveSchedule(doctor, ScheduleSession.MORNING, LocalTime.of(8, 0), LocalTime.of(11, 0));
        saveSchedule(doctor, ScheduleSession.AFTERNOON, LocalTime.of(14, 0), LocalTime.of(17, 0));

        Patient patient = savePatient(saveUser(
                "walk-in-patient-" + marker + "@example.com",
                "Bệnh nhân Walk In",
                Role.PATIENT
        ));
        saveAppointment(patient, mapping, ScheduleSession.MORNING, (short) 1, AppointmentStatus.BOOKED);
        saveAppointment(patient, mapping, ScheduleSession.AFTERNOON, (short) 1, AppointmentStatus.CANCELLED);

        mockMvc.perform(get("/api/staff/appointments/walk-in/availability")
                        .with(user("staff@example.com").roles("STAFF"))
                        .param("doctorId", doctor.getId().toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.sessions[0].session").value("MORNING"))
                .andExpect(jsonPath("$.data.sessions[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.sessions[0].availableCaseCount").value(1))
                .andExpect(jsonPath("$.data.sessions[0].cases[0].queueNumber").value(1))
                .andExpect(jsonPath("$.data.sessions[0].cases[0].available").value(false))
                .andExpect(jsonPath("$.data.sessions[0].cases[1].queueNumber").value(2))
                .andExpect(jsonPath("$.data.sessions[0].cases[1].estimatedStartTime")
                        .value("09:30:00"))
                .andExpect(jsonPath("$.data.sessions[0].cases[1].available").value(true))
                .andExpect(jsonPath("$.data.sessions[1].session").value("AFTERNOON"))
                .andExpect(jsonPath("$.data.sessions[1].availableCaseCount").value(2))
                .andExpect(jsonPath("$.data.sessions[1].cases[0].available").value(true));
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-walk-in-availability-test");
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Doctor saveDoctor(User user, String licenseNumber) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber(licenseNumber);
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử WALK_IN availability");
        service.setPrice(new BigDecimal("250000"));
        service.setActive(true);
        return dentalServiceRepository.saveAndFlush(service);
    }

    private DoctorService saveMapping(Doctor doctor, DentalService service) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);
        return doctorServiceRepository.saveAndFlush(mapping);
    }

    private void saveSchedule(
            Doctor doctor,
            ScheduleSession session,
            LocalTime startTime,
            LocalTime endTime
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) TODAY.getDayOfWeek().getValue());
        schedule.setSession(session);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        doctorScheduleRepository.saveAndFlush(schedule);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFullName(user.getFullName());
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private void saveAppointment(
            Patient patient,
            DoctorService mapping,
            ScheduleSession session,
            short queueNumber,
            AppointmentStatus status
    ) {
        LocalTime estimatedStart = session == ScheduleSession.MORNING
                ? (queueNumber == 1 ? LocalTime.of(8, 0) : LocalTime.of(9, 30))
                : (queueNumber == 1 ? LocalTime.of(14, 0) : LocalTime.of(15, 30));
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setAppointmentDate(TODAY);
        appointment.setSession(session);
        appointment.setQueueNumber(queueNumber);
        appointment.setScheduledStartAt(TODAY.atTime(estimatedStart).atZone(CLINIC_ZONE).toInstant());
        appointment.setStatus(status);
        appointmentRepository.saveAndFlush(appointment);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClinicClock() {
            return Clock.fixed(FIXED_NOW, CLINIC_ZONE);
        }
    }
}
