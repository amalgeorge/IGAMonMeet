package com.igamonmeet.web;

import com.igamonmeet.account.AccountException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every API error is {"error": CODE, "message": text} so the UI can switch on the code. */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(AccountException.class)
    ResponseEntity<Map<String, String>> account(AccountException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCode(), "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldError() != null ? e.getBindingResult().getFieldError().getField() : "";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID", "message", "Check the " + field + " field.", "field", field));
    }
}
