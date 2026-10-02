package seatReservationSystem.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserIdentity {

    public String getUserId(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            throw new UnauthorizedException("Missing Authorization header");
        }

        if (authorization.regionMatches(
                true,
                0,
                "Bearer ",
                0,
                7
        )) {
            String token = authorization.substring(7).trim();
            if (!StringUtils.hasText(token)) {
                throw new UnauthorizedException("Empty bearer token");
            }
            return token;
        }

        throw new UnauthorizedException(
                "Authorization must be Bearer <user_id>"
        );
    }
}
