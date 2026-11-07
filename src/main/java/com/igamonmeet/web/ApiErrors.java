package com.igamonmeet.web;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.meeting.VersionConflictException;
import java.util.HashMap;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** The editor's copy is stale; the response carries the saved version so the user can pick. */
    @ExceptionHandler(VersionConflictException.class)
    ResponseEntity<Map<String, Object>> versionConflict(VersionConflictException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "VERSION_CONFLICT");
        body.put("message", e.getMessage());
        body.put("current", e.getCurrent());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> optimistic(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "VERSION_CONFLICT", "message", "Someone else saved at the same moment. Reload and try again."));
    }

    /** Two saves raced on a unique key (e.g. two Admins publishing at once). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "CONFLICT", "message", "Someone else saved at the same moment. Reload and try again."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldError() != null ? e.getBindingResult().getFieldError().getField() : "";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID", "message", "Check the " + field + " field.", "field", field));
    }
}
