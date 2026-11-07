package com.igamonmeet.web;

import com.igamonmeet.account.LocalAuthenticationProvider;
import com.igamonmeet.account.PendingApprovalException;
import java.time.Clock;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Local accounts (ADR 0002). The React app signs in with POST /api/auth/login and gets JSON back. */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LocalAuthenticationProvider provider) throws Exception {
        // Own manager with no parent: otherwise a failed sign-in is retried by the global manager
        // and every wrong password is counted (and audited) twice.
        http.authenticationManager(new ProviderManager(provider))
            .authorizeRequests(auth -> auth
                .antMatchers("/api/health", "/api/auth/csrf", "/api/auth/register", "/api/auth/login").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .formLogin(f -> f
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, a) -> json(res, HttpStatus.OK, "{\"status\":\"OK\"}"))
                .failureHandler(failureHandler()))
            .logout(l -> l
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            // Session ID is replaced at sign-in (Spring's default migrateSession), defeating fixation.
            .sessionManagement(s -> s.sessionFixation().migrateSession())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    private static AuthenticationFailureHandler failureHandler() {
        return (req, res, ex) -> {
            if (ex instanceof PendingApprovalException) {
                json(res, HttpStatus.FORBIDDEN, "{\"error\":\"PENDING\",\"message\":\"Awaiting admin approval.\"}");
            } else if (ex instanceof DisabledException) {
                json(res, HttpStatus.FORBIDDEN, "{\"error\":\"DISABLED\",\"message\":\"This account is not active. Contact an Admin.\"}");
            } else {
                json(res, HttpStatus.UNAUTHORIZED,
                        "{\"error\":\"BAD_CREDENTIALS\",\"message\":\"Incorrect username or password, or the account is temporarily locked.\"}");
            }
        };
    }

    private static void json(HttpServletResponse res, HttpStatus status, String body) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(body);
    }
}
