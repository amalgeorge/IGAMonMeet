package com.igamonmeet.web;

import com.igamonmeet.account.AppUser;
import com.igamonmeet.account.AppUserRepository;
import com.igamonmeet.account.UserStatus;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Re-checks the signed-in User on every API call:
 * a User disabled by an Admin loses the session at once, and a User with a temporary password
 * can only change it (or sign out) until they do.
 */
@Component
public class AccountStateGuard implements HandlerInterceptor, WebMvcConfigurer {

    private final AppUserRepository users;

    public AccountStateGuard(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/csrf", "/api/auth/register", "/api/auth/forgot");
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return true;
        }
        // Every real principal is a stored User; absence only happens with test doubles.
        Optional<AppUser> user = users.findByUsernameIgnoreCase(auth.getName());
        if (user.isEmpty()) {
            return true;
        }
        AppUser u = user.get();
        if (u.getStatus() != UserStatus.ACTIVE) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            write(res, 401, "{\"error\":\"SIGNED_OUT\",\"message\":\"Your account is no longer active.\"}");
            return false;
        }
        String uri = req.getRequestURI();
        if (u.isMustChangePassword() && !uri.endsWith("/api/account/password") && !uri.endsWith("/api/auth/me")) {
            write(res, 403, "{\"error\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Set a new password to continue.\"}");
            return false;
        }
        return true;
    }

    private static void write(HttpServletResponse res, int status, String body) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(body);
    }
}
