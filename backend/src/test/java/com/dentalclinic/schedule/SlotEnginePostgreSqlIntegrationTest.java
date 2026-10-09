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
@Import(SlotEnginePostgreSqlIntegrationTest.FixedClockConfiguration.class)
class SlotEnginePostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");

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

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "slot-engine-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.slot-duration-minutes", () -> 60);
        registry.add("clinic.appointment.booking-min-advance-minutes", () -> 180);
    }

    @Test
    void shouldCalculateAvailableSlotsFromPostgreSqlData() throws Exception {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "phase11-doctor-" + marker + "@example.com",
                "Bác sĩ Slot Engine",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE11-" + marker);
        DentalService dentalService = saveDentalService("Dịch vụ Phase 11 " + marker, true);
        DoctorService mapping = saveMapping(doctor, dentalService);
        saveSchedule(doctor, ScheduleSession.MORNING, "07:00", "12:00");
        saveSchedule(doctor, ScheduleSession.AFTERNOON, "13:00", "18:00");

        User patientUser = saveUser(
                "phase11-patient-" + marker + "@example.com",
                "Bệnh nhân Slot Engine",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        saveAppointment(patient, mapping, LocalTime.of(14, 0), AppointmentStatus.BOOKED);
        saveAppointment(patient, mapping, LocalTime.of(15, 0), AppointmentStatus.CANCELLED);

        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctor.getId())
                        .param("serviceId", dentalService.getId().toString())
                        .param("date", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].startTime").value("13:00:00"))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-10-12T13:00:00+07:00"))
                .andExpect(jsonPath("$.data[1].startTime").value("15:00:00"))
                .andExpect(jsonPath("$.data[2].startTime").value("16:00:00"))
                .andExpect(jsonPath("$.data[3].startTime").value("17:00:00"));

        assertThat(appointmentRepository.findOccupiedStartTimes(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(1), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        )).containsExactly(toInstant(MONDAY, LocalTime.of(14, 0)));

        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctor.getId())
                        .param("serviceId", dentalService.getId().toString())
                        .param("date", MONDAY.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        DentalService unmappedService = saveDentalService("Không mapping " + marker, true);
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctor.getId())
                        .param("serviceId", unmappedService.getId().toString())
                        .param("date", MONDAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        dentalService.setActive(false);
        dentalServiceRepository.saveAndFlush(dentalService);
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctor.getId())
                        .param("serviceId", dentalService.getId().toString())
                        .param("date", MONDAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        dentalService.setActive(true);
        dentalServiceRepository.saveAndFlush(dentalService);
        doctor.setWorkingStatus(DoctorStatus.ON_LEAVE);
        doctorRepository.saveAndFlush(doctor);
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctor.getId())
                        .param("serviceId", dentalService.getId().toString())
                        .param("date", MONDAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash("not-used-by-public-slot-test");
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
        doctor.setBio("Bác sĩ kiểm thử PHASE 11");
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private DentalService saveDentalService(String name, boolean active) {
        DentalService dentalService = new DentalService();
        dentalService.setName(name);
        dentalService.setDescription("Dịch vụ kiểm thử Slot Engine");
        dentalService.setPrice(new BigDecimal("300000"));
        dentalService.setActive(active);
        return dentalServiceRepository.saveAndFlush(dentalService);
    }

    private DoctorService saveMapping(Doctor doctor, DentalService dentalService) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        return doctorServiceRepository.saveAndFlush(mapping);
    }

    private void saveSchedule(
            Doctor doctor,
            ScheduleSession session,
            String startTime,
            String endTime
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) 1);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(startTime));
        schedule.setEndTime(LocalTime.parse(endTime));
        doctorScheduleRepository.saveAndFlush(schedule);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private void saveAppointment(
            Patient patient,
            DoctorService mapping,
            LocalTime startTime,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(toInstant(MONDAY, startTime));
        appointment.setStatus(status);
        appointmentRepository.saveAndFlush(appointment);
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
