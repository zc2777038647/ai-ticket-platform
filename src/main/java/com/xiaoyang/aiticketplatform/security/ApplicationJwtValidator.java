package com.xiaoyang.aiticketplatform.security;

import com.xiaoyang.aiticketplatform.enums.UserRole;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class ApplicationJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "JWT 必要声明无效",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!hasPositiveLongSubject(jwt)
                || isBlank(jwt.getClaimAsString("username"))
                || !hasValidRole(jwt)
                || isBlank(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasPositiveLongSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (isBlank(subject)) {
            return false;
        }
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean hasValidRole(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (isBlank(role)) {
            return false;
        }
        try {
            UserRole.valueOf(role);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
