package com.igamonmeet.account;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "password_reset_request")
public class PasswordResetRequest {

    public static final String OPEN = "OPEN";
    public static final String DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Instant requestedAt;
    private String status;
    private String handledBy;
    private Instant handledAt;

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(Long userId, Instant requestedAt) {
        this.userId = userId;
        this.requestedAt = requestedAt;
        this.status = OPEN;
    }

    public void close(String admin, Instant at) {
        this.status = DONE;
        this.handledBy = admin;
        this.handledAt = at;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getRequestedAt() { return requestedAt; }
}
