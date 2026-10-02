package seatReservationSystem.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminAuth {

    private final String adminToken;

    public AdminAuth(
            @Value("${app.admin-token:admin-secret}")
            String adminToken
    ) {
        this.adminToken = adminToken;
    }

    public void requireAdmin(HttpServletRequest request) {
        String provided = request.getHeader("X-Admin-Token");
        if (!StringUtils.hasText(provided) || !adminToken.equals(provided.trim())) {
            throw new ForbiddenException("Admin access required");
        }
    }
}
