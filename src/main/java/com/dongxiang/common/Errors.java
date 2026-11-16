package com.dongxiang.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<Api<Void>> badRequest(Exception e) { return ResponseEntity.badRequest().body(Api.error(e.getMessage())); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Api<Void>> conflict(IllegalStateException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Api.error(e.getMessage())); }
}
