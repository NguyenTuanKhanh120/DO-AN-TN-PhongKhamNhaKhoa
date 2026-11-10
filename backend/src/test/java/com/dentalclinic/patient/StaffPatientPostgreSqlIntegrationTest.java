package com.dentalclinic.patient;

import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.patient.dto.CreateStaffPatientRequest;
import com.dentalclinic.patient.dto.StaffPatientResponse;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.patient.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
@Transactional
class StaffPatientPostgreSqlIntegrationTest {

    @Autowired
    private PatientService patientService;

    @Autowired
    private PatientRepository patientRepository;

    @Test
    void shouldCreateSearchAndRejectDuplicatePatientWithoutUserAccount() {
        String phone = "09" + String.format(
                "%08d",
                Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000)
        );
        LocalDate dateOfBirth = LocalDate.of(1990, 6, 15);
        CreateStaffPatientRequest request = new CreateStaffPatientRequest(
                "  Bệnh   nhân   tại quầy  ",
                phone,
                dateOfBirth,
                Gender.FEMALE,
                "TP.HCM",
                null,
                "Penicillin"
        );

        StaffPatientResponse created = patientService.createForStaff(request);

        Patient persisted = patientRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getUser()).isNull();
        assertThat(persisted.getFullName()).isEqualTo("Bệnh nhân tại quầy");
        assertThat(persisted.getPhone()).isEqualTo(phone);

        PageResponse<StaffPatientResponse> byPhone = patientService.searchForStaff(phone, 0, 20);
        assertThat(byPhone.content()).extracting(StaffPatientResponse::id)
                .contains(created.id());

        PageResponse<StaffPatientResponse> byId = patientService.searchForStaff(
                created.id().toString(),
                0,
                20
        );
        assertThat(byId.content()).extracting(StaffPatientResponse::id)
                .containsExactly(created.id());

        assertThatThrownBy(() -> patientService.createForStaff(
                new CreateStaffPatientRequest(
                        "bệnh nhân tại quầy",
                        phone,
                        dateOfBirth,
                        Gender.FEMALE,
                        null,
                        null,
                        null
                )
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Đã tồn tại hồ sơ bệnh nhân");
    }
}
