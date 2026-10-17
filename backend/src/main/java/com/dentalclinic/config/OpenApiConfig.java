package com.dentalclinic.config;

import com.dentalclinic.common.constant.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final Set<String> CREATED_ENDPOINTS = Set.of(
            "POST /api/auth/register",
            "POST /api/admin/doctors",
            "POST /api/admin/services",
            "POST /api/doctor/schedules",
            "POST /api/admin/doctors/{doctorId}/days-off",
            "POST /api/patient/appointments",
            "POST /api/doctor/appointments/{id}/medical-record"
    );

    @Bean
    public OpenAPI dentalClinicOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .name(BEARER_AUTH)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token nhận từ POST /api/auth/login"))
                .addSchemas(ERROR_SCHEMA, errorResponseSchema());

        return new OpenAPI()
                .info(new Info()
                        .title("Dental Clinic Management API")
                        .version("1.0.0")
                        .description(apiDescription())
                        .contact(new Contact().name("Dental Clinic Backend Team")))
                .components(components)
                .tags(List.of(
                        new Tag().name("Authentication").description("Đăng ký bệnh nhân và đăng nhập"),
                        new Tag().name("Patient Profile").description("Hồ sơ cá nhân của bệnh nhân"),
                        new Tag().name("Admin Users").description("Quản lý tài khoản bởi quản trị viên"),
                        new Tag().name("Doctors").description("Tra cứu và quản lý bác sĩ"),
                        new Tag().name("Dental Services").description("Tra cứu và quản lý dịch vụ nha khoa"),
                        new Tag().name("Doctor Services").description("Phân công dịch vụ cho bác sĩ"),
                        new Tag().name("Doctor Schedules").description("Lịch làm việc cố định theo tuần"),
                        new Tag().name("Doctor Days Off").description("Ngoại lệ nghỉ theo ngày do quản trị viên đăng ký"),
                        new Tag().name("Available Slots").description("Khung giờ khám còn trống"),
                        new Tag().name("Patient Appointments").description("Lịch hẹn của bệnh nhân"),
                        new Tag().name("Doctor Appointments").description("Quy trình khám của bác sĩ"),
                        new Tag().name("Patient Medical History").description("Lịch sử bệnh án của bệnh nhân"),
                        new Tag().name("Admin Appointments").description("Tra cứu và audit lịch hẹn"),
                        new Tag().name("Admin Dashboard").description("Thống kê tổng quan phòng khám")
                ));
    }

    @Bean
    public OpenApiCustomizer dentalClinicApiConventionCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(ERROR_SCHEMA, errorResponseSchema());
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        operation.setTags(List.of(resolveTag(path)));
                        boolean protectedEndpoint = !isPublicEndpoint(path);
                        if (protectedEndpoint) {
                            operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
                        }

                        normalizeCreatedResponse(path, method.name(), operation);
                        addResponseIfMissing(
                                operation,
                                "400",
                                "INVALID_REQUEST, VALIDATION_FAILED hoặc MALFORMED_REQUEST"
                        );
                        if (protectedEndpoint || path.equals("/api/auth/login")) {
                            addResponseIfMissing(
                                    operation,
                                    "401",
                                    "AUTHENTICATION_REQUIRED hoặc INVALID_CREDENTIALS"
                            );
                        }
                        if (protectedEndpoint) {
                            addResponseIfMissing(
                                    operation,
                                    "403",
                                    "ACCESS_DENIED hoặc ACCOUNT_INACTIVE"
                            );
                        }
                        if (path.contains("{")) {
                            addResponseIfMissing(operation, "404", "RESOURCE_NOT_FOUND");
                        }
                        if (!method.name().equals("GET")) {
                            addResponseIfMissing(
                                    operation,
                                    "409",
                                    "RESOURCE_CONFLICT hoặc mã xung đột nghiệp vụ cụ thể"
                            );
                        }
                        addResponseIfMissing(operation, "500", "INTERNAL_SERVER_ERROR");
                    })
            );
        };
    }

    private void normalizeCreatedResponse(
            String path,
            String method,
            io.swagger.v3.oas.models.Operation operation
    ) {
        if (!CREATED_ENDPOINTS.contains(method + " " + path)) {
            return;
        }
        io.swagger.v3.oas.models.responses.ApiResponse success = operation.getResponses().remove("200");
        if (success == null) {
            success = new io.swagger.v3.oas.models.responses.ApiResponse();
        }
        success.setDescription("Tạo mới thành công");
        operation.getResponses().addApiResponse("201", success);
    }

    private void addResponseIfMissing(
            io.swagger.v3.oas.models.Operation operation,
            String status,
            String description
    ) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
        io.swagger.v3.oas.models.media.MediaType mediaType =
                new io.swagger.v3.oas.models.media.MediaType().schema(schema);
        operation.getResponses().addApiResponse(
                status,
                new io.swagger.v3.oas.models.responses.ApiResponse()
                        .description(description)
                        .content(new io.swagger.v3.oas.models.media.Content()
                                .addMediaType(MediaType.APPLICATION_JSON_VALUE, mediaType))
        );
    }

    private Schema<?> errorResponseSchema() {
        Schema<?> fieldViolation = new ObjectSchema()
                .addProperty("field", new StringSchema().example("email"))
                .addProperty("message", new StringSchema().example("Email không đúng định dạng"));
        return new ObjectSchema()
                .description("Cấu trúc lỗi thống nhất của REST API")
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("code", new StringSchema().example(ErrorCode.VALIDATION_FAILED.name()))
                .addProperty("message", new StringSchema().example(ErrorCode.VALIDATION_FAILED.getDefaultMessage()))
                .addProperty("errors", new ArraySchema().items(fieldViolation).nullable(true))
                .addProperty("path", new StringSchema().example("/api/auth/register"));
    }

    private String apiDescription() {
        StringBuilder description = new StringBuilder("""
                Backend modular monolith cho hệ thống quản lý phòng khám nha khoa.

                Các API bảo vệ sử dụng header `Authorization: Bearer <access-token>`.
                Thời gian nghiệp vụ sử dụng múi giờ `Asia/Ho_Chi_Minh`; timestamp trao đổi theo ISO-8601.

                ### Error codes

                | Code | HTTP | Message mặc định |
                |---|---:|---|
                """);
        Arrays.stream(ErrorCode.values()).forEach(errorCode -> description
                .append('|').append(errorCode.name())
                .append('|').append(errorCode.getHttpStatus().value())
                .append('|').append(errorCode.getDefaultMessage()).append("|\n"));
        return description.toString();
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/")
                || path.equals("/api/doctors")
                || path.startsWith("/api/doctors/")
                || path.equals("/api/services")
                || path.startsWith("/api/services/");
    }

    private String resolveTag(String path) {
        if (path.startsWith("/api/auth/")) {
            return "Authentication";
        }
        if (path.startsWith("/api/patients/")) {
            return "Patient Profile";
        }
        if (path.startsWith("/api/admin/dashboard")) {
            return "Admin Dashboard";
        }
        if (path.startsWith("/api/admin/users")) {
            return "Admin Users";
        }
        if (path.startsWith("/api/admin/appointments")) {
            return "Admin Appointments";
        }
        if (path.startsWith("/api/admin/doctors")) {
            if (path.contains("/days-off")) {
                return "Doctor Days Off";
            }
            return path.contains("/services") ? "Doctor Services" : "Doctors";
        }
        if (path.startsWith("/api/admin/services")) {
            return "Dental Services";
        }
        if (path.startsWith("/api/patient/medical-records")) {
            return "Patient Medical History";
        }
        if (path.startsWith("/api/patient/appointments")) {
            return "Patient Appointments";
        }
        if (path.startsWith("/api/doctor/appointments")) {
            return "Doctor Appointments";
        }
        if (path.startsWith("/api/doctor/schedules")) {
            return "Doctor Schedules";
        }
        if (path.contains("available-slots")) {
            return "Available Slots";
        }
        if (path.startsWith("/api/doctors") && path.contains("/days-off")) {
            return "Doctor Days Off";
        }
        if (path.endsWith("/schedule")) {
            return "Doctor Schedules";
        }
        if (path.contains("/services") && path.contains("/doctors")) {
            return "Doctor Services";
        }
        if (path.startsWith("/api/doctors")) {
            return "Doctors";
        }
        if (path.startsWith("/api/services")) {
            return "Dental Services";
        }
        return "Dental Clinic";
    }
}
