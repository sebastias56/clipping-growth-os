package com.clippinggrowth.api;

import java.util.Comparator;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Comparator<ValidationMessage> VALIDATION_MESSAGE_ORDER =
            Comparator.comparing(ValidationMessage::location)
                    .thenComparing(ValidationMessage::message);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationMessage(
                        validationLocation(error), error.getDefaultMessage()))
                .filter(validation -> validation.message() != null
                        && !validation.message().isBlank())
                .min(VALIDATION_MESSAGE_ORDER)
                .map(ValidationMessage::message)
                .orElse("Request validation failed");
        return invalidRequest(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleMalformedBody() {
        return invalidRequest("Request body is malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidRequest("Invalid value for " + exception.getName());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException exception) {
        String detail = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationMessage(
                                parameterName(result), error.getDefaultMessage())))
                .filter(validation -> validation.message() != null
                        && !validation.message().isBlank())
                .min(VALIDATION_MESSAGE_ORDER)
                .map(ValidationMessage::message)
                .orElse("Request validation failed");
        return invalidRequest(detail);
    }

    private ResponseEntity<ProblemDetail> invalidRequest(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String validationLocation(ObjectError error) {
        return error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();
    }

    private static String parameterName(ParameterValidationResult result) {
        String parameterName = result.getMethodParameter().getParameterName();
        return parameterName == null ? "parameter" : parameterName;
    }

    private record ValidationMessage(String location, String message) {
    }
}
