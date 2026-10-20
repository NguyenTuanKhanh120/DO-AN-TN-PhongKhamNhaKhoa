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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final Set<String> CREATED_ENDPOINTS = Set.of(
            "POST /api/auth/register",
            "POST /api/admin/staff",
            "POST /api/admin/doctors",
            "POST /api/admin/services",
            "POST /api/admin/doctors/{doctorId}/schedules",
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
                        new Tag().name("Admin Staff").description("Quản lý tài khoản nhân viên lễ tân - thu ngân"),
                        new Tag().name("Doctors").description("Tra cứu và quản lý bác sĩ"),
                        new Tag().name("Dental Services").description("Tra cứu và quản lý dịch vụ nha khoa"),
                        new Tag().name("Doctor Services").description("Phân công dịch vụ cho bác sĩ"),
                        new Tag().name("Doctor Schedules").description(
                                "Admin quản lý lịch làm việc tuần; bác sĩ chỉ xem lịch của mình"
                        ),
                        new Tag().name("Doctor Days Off").description("Ngoại lệ nghỉ theo ngày do quản trị viên đăng ký"),
                        new Tag().name("Available Slots").description("Khung giờ khám còn trống"),
                        new Tag().name("Patient Appointments").description("Lịch hẹn của bệnh nhân"),
                        new Tag().name("Doctor Appointments").description("Quy trình khám của bác sĩ"),
                        new Tag().name("Doctor Payments").description("Bác sĩ ghi nhận dịch vụ thực tế"),
                        new Tag().name("Patient Payments").description("Bệnh nhân xem phiếu thanh toán"),
                        new Tag().name("Staff Appointments").description("Nhân viên tra cứu lịch khám toàn phòng khám"),
                        new Tag().name("Staff Payments").description("Nhân viên thu tiền trực tiếp tại quầy"),
                        new Tag().name("Admin Payments").description("Admin tra cứu và xác nhận thanh toán dự phòng"),
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
                        addExecutionResultNote(path, operation);

                        normalizeCreatedResponse(path, method.name(), operation);
                        addSuccessExample(path, method.name(), operation);
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

    private void addSuccessExample(
            String path,
            String method,
            io.swagger.v3.oas.models.Operation operation
    ) {
        String status = CREATED_ENDPOINTS.contains(method + " " + path) ? "201" : "200";
        io.swagger.v3.oas.models.responses.ApiResponse response = operation.getResponses().get(status);
        if (response == null || response.getContent() == null) {
            return;
        }
        io.swagger.v3.oas.models.media.MediaType mediaType =
                response.getContent().get(MediaType.APPLICATION_JSON_VALUE);
        if (mediaType != null) {
            mediaType.setExample(successExample(path));
            if (path.equals(LOGIN_ENDPOINT)) {
                response.setDescription("Đăng nhập thành công; JWT được trả tại data.accessToken");
            }
        }
    }

    private void addExecutionResultNote(
            String path,
            io.swagger.v3.oas.models.Operation operation
    ) {
        if (!path.equals(LOGIN_ENDPOINT)) {
            return;
        }
        operation.setDescription("""
                Sau khi bấm **Execute**, kết quả thực tế của lần gọi nằm trong mục **Server response**.

                Bảng **Responses** bên dưới là tài liệu các kết quả *có thể xảy ra*, không phải các kết quả
                được trả đồng thời. Khi đăng nhập thành công, JWT nằm tại `data.accessToken` của response 200.
                """);
    }

    private Map<String, Object> successExample(String path) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", true);
        example.put("code", ErrorCode.SUCCESS.name());
        if (path.equals(LOGIN_ENDPOINT) || path.equals(REGISTER_ENDPOINT)) {
            boolean login = path.equals(LOGIN_ENDPOINT);
            example.put("message", login
                    ? "Đăng nhập thành công"
                    : "Đăng ký tài khoản thành công");
            example.put("data", authResponseExample(path));
        } else {
            example.put("message", ErrorCode.SUCCESS.getDefaultMessage());
            example.put("data", Map.of());
        }
        return example;
    }

    private Map<String, Object> authResponseExample(String path) {
        boolean login = path.equals(LOGIN_ENDPOINT);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", "<JWT_ACCESS_TOKEN>");
        data.put("tokenType", "Bearer");
        data.put("expiresInSeconds", 3600);
        data.put("userId", login ? "<USER_ID>" : "00000000-0000-0000-0000-000000000000");
        data.put("email", login ? "<EMAIL>" : "patient@example.com");
        data.put("fullName", login ? "<FULL_NAME>" : "Nguyễn Văn A");
        data.put("role", login ? "<PATIENT|DOCTOR|ADMIN|STAFF>" : "PATIENT");
        return data;
    }

    private Schema<?> errorResponseSchema() {
        Schema<?> fieldViolation = new ObjectSchema()
                .addProperty("field", new StringSchema().description("Tên field không hợp lệ"))
                .addProperty("message", new StringSchema().description("Chi tiết lỗi validation"));
        return new ObjectSchema()
                .description("Cấu trúc lỗi thống nhất của REST API")
                .addProperty("success", new BooleanSchema().description("Luôn false khi có lỗi"))
                .addProperty("code", new StringSchema().description("Mã lỗi nghiệp vụ"))
                .addProperty("message", new StringSchema().description("Thông báo lỗi"))
                .addProperty("errors", new ArraySchema().items(fieldViolation).nullable(true))
                .addProperty("path", new StringSchema().description("Endpoint phát sinh lỗi"));
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
        if (path.startsWith("/api/admin/payments")) {
            return "Admin Payments";
        }
        if (path.startsWith("/api/admin/staff")) {
            return "Admin Staff";
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
            if (path.contains("/schedules")) {
                return "Doctor Schedules";
            }
            return path.contains("/services") ? "Doctor Services" : "Doctors";
        }
        if (path.startsWith("/api/admin/services")) {
            return "Dental Services";
        }
        if (path.startsWith("/api/patient/medical-records")) {
            return "Patient Medical History";
        }
        if (path.startsWith("/api/patient/payments")) {
            return "Patient Payments";
        }
        if (path.startsWith("/api/patient/appointments")) {
            return "Patient Appointments";
        }
        if (path.startsWith("/api/staff/appointments")) {
            return "Staff Appointments";
        }
        if (path.startsWith("/api/staff/payments")) {
            return "Staff Payments";
        }
        if (path.startsWith("/api/doctor/appointments")) {
            return path.contains("/payment") ? "Doctor Payments" : "Doctor Appointments";
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
