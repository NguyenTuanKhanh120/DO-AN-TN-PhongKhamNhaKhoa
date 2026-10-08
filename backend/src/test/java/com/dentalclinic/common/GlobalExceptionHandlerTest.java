package com.dentalclinic.common;

import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void shouldReturnNotFoundResponseForBusinessException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy lịch hẹn"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnConflictResponse() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void shouldReturnFieldErrorsForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Tên không được để trống"));
    }

    @Test
    void shouldReturnSafeErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Nội dung yêu cầu không hợp lệ"));
    }

    @Test
    void shouldReturnTypeMismatchWithoutEchoingRejectedValue() throws Exception {
        mockMvc.perform(get("/test/page").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].message").value("Giá trị không đúng kiểu dữ liệu"));
    }

    @Test
    void shouldReturnMissingParameterError() throws Exception {
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void shouldReturnMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldReturnForbiddenForMethodAuthorizationFailure() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @GetMapping("/not-found")
        String notFound() {
            throw new NotFoundException("Không tìm thấy lịch hẹn");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ConflictException("Khung giờ đã được đặt");
        }

        @PostMapping("/validation")
        String validation(@Valid @RequestBody TestRequest request) {
            return request.name();
        }

        @GetMapping("/page")
        int page(@RequestParam Integer page) {
            return page;
        }

        @GetMapping("/forbidden")
        String forbidden() {
            throw new AuthorizationDeniedException("denied");
        }
    }

    private record TestRequest(
            @NotBlank(message = "Tên không được để trống") String name
    ) {
    }
}
