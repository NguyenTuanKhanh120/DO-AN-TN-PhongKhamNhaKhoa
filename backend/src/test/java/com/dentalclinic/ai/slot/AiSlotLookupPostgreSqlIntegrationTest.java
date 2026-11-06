package com.dentalclinic.ai.slot;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.slot.model.ChatSlotLookupRequest;
import com.dentalclinic.ai.slot.model.ChatSlotLookupResult;
import com.dentalclinic.ai.slot.model.SlotLookupTarget;
import com.dentalclinic.ai.slot.service.AiSlotLookupService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("development")
@Transactional
@Import(AiSlotLookupPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class AiSlotLookupPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final Instant FIXED_NOW = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    private AiSlotLookupService slotLookupService;

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
        registry.add("security.jwt.issuer", () -> "ai-slot-lookup-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @Test
    void shouldLookupDateRangeSessionDoctorAndMultiDoctorSlotsWithoutBooking() {
        String marker = UUID.randomUUID().toString();
        Doctor firstDoctor = saveDoctor(marker + "-first");
        Doctor secondDoctor = saveDoctor(marker + "-second");
        DentalService service = saveDentalService("AI-10 " + marker);
        DoctorService firstMapping = saveMapping(firstDoctor, service);
        saveMapping(secondDoctor, service);

        saveSchedule(firstDoctor, (short) 1, ScheduleSession.MORNING, "08:00", "11:00");
        saveSchedule(firstDoctor, (short) 1, ScheduleSession.AFTERNOON, "14:00", "17:00");
        saveSchedule(firstDoctor, (short) 2, ScheduleSession.MORNING, "08:00", "11:00");
        saveSchedule(secondDoctor, (short) 1, ScheduleSession.MORNING, "08:00", "11:00");

        Patient patient = savePatient(marker);
        saveAppointment(patient, firstMapping, MONDAY, LocalTime.of(9, 0));
        long appointmentCountBeforeLookup = appointmentRepository.count();

        ChatSlotLookupResult multiDoctorResult = slotLookupService.lookup(
                new ChatSlotLookupRequest(
                        List.of(
                                new SlotLookupTarget(firstDoctor.getId(), service.getId()),
                                new SlotLookupTarget(secondDoctor.getId(), service.getId())
                        ),
                        MONDAY,
                        MONDAY.plusDays(1),
                        ChatSessionPreference.MORNING
                )
        );

        assertThat(multiDoctorResult.availability()).satisfiesExactly(
                first -> {
                    assertThat(first.doctorId()).isEqualTo(firstDoctor.getId());
                    assertThat(first.slots()).hasSize(3);
                    assertThat(first.slots()).allSatisfy(slot ->
                            assertThat(slot.session()).isEqualTo(ChatSessionPreference.MORNING)
                    );
                    assertThat(first.slots())
                            .filteredOn(slot -> slot.date().equals(MONDAY))
                            .extracting(slot -> slot.estimatedStartTime())
                            .containsExactly(LocalTime.of(9, 30));
                    assertThat(first.slots())
                            .filteredOn(slot -> slot.date().equals(MONDAY))
                            .extracting(slot -> slot.queueNumber())
                            .containsExactly(2);
                    assertThat(first.slots())
                            .filteredOn(slot -> slot.date().equals(MONDAY.plusDays(1)))
                            .hasSize(2);
                },
                second -> {
                    assertThat(second.doctorId()).isEqualTo(secondDoctor.getId());
                    assertThat(second.slots()).hasSize(2);
                    assertThat(second.slots()).allSatisfy(slot ->
                            assertThat(slot.date()).isEqualTo(MONDAY)
                    );
                }
        );

        ChatSlotLookupResult doctorOnlyResult = slotLookupService.lookup(
                new ChatSlotLookupRequest(
                        List.of(new SlotLookupTarget(firstDoctor.getId(), null)),
                        MONDAY,
                        MONDAY,
                        ChatSessionPreference.AFTERNOON
                )
        );
        assertThat(doctorOnlyResult.availability()).singleElement().satisfies(availability -> {
            assertThat(availability.serviceId()).isNull();
            assertThat(availability.slots()).hasSize(2);
            assertThat(availability.slots()).allSatisfy(slot ->
                    assertThat(slot.session()).isEqualTo(ChatSessionPreference.AFTERNOON)
            );
            assertThat(availability.slots())
                    .extracting(slot -> slot.estimatedStartTime())
                    .containsExactly(LocalTime.of(14, 0), LocalTime.of(15, 30));
        });
        assertThat(appointmentRepository.count()).isEqualTo(appointmentCountBeforeLookup);
    }

    private Doctor saveDoctor(String marker) {
        User user = new User();
        user.setEmail(marker + "@ai10.example.com");
        user.setPasswordHash("not-used-by-ai-slot-test");
        user.setFullName("Bác sĩ " + marker);
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.saveAndFlush(user);

        Doctor doctor = new Doctor();
        doctor.setUser(savedUser);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber("AI10-" + marker);
        doctor.setBio("Bác sĩ kiểm thử AI-10");
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private DentalService saveDentalService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử AI-10");
        service.setPrice(new BigDecimal("300000.00"));
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
            short dayOfWeek,
            ScheduleSession session,
            String startTime,
            String endTime
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(startTime));
        schedule.setEndTime(LocalTime.parse(endTime));
        doctorScheduleRepository.saveAndFlush(schedule);
    }

    private Patient savePatient(String marker) {
        User user = new User();
        user.setEmail(marker + "@ai10-patient.example.com");
        user.setPasswordHash("not-used-by-ai-slot-test");
        user.setFullName("Bệnh nhân AI-10");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.saveAndFlush(user);

        Patient patient = new Patient();
        patient.setUser(savedUser);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private void saveAppointment(
            Patient patient,
            DoctorService mapping,
            LocalDate date,
            LocalTime startTime
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(date.atTime(startTime).atZone(CLINIC_ZONE).toInstant());
        appointment.setAppointmentDate(date);
        appointment.setSession(ScheduleSession.MORNING);
        appointment.setQueueNumber((short) 1);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointmentRepository.saveAndFlush(appointment);
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
