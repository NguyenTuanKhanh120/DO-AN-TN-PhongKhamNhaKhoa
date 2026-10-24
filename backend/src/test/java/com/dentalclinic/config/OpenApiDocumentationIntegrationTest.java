package com.dentalclinic.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
class OpenApiDocumentationIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "openapi-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.cors.allowed-origins", () -> ALLOWED_ORIGIN);
        registry.add("springdoc.api-docs.enabled", () -> true);
        registry.add("springdoc.swagger-ui.enabled", () -> true);
    }

    @Test
    void shouldExposeOpenApiAndSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDocumentJwtSecurityDtosStatusCodesAndErrorCodes() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());

        assertThat(document.path("info").path("title").asText())
                .isEqualTo("Dental Clinic Management API");
        assertThat(document.path("info").path("version").asText()).isEqualTo("1.0.0");
        assertThat(document.path("info").path("description").asText())
                .contains("RESOURCE_CONFLICT", "AUTHENTICATION_REQUIRED", "INTERNAL_SERVER_ERROR");

        JsonNode bearerScheme = document.path("components")
                .path("securitySchemes")
                .path(OpenApiConfig.BEARER_AUTH);
        assertThat(bearerScheme.path("type").asText()).isEqualTo("http");
        assertThat(bearerScheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerScheme.path("bearerFormat").asText()).isEqualTo("JWT");

        JsonNode publicLogin = operation(document, "/api/auth/login", "post");
        assertThat(publicLogin.has("security")).isFalse();
        assertOnlyResponses(publicLogin, "200");
        assertThat(publicLogin.path("description").asText())
                .contains("Server response", "data.accessToken");
        JsonNode loginSuccess = responseExample(publicLogin, "200");
        assertThat(loginSuccess.path("message").asText()).isEqualTo("Đăng nhập thành công");
        assertThat(loginSuccess.path("data").path("accessToken").asText())
                .isEqualTo("<JWT_ACCESS_TOKEN>");
        assertThat(loginSuccess.path("data").path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(loginSuccess.path("data").path("expiresInSeconds").asLong()).isEqualTo(900);
        assertThat(loginSuccess.path("data").has("refreshToken")).isFalse();
        assertThat(loginSuccess.path("data").path("email").asText()).isEqualTo("<EMAIL>");
        assertThat(loginSuccess.path("data").path("fullName").asText()).isEqualTo("<FULL_NAME>");
        assertThat(loginSuccess.path("data").path("role").asText())
                .isEqualTo("<PATIENT|DOCTOR|ADMIN|STAFF>");
        JsonNode protectedDashboard = operation(
                document,
                "/api/admin/dashboard/summary",
                "get"
        );
        assertThat(protectedDashboard.path("security").get(0).has(OpenApiConfig.BEARER_AUTH))
                .isTrue();
        assertOnlyResponses(protectedDashboard, "200");

        JsonNode registration = operation(document, "/api/auth/register", "post");
        assertOnlyResponses(registration, "201");
        JsonNode registrationSuccess = responseExample(registration, "201");
        assertThat(registrationSuccess.path("success").asBoolean()).isTrue();
        assertThat(registrationSuccess.path("code").asText()).isEqualTo("SUCCESS");
        assertThat(registrationSuccess.has("errors")).isFalse();
        assertThat(registrationSuccess.has("path")).isFalse();

        JsonNode refresh = operation(document, "/api/auth/refresh", "post");
        assertThat(refresh.has("security")).isFalse();
        assertThat(refresh.path("parameters").toString()).contains("X-XSRF-TOKEN");
        assertThat(refresh.path("description").asText()).contains("refresh_token", "double-submit CSRF");
        assertThat(responseExample(refresh, "200").path("data").has("refreshToken")).isFalse();

        JsonNode session = operation(document, "/api/auth/session", "get");
        assertThat(session.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();
        JsonNode activity = operation(document, "/api/auth/activity", "post");
        assertThat(activity.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();

        JsonNode officialMedicalRecord = operation(
                document,
                "/api/doctor/appointments/{id}/medical-record",
                "get"
        );
        assertThat(officialMedicalRecord.path("security").get(0)
                .has(OpenApiConfig.BEARER_AUTH)).isTrue();

        JsonNode adminDoctorSchedule = operation(
                document,
                "/api/admin/doctors/{doctorId}/schedules",
                "post"
        );
        assertThat(adminDoctorSchedule.path("tags").get(0).asText())
                .isEqualTo("Doctor Schedules");
        assertThat(adminDoctorSchedule.path("security").get(0).has(OpenApiConfig.BEARER_AUTH))
                .isTrue();
        assertThat(adminDoctorSchedule.path("responses").has("201")).isTrue();

        JsonNode doctorSchedule = operation(document, "/api/doctor/schedules", "get");
        assertThat(doctorSchedule.path("tags").get(0).asText()).isEqualTo("Doctor Schedules");
        assertThat(document.path("paths").path("/api/doctor/schedules").has("post")).isFalse();
        assertThat(document.path("paths").path("/api/doctor/schedules").has("put")).isFalse();
        assertThat(document.path("paths").path("/api/doctor/schedules").has("delete")).isFalse();

        JsonNode currentDoctorServices = operation(document, "/api/doctor/services", "get");
        assertThat(currentDoctorServices.path("tags").get(0).asText())
                .isEqualTo("Doctor Services");
        assertThat(currentDoctorServices.path("security").get(0).has(OpenApiConfig.BEARER_AUTH))
                .isTrue();

        JsonNode doctorCalendar = operation(
                document,
                "/api/doctors/{doctorId}/calendar",
                "get"
        );
        assertThat(doctorCalendar.path("tags").get(0).asText()).isEqualTo("Doctor Calendar");
        assertThat(doctorCalendar.has("security")).isFalse();

        JsonNode doctorPayment = operation(
                document,
                "/api/doctor/appointments/{id}/payment/items",
                "put"
        );
        assertThat(doctorPayment.path("tags").get(0).asText()).isEqualTo("Doctor Payments");
        assertThat(doctorPayment.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();
        assertOnlyResponses(doctorPayment, "200");

        JsonNode adminPayment = operation(document, "/api/admin/payments/{id}/confirm", "patch");
        assertThat(adminPayment.path("tags").get(0).asText()).isEqualTo("Admin Payments");
        assertThat(adminPayment.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();

        JsonNode createStaff = operation(document, "/api/admin/staff", "post");
        assertThat(createStaff.path("tags").get(0).asText()).isEqualTo("Admin Staff");
        assertThat(createStaff.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();
        assertOnlyResponses(createStaff, "201");

        JsonNode staffAppointments = operation(document, "/api/staff/appointments", "get");
        assertThat(staffAppointments.path("tags").get(0).asText())
                .isEqualTo("Staff Appointments");
        assertThat(staffAppointments.path("security").get(0).has(OpenApiConfig.BEARER_AUTH))
                .isTrue();

        JsonNode staffPayment = operation(document, "/api/staff/payments/{id}/confirm", "patch");
        assertThat(staffPayment.path("tags").get(0).asText()).isEqualTo("Staff Payments");
        assertThat(staffPayment.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();

        JsonNode patientPayments = operation(document, "/api/patient/payments", "get");
        assertThat(patientPayments.path("tags").get(0).asText()).isEqualTo("Patient Payments");

        JsonNode revenue = operation(document, "/api/admin/dashboard/revenue", "get");
        assertThat(revenue.path("tags").get(0).asText()).isEqualTo("Admin Dashboard");

        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.has("RegisterRequest")).isTrue();
        assertThat(schemas.has("CreateStaffRequest")).isTrue();
        assertThat(schemas.has("BookAppointmentRequest")).isTrue();
        assertThat(schemas.has("DashboardSummaryResponse")).isTrue();
        assertThat(schemas.path("DashboardSummaryResponse").path("properties").has("patientsVisitedToday"))
                .isTrue();
        assertThat(schemas.path("DashboardSummaryResponse").path("properties").has("totalPatients"))
                .isFalse();
        assertThat(schemas.has("CreateDoctorDayOffRequest")).isFalse();
        assertThat(schemas.has("CreateDoctorDayOffResponse")).isFalse();
        assertThat(schemas.path("CalendarAvailabilityStatus").toString())
                .doesNotContain("DAY_OFF");
        assertThat(schemas.has("UpdatePaymentItemsRequest")).isTrue();
        assertThat(schemas.has("PaymentResponse")).isTrue();
        assertThat(schemas.has("ConfirmPaymentRequest")).isTrue();
        assertThat(schemas.has("RevenueSummaryResponse")).isTrue();
        assertThat(schemas.path("CreateDentalServiceRequest").path("properties").has("imageUrl"))
                .isTrue();
        assertThat(schemas.path("UpdateDentalServiceRequest").path("properties").has("imageUrl"))
                .isTrue();
        assertThat(schemas.path("DentalServiceResponse").path("properties").has("imageUrl"))
                .isTrue();
        assertThat(schemas.path("DoctorAssignedServiceResponse").path("properties")
                .has("durationMinutes")).isTrue();
        JsonNode serviceImageUpload = operation(document, "/api/admin/services/images", "post");
        assertThat(serviceImageUpload.path("security").get(0).has(OpenApiConfig.BEARER_AUTH))
                .isTrue();
        assertThat(serviceImageUpload.path("requestBody").path("content").has("multipart/form-data"))
                .isTrue();
        assertThat(document.path("paths").has("/api/services/images/{filename}"))
                .isTrue();
        assertThat(schemas.has("AuthSessionResponse")).isTrue();
        assertThat(schemas.has("MedicalRecordResponse")).isTrue();
        assertThat(schemas.has("MedicalRecordDraftRequest")).isFalse();
        assertThat(schemas.has("MedicalRecordDraftResponse")).isFalse();
        assertThat(schemas.has("ErrorResponse")).isTrue();
        assertThat(schemas.has("UserPrincipal")).isFalse();
        assertAllSchemaReferencesResolve(document, schemas);

        assertThat(document.path("paths").has("/api/doctor/appointments/{id}/check-in"))
                .isFalse();
        assertThat(document.path("paths").has("/api/doctor/appointments/{id}/follow-up"))
                .isFalse();
        assertThat(document.path("paths").has("/api/doctor/appointments/{id}/follow-up-slots"))
                .isFalse();
        assertThat(document.path("paths").has("/api/admin/doctors/{doctorId}/days-off"))
                .isFalse();
        assertThat(document.path("paths").has("/api/admin/doctors/{doctorId}/days-off/preview"))
                .isFalse();
        assertThat(document.path("paths").has("/api/admin/doctors/{doctorId}/days-off/{dayOffId}"))
                .isFalse();
        assertThat(document.path("paths").has("/api/doctors/{doctorId}/days-off"))
                .isFalse();
        assertThat(document.path("paths")
                .has("/api/doctor/appointments/{id}/medical-record/draft"))
                .isFalse();
        assertThat(document.path("paths").has("/api/chat/sessions")).isFalse();
    }

    @Test
    void shouldAllowConfiguredReactOriginForApiPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization,content-type"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")
                ));
    }

    @Test
    void shouldRejectUnconfiguredOriginForApiPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private JsonNode operation(JsonNode document, String path, String method) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode()).as(method + " " + path).isFalse();
        return operation;
    }

    private void assertOnlyResponses(JsonNode operation, String... expectedStatuses) {
        List<String> actualStatuses = new ArrayList<>();
        operation.path("responses").fieldNames().forEachRemaining(actualStatuses::add);
        assertThat(actualStatuses).containsExactlyInAnyOrder(expectedStatuses);
    }

    private JsonNode responseExample(JsonNode operation, String status) {
        JsonNode example = operation.path("responses")
                .path(status)
                .path("content")
                .path("application/json")
                .path("example");
        assertThat(example.isMissingNode()).as("Missing response example for HTTP " + status).isFalse();
        return example;
    }

    private void assertAllSchemaReferencesResolve(JsonNode document, JsonNode schemas) {
        List<String> references = new ArrayList<>();
        collectSchemaReferences(document, references);
        assertThat(references).isNotEmpty();
        references.forEach(reference -> {
            String schemaName = reference.substring("#/components/schemas/".length());
            assertThat(schemas.has(schemaName)).as("Unresolved OpenAPI schema: " + reference).isTrue();
        });
    }

    private void collectSchemaReferences(JsonNode node, List<String> references) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (entry.getKey().equals("$ref")
                        && entry.getValue().isTextual()
                        && entry.getValue().asText().startsWith("#/components/schemas/")) {
                    references.add(entry.getValue().asText());
                } else {
                    collectSchemaReferences(entry.getValue(), references);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectSchemaReferences(child, references));
        }
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
