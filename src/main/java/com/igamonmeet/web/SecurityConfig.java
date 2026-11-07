package com.igamonmeet.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Skeleton only: everything except the health check and the static UI requires sign-in.
 * Local accounts, registration and lockout are added by the accounts tickets (see docs/spec.md §1).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeRequests(auth -> auth
                .antMatchers("/api/health", "/", "/index.html", "/assets/**").permitAll()
                .anyRequest().authenticated())
            // The React app handles sign-in; the API answers 401 instead of redirecting.
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
