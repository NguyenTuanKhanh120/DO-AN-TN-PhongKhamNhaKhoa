package com.dentalclinic.common.exception;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn(
                "Business request rejected: code={}, method={}, path={}",
                errorCode.name(),
                request.getMethod(),
                request.getRequestURI()
        );
        return errorResponse(errorCode, exception.getMessage(), null, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindingException(
            BindException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new FieldViolation(error.getField(), resolveFieldMessage(error)))
                .distinct()
                .toList();

        log.warn(
                "Request validation failed: fields={}, method={}, path={}",
                errors.stream().map(FieldViolation::field).distinct().toList(),
                request.getMethod(),
                request.getRequestURI()
        );
        return errorResponse(ErrorCode.VALIDATION_FAILED, null, errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return errorResponse(ErrorCode.VALIDATION_FAILED, null, errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage() == null
                                        ? ErrorCode.VALIDATION_FAILED.getDefaultMessage()
                                        : error.getDefaultMessage()
                        )))
                .toList();
        return errorResponse(ErrorCode.VALIDATION_FAILED, null, errors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = List.of(new FieldViolation(
                exception.getName(),
                "Giá trị không đúng kiểu dữ liệu"
        ));
        return errorResponse(ErrorCode.INVALID_REQUEST, null, errors, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = List.of(new FieldViolation(
                exception.getParameterName(),
                "Tham số bắt buộc chưa được cung cấp"
        ));
        return errorResponse(ErrorCode.INVALID_REQUEST, null, errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.warn("Malformed request body: method={}, path={}", request.getMethod(), request.getRequestURI());
        return errorResponse(ErrorCode.MALFORMED_REQUEST, null, null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return errorResponse(ErrorCode.METHOD_NOT_ALLOWED, null, null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Database constraint rejected request: method={}, path={}",
                request.getMethod(),
                request.getRequestURI()
        );
        return errorResponse(ErrorCode.RESOURCE_CONFLICT, null, null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Method authorization rejected request: method={}, path={}",
                request.getMethod(),
                request.getRequestURI()
        );
        return errorResponse(ErrorCode.ACCESS_DENIED, null, null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected request failure: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR, null, null, request);
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(
            ErrorCode errorCode,
            String message,
            List<FieldViolation> errors,
            HttpServletRequest request
    ) {
        ApiResponse<Void> body = ApiResponse.error(
                errorCode,
                message,
                errors,
                request.getRequestURI()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    private String resolveFieldMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() == null
                ? ErrorCode.VALIDATION_FAILED.getDefaultMessage()
                : fieldError.getDefaultMessage();
    }
}
