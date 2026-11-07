package com.igamonmeet.account;

import org.springframework.security.authentication.AccountStatusException;

public class PendingApprovalException extends AccountStatusException {
    public PendingApprovalException() {
        super("Awaiting admin approval");
    }
}
