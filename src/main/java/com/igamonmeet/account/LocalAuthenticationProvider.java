package com.igamonmeet.account;

import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks the password before revealing anything about the account, so a wrong password, an unknown
 * username and a locked account all give the same answer. Only a correct password learns that the
 * account is pending or disabled.
 */
@Component
public class LocalAuthenticationProvider implements AuthenticationProvider {

    private static final String GENERIC = "Incorrect username or password, or the account is temporarily locked.";

    private final AppUserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder encoder;
    /** Compared against when the username is unknown, so response time does not reveal it. */
    private final String dummyHash;

    public LocalAuthenticationProvider(AppUserRepository users, AccountService accounts, PasswordEncoder encoder) {
        this.users = users;
        this.accounts = accounts;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("not-a-real-password-placeholder");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = String.valueOf(authentication.getPrincipal()).trim();
        String password = String.valueOf(authentication.getCredentials());

        AppUser user = users.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw new BadCredentialsException(GENERIC);
        }
        if (user.isLockedAt(accounts.now())) {
            throw new BadCredentialsException(GENERIC);
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            accounts.recordFailedSignIn(user.getId());
            throw new BadCredentialsException(GENERIC);
        }
        accounts.recordSuccessfulSignIn(user.getId());
        if (user.getStatus() == UserStatus.PENDING) {
            throw new PendingApprovalException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
        return new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
