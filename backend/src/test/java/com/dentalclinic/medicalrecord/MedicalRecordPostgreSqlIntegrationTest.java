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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class MedicalRecordPostgreSqlIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "medical-record-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void shouldCreateMedicalRecordPersistJsonbAndEnforceOnePerAppointment() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        MvcResult created = mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo bệnh án thành công"))
                .andExpect(jsonPath("$.data.appointmentId")
                        .value(fixture.inProgressAppointmentId().toString()))
                .andExpect(jsonPath("$.data.diagnosis").value("Viêm tủy răng 36"))
                .andExpect(jsonPath("$.data.prescription[0].medicationName")
                        .value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.data.prescription[0].quantity").value(10))
                .andExpect(jsonPath("$.data.followUpRequired").value(true))
                .andExpect(jsonPath("$.data.recommendedFollowUpDate")
                        .value("2026-10-20"))
                .andReturn();
        UUID recordId = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsByteArray()
        ).path("data").path("id").asText());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select id,
                       appointment_id,
                       diagnosis,
                       treatment,
                       treatment_plan,
                       notes,
                       follow_up_required,
                       recommended_follow_up_date::text as recommended_follow_up_date,
                       follow_up_note,
                       jsonb_typeof(prescription) as prescription_type,
                       jsonb_array_length(prescription) as prescription_count,
                       prescription -> 0 ->> 'medicationName' as medication_name,
                       prescription -> 0 ->> 'quantity' as quantity
                from medical_records
                where id = ?
                """, recordId);
        assertThat(row.get("appointment_id")).isEqualTo(fixture.inProgressAppointmentId());
        assertThat(row.get("diagnosis")).isEqualTo("Viêm tủy răng 36");
        assertThat(row.get("treatment")).isEqualTo("Điều trị tủy lần 1");
        assertThat(row.get("treatment_plan")).isEqualTo("Điều trị tiếp lần 2");
        assertThat(row.get("notes")).isEqualTo("Hạn chế nhai bên đang điều trị");
        assertThat(row.get("follow_up_required")).isEqualTo(true);
        assertThat(row.get("recommended_follow_up_date")).isEqualTo("2026-10-20");
        assertThat(row.get("follow_up_note")).isEqualTo("Tiếp tục điều trị tủy");
        assertThat(row.get("prescription_type")).isEqualTo("array");
        assertThat(row.get("prescription_count")).isEqualTo(1);
        assertThat(row.get("medication_name")).isEqualTo("Paracetamol 500mg");
        assertThat(row.get("quantity")).isEqualTo("10");

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Lịch hẹn đã có bệnh án"));
        assertThat(recordCount(fixture.inProgressAppointmentId())).isEqualTo(1);
    }

    @Test
    void shouldUpdateExistingMedicalRecordAndKeepSameIdentity() throws Exception {
        MedicalRecord medicalRecord = saveMedicalRecord(
                fixture.inProgressAppointmentId(),
                "Chẩn đoán cũ",
                "Điều trị cũ"
        );
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(put(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noFollowUpRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật bệnh án thành công"))
                .andExpect(jsonPath("$.data.id").value(medicalRecord.getId().toString()))
                .andExpect(jsonPath("$.data.diagnosis").value("Sâu răng nhẹ"))
                .andExpect(jsonPath("$.data.followUpRequired").value(false))
                .andExpect(jsonPath("$.data.prescription.length()").value(0));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select id,
                       diagnosis,
                       treatment,
                       follow_up_required,
                       recommended_follow_up_date,
                       follow_up_note,
                       jsonb_typeof(prescription) as prescription_type,
                       jsonb_array_length(prescription) as prescription_count
                from medical_records
                where appointment_id = ?
                """, fixture.inProgressAppointmentId());
        assertThat(row.get("id")).isEqualTo(medicalRecord.getId());
        assertThat(row.get("diagnosis")).isEqualTo("Sâu răng nhẹ");
        assertThat(row.get("treatment")).isEqualTo("Trám răng");
        assertThat(row.get("follow_up_required")).isEqualTo(false);
        assertThat(row.get("recommended_follow_up_date")).isNull();
        assertThat(row.get("follow_up_note")).isNull();
        assertThat(row.get("prescription_type")).isEqualTo("array");
        assertThat(row.get("prescription_count")).isEqualTo(0);
    }

    @Test
    void shouldHideAnotherDoctorsAppointment() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.otherDoctorAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(recordCount(fixture.otherDoctorAppointmentId())).isZero();
    }

    @Test
    void shouldRejectCreateUnlessAppointmentIsInProgress() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.bookedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noFollowUpRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Chỉ có thể nhập bệnh án cho lịch hẹn đang ở trạng thái IN_PROGRESS"
                ));

        assertThat(recordCount(fixture.bookedAppointmentId())).isZero();
    }

    @Test
    void shouldRejectUpdateAfterAppointmentWasCompleted() throws Exception {
        MedicalRecord record = saveMedicalRecord(
                fixture.completedAppointmentId(),
                "Chẩn đoán đã hoàn tất",
                "Điều trị đã hoàn tất"
        );
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(put(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.completedAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noFollowUpRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        assertThat(diagnosisOf(record.getId())).isEqualTo("Chẩn đoán đã hoàn tất");
    }

    @Test
    void shouldValidateRequiredFollowUpInformation() throws Exception {
        String token = login(fixture.firstDoctorEmail());

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosis": "Viêm tủy",
                                  "treatment": "Điều trị tủy",
                                  "prescription": [],
                                  "followUpRequired": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Khi cần tái khám phải có ngày tái khám dự kiến"
                ));

        assertThat(recordCount(fixture.inProgressAppointmentId())).isZero();
    }

    @Test
    void shouldPersistRestoreAndProtectMedicalRecordDraftThenDeleteItOnFinalSave() throws Exception {
        String ownerToken = login(fixture.firstDoctorEmail());

        mockMvc.perform(put(
                        "/api/doctor/appointments/{id}/medical-record/draft",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosis": "Chẩn đoán đang nhập",
                                  "treatment": null,
                                  "prescription": [
                                    {
                                      "medicationName": "Paracetamol",
                                      "quantity": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis").value("Chẩn đoán đang nhập"))
                .andExpect(jsonPath("$.data.treatment").isEmpty())
                .andExpect(jsonPath("$.data.prescription[0].medicationName")
                        .value("Paracetamol"));

        Map<String, Object> draftRow = jdbcTemplate.queryForMap("""
                select jsonb_typeof(draft_data) as draft_type,
                       draft_data ->> 'diagnosis' as diagnosis,
                       draft_data -> 'prescription' -> 0 ->> 'medicationName' as medication_name
                from medical_record_drafts
                where appointment_id = ?
                """, fixture.inProgressAppointmentId());
        assertThat(draftRow.get("draft_type")).isEqualTo("object");
        assertThat(draftRow.get("diagnosis")).isEqualTo("Chẩn đoán đang nhập");
        assertThat(draftRow.get("medication_name")).isEqualTo("Paracetamol");
        assertThat(appointmentRepository.findById(fixture.inProgressAppointmentId())
                .orElseThrow().getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);

        mockMvc.perform(get(
                        "/api/doctor/appointments/{id}/medical-record/draft",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis").value("Chẩn đoán đang nhập"));

        String otherDoctorToken = login(fixture.secondDoctorEmail());
        mockMvc.perform(get(
                        "/api/doctor/appointments/{id}/medical-record/draft",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(otherDoctorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        fixture.inProgressAppointmentId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated());
        Integer remainingDrafts = jdbcTemplate.queryForObject(
                "select count(*) from medical_record_drafts where appointment_id = ?",
                Integer.class,
                fixture.inProgressAppointmentId()
        );
        assertThat(remainingDrafts).isZero();
    }

    private Fixture createFixture() {
        String marker = UUID.randomUUID().toString();
        User firstDoctorUser = saveUser(
                "phase19-doctor-a-" + marker + "@example.com",
                "Bác sĩ Phase 19 A",
                Role.DOCTOR
        );
        Doctor firstDoctor = saveDoctor(firstDoctorUser, "PHASE19-A-" + marker);
        User secondDoctorUser = saveUser(
                "phase19-doctor-b-" + marker + "@example.com",
                "Bác sĩ Phase 19 B",
                Role.DOCTOR
        );
        Doctor secondDoctor = saveDoctor(secondDoctorUser, "PHASE19-B-" + marker);
        User patientUser = saveUser(
                "phase19-patient-" + marker + "@example.com",
                "Bệnh nhân Phase 19",
                Role.PATIENT
        );
        Patient patient = savePatient(patientUser);
        DentalService service = saveService("Dịch vụ Phase 19 " + marker);
        DoctorService firstMapping = saveMapping(firstDoctor, service);
        DoctorService secondMapping = saveMapping(secondDoctor, service);

        Appointment inProgress = saveAppointment(
                patient,
                firstMapping,
                Instant.parse("2026-10-12T02:00:00Z"),
                AppointmentStatus.IN_PROGRESS
        );
        Appointment booked = saveAppointment(
                patient,
                firstMapping,
                Instant.parse("2026-10-12T04:00:00Z"),
                AppointmentStatus.BOOKED
        );
        Appointment completed = saveAppointment(
                patient,
                firstMapping,
                Instant.parse("2026-10-12T05:00:00Z"),
                AppointmentStatus.COMPLETED
        );
        completed.setActualStartAt(Instant.parse("2026-10-12T05:00:00Z"));
        completed.setActualEndAt(Instant.parse("2026-10-12T05:45:00Z"));
        appointmentRepository.saveAndFlush(completed);
        Appointment otherDoctor = saveAppointment(
                patient,
                secondMapping,
                Instant.parse("2026-10-12T03:00:00Z"),
                AppointmentStatus.IN_PROGRESS
        );

        return new Fixture(
                firstDoctorUser.getEmail(),
                secondDoctorUser.getEmail(),
                inProgress.getId(),
                booked.getId(),
                completed.getId(),
                otherDoctor.getId()
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
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber(licenseNumber);
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
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
        service.setDescription("Dịch vụ kiểm thử bệnh án");
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
        if (status == AppointmentStatus.IN_PROGRESS) {
            appointment.setCheckedInAt(scheduledStartAt.minusSeconds(300));
            appointment.setActualStartAt(scheduledStartAt);
        }
        return appointmentRepository.saveAndFlush(appointment);
    }

    private MedicalRecord saveMedicalRecord(
            UUID appointmentId,
            String diagnosis,
            String treatment
    ) {
        MedicalRecord record = new MedicalRecord();
        record.setAppointment(appointmentRepository.findById(appointmentId).orElseThrow());
        record.setDiagnosis(diagnosis);
        record.setTreatment(treatment);
        record.setFollowUpRequired(false);
        record.setPrescription(JsonNodeFactory.instance.arrayNode());
        return medicalRecordRepository.saveAndFlush(record);
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

    private int recordCount(UUID appointmentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from medical_records where appointment_id = ?",
                Integer.class,
                appointmentId
        );
        return count == null ? 0 : count;
    }

    private String diagnosisOf(UUID recordId) {
        return jdbcTemplate.queryForObject(
                "select diagnosis from medical_records where id = ?",
                String.class,
                recordId
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String validRequestJson() {
        return """
                {
                  "diagnosis": "Viêm tủy răng 36",
                  "treatment": "Điều trị tủy lần 1",
                  "treatmentPlan": "Điều trị tiếp lần 2",
                  "notes": "Hạn chế nhai bên đang điều trị",
                  "prescription": [
                    {
                      "medicationName": "Paracetamol 500mg",
                      "dosage": "1 viên",
                      "quantity": 10,
                      "instructions": "Uống sau ăn khi đau"
                    }
                  ],
                  "followUpRequired": true,
                  "recommendedFollowUpDate": "2026-10-20",
                  "followUpNote": "Tiếp tục điều trị tủy"
                }
                """;
    }

    private String noFollowUpRequestJson() {
        return """
                {
                  "diagnosis": "Sâu răng nhẹ",
                  "treatment": "Trám răng",
                  "prescription": [],
                  "followUpRequired": false
                }
                """;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record Fixture(
            String firstDoctorEmail,
            String secondDoctorEmail,
            UUID inProgressAppointmentId,
            UUID bookedAppointmentId,
            UUID completedAppointmentId,
            UUID otherDoctorAppointmentId
    ) {
    }
}
