package com.dentalclinic.medicalrecord;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class PatientMedicalHistoryPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";

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
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "patient-history-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldReturnOnlyOwnedCompletedHistoryNewestFirstWithPagination() throws Exception {
        String token = login(fixture.patientEmail());

        mockMvc.perform(get("/api/patient/medical-records?page=0&size=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].appointmentId")
                        .value(fixture.newerCompletedAppointmentId().toString()))
                .andExpect(jsonPath("$.data.content[0].doctorFullName")
                        .value("Bác sĩ Phase 22"))
                .andExpect(jsonPath("$.data.content[0].doctorSpecialization")
                        .value("Nội nha"))
                .andExpect(jsonPath("$.data.content[0].serviceName")
                        .value(fixture.serviceName()))
                .andExpect(jsonPath("$.data.content[0].scheduledStartAt")
                        .value("2026-10-14T10:00:00+07:00"))
                .andExpect(jsonPath("$.data.content[0].actualEndAt")
                        .value("2026-10-14T10:45:00+07:00"))
                .andExpect(jsonPath("$.data.content[0].diagnosis")
                        .value("Viêm tủy răng 36"))
                .andExpect(jsonPath("$.data.content[0].prescription[0].medicationName")
                        .value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.data.content[0].prescription[0].quantity")
                        .value(10))
                .andExpect(jsonPath("$.data.content[0].followUpRequired").value(true))
                .andExpect(jsonPath("$.data.content[0].recommendedFollowUpDate")
                        .value("2026-10-21"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(get("/api/patient/medical-records?page=1&size=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].appointmentId")
                        .value(fixture.olderCompletedAppointmentId().toString()))
                .andExpect(jsonPath("$.data.hasPrevious").value(true));
    }

    @Test
    void shouldReturnOwnedCompletedRecordByAppointmentId() throws Exception {
        String token = login(fixture.patientEmail());

        MvcResult result = mockMvc.perform(get(
                        "/api/patient/medical-records/{appointmentId}",
                        fixture.newerCompletedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId")
                        .value(fixture.newerCompletedAppointmentId().toString()))
                .andExpect(jsonPath("$.data.treatment")
                        .value("Điều trị tủy lần 1"))
                .andExpect(jsonPath("$.data.followUpNote")
                        .value("Tiếp tục điều trị tủy"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.path("data").path("id").asText()).isNotBlank();
    }

    @Test
    void shouldHideAnotherPatientsAndUnfinishedMedicalRecords() throws Exception {
        String token = login(fixture.patientEmail());

        mockMvc.perform(get(
                        "/api/patient/medical-records/{appointmentId}",
                        fixture.otherPatientCompletedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get(
                        "/api/patient/medical-records/{appointmentId}",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả khám"));
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "phase22-doctor-" + marker + "@example.com",
                "Bác sĩ Phase 22",
                Role.DOCTOR
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE22-" + marker);
        User patientUser = saveUser(
                "phase22-patient-a-" + marker + "@example.com",
                "Bệnh nhân Phase 22 A",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        User otherPatientUser = saveUser(
                "phase22-patient-b-" + marker + "@example.com",
                "Bệnh nhân Phase 22 B",
                Role.PATIENT
        );
        Patient otherPatient = savePatient(otherPatientUser);
        String serviceName = "Điều trị tủy Phase 22 " + marker;
        DentalService service = saveService(serviceName);
        DoctorService mapping = saveMapping(doctor, service);

        Appointment olderCompleted = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-12T03:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        setExaminationTimes(
                olderCompleted,
                Instant.parse("2026-10-12T03:05:00Z"),
                Instant.parse("2026-10-12T03:40:00Z")
        );
        saveMedicalRecord(olderCompleted, "Sâu răng", false);

        Appointment newerCompleted = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-14T03:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        setExaminationTimes(
                newerCompleted,
                Instant.parse("2026-10-14T03:05:00Z"),
                Instant.parse("2026-10-14T03:45:00Z")
        );
        saveMedicalRecord(newerCompleted, "Viêm tủy răng 36", true);

        Appointment inProgress = saveAppointment(
                patient,
                mapping,
                Instant.parse("2026-10-15T03:00:00Z"),
                AppointmentStatus.IN_PROGRESS
        );
        inProgress.setActualStartAt(Instant.parse("2026-10-15T03:05:00Z"));
        appointmentRepository.saveAndFlush(inProgress);
        saveMedicalRecord(inProgress, "Bệnh án đang soạn", false);

        Appointment otherPatientCompleted = saveAppointment(
                otherPatient,
                mapping,
                Instant.parse("2026-10-16T03:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        setExaminationTimes(
                otherPatientCompleted,
                Instant.parse("2026-10-16T03:05:00Z"),
                Instant.parse("2026-10-16T03:40:00Z")
        );
        saveMedicalRecord(otherPatientCompleted, "Bệnh án của người khác", false);

        return new Fixture(
                patientUser.getEmail(),
                serviceName,
                olderCompleted.getId(),
                newerCompleted.getId(),
                inProgress.getId(),
                otherPatientCompleted.getId()
        );
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Doctor saveDoctor(User user, String licenseNumber) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("Nội nha");
        doctor.setLicenseNumber(licenseNumber);
        doctor.setRating(new BigDecimal("4.8"));
        doctor.setExperienceYears(10);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private DentalService saveService(String name) {
        DentalService service = new DentalService();
        service.setName(name);
        service.setDescription("Dịch vụ kiểm thử lịch sử khám");
        service.setPrice(new BigDecimal("750000"));
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

    private Appointment saveAppointment(
            Patient patient,
            DoctorService doctorService,
            Instant scheduledStartAt,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(status);
        return appointmentRepository.saveAndFlush(appointment);
    }

    private void setExaminationTimes(
            Appointment appointment,
            Instant actualStartAt,
            Instant actualEndAt
    ) {
        appointment.setCheckedInAt(actualStartAt.minusSeconds(300));
        appointment.setActualStartAt(actualStartAt);
        appointment.setActualEndAt(actualEndAt);
        appointmentRepository.saveAndFlush(appointment);
    }

    private MedicalRecord saveMedicalRecord(
            Appointment appointment,
            String diagnosis,
            boolean followUpRequired
    ) {
        MedicalRecord record = new MedicalRecord();
        record.setAppointment(appointment);
        record.setDiagnosis(diagnosis);
        record.setTreatment(followUpRequired ? "Điều trị tủy lần 1" : "Trám răng");
        record.setTreatmentPlan(followUpRequired ? "Điều trị tiếp lần 2" : null);
        record.setNotes(followUpRequired ? "Hạn chế nhai bên đang điều trị" : null);
        record.setPrescription(followUpRequired ? prescription() : JsonNodeFactory.instance.arrayNode());
        record.setFollowUpRequired(followUpRequired);
        record.setRecommendedFollowUpDate(
                followUpRequired ? LocalDate.of(2026, 10, 21) : null
        );
        record.setFollowUpNote(followUpRequired ? "Tiếp tục điều trị tủy" : null);
        return medicalRecordRepository.saveAndFlush(record);
    }

    private ArrayNode prescription() {
        ArrayNode prescription = JsonNodeFactory.instance.arrayNode();
        prescription.addObject()
                .put("medicationName", "Paracetamol 500mg")
                .put("dosage", "1 viên")
                .put("quantity", 10)
                .put("instructions", "Uống sau ăn khi đau");
        return prescription;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            String patientEmail,
            String serviceName,
            UUID olderCompletedAppointmentId,
            UUID newerCompletedAppointmentId,
            UUID inProgressAppointmentId,
            UUID otherPatientCompletedAppointmentId
    ) {
    }
}
