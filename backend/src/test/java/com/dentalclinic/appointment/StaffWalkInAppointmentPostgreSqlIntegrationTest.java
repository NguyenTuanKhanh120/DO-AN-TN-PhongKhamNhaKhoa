package com.dentalclinic.appointment;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.entity.BookingSource;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.auth.security.UserPrincipal;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
@Import(StaffWalkInAppointmentPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class StaffWalkInAppointmentPostgreSqlIntegrationTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DentalServiceRepository dentalServiceRepository;

    @Autowired
    private DoctorServiceRepository doctorServiceRepository;

    @Autowired
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Test
    void shouldPersistTodayWalkInForUserlessPatientAndAuditStaffCreator() throws Exception {
        Fixture fixture = fixture();

        String body = mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .with(user(UserPrincipal.from(fixture.staff())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(fixture, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.patientId")
                        .value(fixture.patient().getId().toString()))
                .andExpect(jsonPath("$.data.appointmentDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.session").value("MORNING"))
                .andExpect(jsonPath("$.data.queueNumber").value(2))
                .andExpect(jsonPath("$.data.estimatedStartAt")
                        .value("2026-10-12T09:30:00+07:00"))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.bookingSource").value("WALK_IN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        UUID appointmentId = UUID.fromString(response.path("data").path("id").asText());
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(appointment.getPatient().getUser()).isNull();
        assertThat(appointment.getAppointmentDate()).isEqualTo(TODAY);
        assertThat(appointment.getSession()).isEqualTo(ScheduleSession.MORNING);
        assertThat(appointment.getQueueNumber()).isEqualTo((short) 2);
        assertThat(appointment.getScheduledStartAt())
                .isEqualTo(Instant.parse("2026-10-12T02:30:00Z"));
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getBookingSource()).isEqualTo(BookingSource.WALK_IN);

        List<AppointmentHistory> history = appointmentHistoryRepository
                .findTimelineByAppointmentId(appointmentId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getAction()).isEqualTo(AppointmentAction.BOOK);
        assertThat(history.getFirst().getChangedBy().getId()).isEqualTo(fixture.staff().getId());
        assertThat(history.getFirst().getDetails().path("booking_source").asText())
                .isEqualTo("WALK_IN");
        assertThat(history.getFirst().getDetails().path("created_by_role").asText())
                .isEqualTo("STAFF");
    }

    @Test
    void shouldRecheckAndRejectAnOccupiedCase() throws Exception {
        Fixture fixture = fixture();
        Patient otherPatient = savePatient("Bệnh nhân giữ ca");
        saveAppointment(otherPatient, fixture.mapping(), (short) 2);

        mockMvc.perform(post("/api/staff/appointments/walk-in")
                        .with(user(UserPrincipal.from(fixture.staff())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(fixture, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("Ca khám vừa được đặt. Vui lòng chọn ca khác."));
    }

    private Fixture fixture() {
        String marker = UUID.randomUUID().toString();
        User staff = saveUser(
                "walk-in-staff-" + marker + "@example.com",
                "Nhân viên WALK_IN",
                Role.STAFF
        );
        Doctor doctor = saveDoctor(saveUser(
                "walk-in-doctor-" + marker + "@example.com",
                "Bác sĩ WALK_IN",
                Role.DOCTOR
        ), "WALK-IN-" + marker);
        DentalService service = saveService("Dịch vụ WALK_IN " + marker);
        DoctorService mapping = saveMapping(doctor, service);
        saveSchedule(doctor);
        Patient patient = savePatient("Bệnh nhân không tài khoản " + marker);
        return new Fixture(staff, patient, doctor, service, mapping);
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-walk-in-test");
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Patient savePatient(String fullName) {
        Patient patient = new Patient();
        patient.setUser(null);
        patient.setFullName(fullName);
        patient.setGender(Gender.UNSPECIFIED);
        patient.setPhone("090" + Math.abs(fullName.hashCode()));
        return patientRepository.saveAndFlush(patient);
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
        service.setDescription("Dịch vụ kiểm thử tạo WALK_IN");
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

    private void saveSchedule(Doctor doctor) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) TODAY.getDayOfWeek().getValue());
        schedule.setSession(ScheduleSession.MORNING);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(11, 0));
        doctorScheduleRepository.saveAndFlush(schedule);
    }

    private void saveAppointment(
            Patient patient,
            DoctorService mapping,
            short queueNumber
    ) {
        LocalTime estimatedStart = queueNumber == 1
                ? LocalTime.of(8, 0)
                : LocalTime.of(9, 30);
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setAppointmentDate(TODAY);
        appointment.setSession(ScheduleSession.MORNING);
        appointment.setQueueNumber(queueNumber);
        appointment.setScheduledStartAt(TODAY.atTime(estimatedStart).atZone(CLINIC_ZONE).toInstant());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setBookingSource(BookingSource.ONLINE);
        appointmentRepository.saveAndFlush(appointment);
    }

    private String requestBody(Fixture fixture, int queueNumber) {
        return """
                {
                  "patientId": "%s",
                  "doctorId": "%s",
                  "serviceId": "%s",
                  "session": "MORNING",
                  "queueNumber": %d
                }
                """.formatted(
                fixture.patient().getId(),
                fixture.doctor().getId(),
                fixture.service().getId(),
                queueNumber
        );
    }

    private record Fixture(
            User staff,
            Patient patient,
            Doctor doctor,
            DentalService service,
            DoctorService mapping
    ) {
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
