package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.dto.DoctorScheduleRequest;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.schedule.service.DoctorScheduleService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
@Transactional
@Import(DoctorScheduleAppointmentConflictPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class DoctorScheduleAppointmentConflictPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-19T00:00:00Z");
    private static final LocalDate FUTURE_MONDAY = LocalDate.of(2026, 10, 26);

    @Autowired
    private DoctorScheduleService doctorScheduleService;

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
        registry.add("security.jwt.issuer", () -> "doctor-schedule-conflict-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
        registry.add("clinic.appointment.slot-duration-minutes", () -> 60);
    }

    @Test
    void shouldKeepWeeklyScheduleWhenFutureBookedAppointmentWouldBeLost() {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser("schedule-doctor-" + marker + "@example.com", Role.DOCTOR);
        Doctor doctor = saveDoctor(doctorUser, "SCHEDULE-" + marker);
        DentalService service = saveService("Schedule conflict " + marker);
        DoctorService mapping = saveMapping(doctor, service);
        DoctorSchedule schedule = saveSchedule(doctor);
        User patientUser = saveUser("schedule-patient-" + marker + "@example.com", Role.PATIENT);
        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient.setGender(Gender.UNSPECIFIED);
        patient = patientRepository.saveAndFlush(patient);
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(FUTURE_MONDAY.atTime(8, 0).atZone(CLINIC_ZONE).toInstant());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointmentRepository.saveAndFlush(appointment);

        DoctorScheduleRequest shortened = new DoctorScheduleRequest(
                1,
                ScheduleSession.MORNING,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0)
        );
        assertThatThrownBy(() -> doctorScheduleService.updateSchedule(
                doctor.getId(),
                schedule.getId(),
                shortened
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 lịch hẹn tương lai");

        assertThatThrownBy(() -> doctorScheduleService.deleteSchedule(
                doctor.getId(),
                schedule.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 lịch hẹn tương lai");

        DoctorSchedule unchanged = doctorScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(unchanged.getEndTime()).isEqualTo(LocalTime.of(11, 0));
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-schedule-conflict-test");
        user.setFullName(role == Role.DOCTOR ? "Bác sĩ Schedule" : "Bệnh nhân Schedule");
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

    private DoctorSchedule saveSchedule(Doctor doctor) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) 1);
        schedule.setSession(ScheduleSession.MORNING);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(11, 0));
        return doctorScheduleRepository.saveAndFlush(schedule);
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
