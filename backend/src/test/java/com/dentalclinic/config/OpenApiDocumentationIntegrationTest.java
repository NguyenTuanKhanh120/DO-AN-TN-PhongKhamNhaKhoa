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
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
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
        assertThat(publicLogin.path("responses").has("401")).isTrue();

        JsonNode protectedDashboard = operation(
                document,
                "/api/admin/dashboard/summary",
                "get"
        );
        assertThat(protectedDashboard.path("security").get(0).has(OpenApiConfig.BEARER_AUTH))
                .isTrue();
        assertThat(protectedDashboard.path("responses").has("401")).isTrue();
        assertThat(protectedDashboard.path("responses").has("403")).isTrue();

        JsonNode registration = operation(document, "/api/auth/register", "post");
        assertThat(registration.path("responses").has("201")).isTrue();
        assertThat(registration.path("responses").has("409")).isTrue();

        JsonNode createDayOff = operation(
                document,
                "/api/admin/doctors/{doctorId}/days-off",
                "post"
        );
        assertThat(createDayOff.path("tags").get(0).asText()).isEqualTo("Doctor Days Off");
        assertThat(createDayOff.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();
        assertThat(createDayOff.path("responses").has("201")).isTrue();
        assertThat(createDayOff.path("responses").has("409")).isTrue();

        JsonNode publicDaysOff = operation(
                document,
                "/api/doctors/{doctorId}/days-off",
                "get"
        );
        assertThat(publicDaysOff.path("tags").get(0).asText()).isEqualTo("Doctor Days Off");
        assertThat(publicDaysOff.has("security")).isFalse();

        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.has("RegisterRequest")).isTrue();
        assertThat(schemas.has("BookAppointmentRequest")).isTrue();
        assertThat(schemas.has("DashboardSummaryResponse")).isTrue();
        assertThat(schemas.has("CreateDoctorDayOffRequest")).isTrue();
        assertThat(schemas.has("CreateDoctorDayOffResponse")).isTrue();
        assertThat(schemas.has("ErrorResponse")).isTrue();
        assertThat(schemas.has("UserPrincipal")).isFalse();
        assertAllSchemaReferencesResolve(document, schemas);

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
