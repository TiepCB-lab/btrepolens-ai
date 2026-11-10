package com.btrepolens.common;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiResponse<Void>> application(AppException e) { return ResponseEntity.status(e.status()).body(ApiResponse.error(e.getMessage(), e.code())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request parameters or body", "VALIDATION_ERROR")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("Repository already exists or selection changed concurrently", "CONFLICT")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR")); }
}
