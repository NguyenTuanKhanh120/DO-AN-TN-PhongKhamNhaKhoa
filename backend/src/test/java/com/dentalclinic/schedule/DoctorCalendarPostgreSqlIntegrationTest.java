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
import com.dentalclinic.schedule.entity.DoctorDayOff;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorDayOffRepository;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
@Import(DoctorCalendarPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class DoctorCalendarPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-19T00:00:00Z");

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
    private DoctorDayOffRepository doctorDayOffRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-calendar-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.slot-duration-minutes", () -> 60);
        registry.add("clinic.appointment.booking-min-advance-minutes", () -> 120);
    }

    @Test
    void shouldReturnThirtyDayCalendarWithoutPatientInformation() throws Exception {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "calendar-doctor-" + marker + "@example.com",
                "Bác sĩ Calendar",
                Role.DOCTOR
        );
        User admin = saveUser(
                "calendar-admin-" + marker + "@example.com",
                "Quản trị viên",
                Role.ADMIN
        );
        Doctor doctor = saveDoctor(doctorUser, "CALENDAR-" + marker);
        DentalService service = saveService("Dịch vụ Calendar " + marker);
        DoctorService mapping = saveMapping(doctor, service);
        saveDefaultSchedules(doctor);
        saveMorningDayOff(doctor, admin, MONDAY.plusDays(1));

        User patientUser = saveUser(
                "calendar-patient-" + marker + "@example.com",
                "Bệnh nhân bí mật",
                Role.PATIENT
        );
        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient.setGender(Gender.UNSPECIFIED);
        patient = patientRepository.saveAndFlush(patient);
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(toInstant(MONDAY, LocalTime.of(13, 0)));
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointmentRepository.saveAndFlush(appointment);

        MvcResult result = mockMvc.perform(get("/api/doctors/{doctorId}/calendar", doctor.getId())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromDate").value(MONDAY.toString()))
                .andExpect(jsonPath("$.data.toDate").value(MONDAY.plusDays(29).toString()))
                .andExpect(jsonPath("$.data.days.length()").value(30))
                .andExpect(jsonPath("$.data.days[0].sessions[0].availableSlotCount").value(2))
                .andExpect(jsonPath("$.data.days[0].sessions[1].availableSlotCount").value(3))
                .andExpect(jsonPath("$.data.days[1].sessions[0].status").value("DAY_OFF"))
                .andExpect(jsonPath("$.data.days[6].status").value("NOT_WORKING"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody)
                .doesNotContain("Bệnh nhân bí mật")
                .doesNotContain(patient.getId().toString())
                .doesNotContain(patientUser.getId().toString());
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-calendar-test");
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
        service.setDescription("Dịch vụ kiểm thử calendar");
        service.setPrice(new BigDecimal("200000"));
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

    private void saveDefaultSchedules(Doctor doctor) {
        for (short day = 1; day <= 6; day++) {
            saveSchedule(doctor, day, ScheduleSession.MORNING, "08:00", "11:00");
            saveSchedule(doctor, day, ScheduleSession.AFTERNOON, "13:00", "17:00");
        }
    }

    private void saveSchedule(
            Doctor doctor,
            short day,
            ScheduleSession session,
            String start,
            String end
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek(day);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(start));
        schedule.setEndTime(LocalTime.parse(end));
        doctorScheduleRepository.save(schedule);
    }

    private void saveMorningDayOff(Doctor doctor, User admin, LocalDate date) {
        DoctorDayOff dayOff = new DoctorDayOff();
        dayOff.setDoctor(doctor);
        dayOff.setOffDate(date);
        dayOff.setSession(ScheduleSession.MORNING);
        dayOff.setReason("Nghỉ kiểm thử");
        dayOff.setCreatedBy(admin);
        doctorDayOffRepository.saveAndFlush(dayOff);
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
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
