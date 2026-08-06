package com.xiaoyang.aiticketplatform.ratelimit;

import com.xiaoyang.aiticketplatform.config.LoginRateLimitProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class LoginRateLimitKeyGenerator {

    private static final String UNKNOWN_REMOTE_ADDRESS = "unknown";

    private final LoginRateLimitProperties properties;

    public LoginRateLimitKeyGenerator(LoginRateLimitProperties properties) {
        this.properties = properties;
    }

    public String ipKey(String remoteAddress) {
        String normalizedAddress = normalizeRemoteAddress(remoteAddress);
        return properties.getKeyPrefix() + ":ip:" + sha256(normalizedAddress);
    }

    public String usernameKey(String username) {
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        return properties.getKeyPrefix() + ":username:" + sha256(normalizedUsername);
    }

    private String normalizeRemoteAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return UNKNOWN_REMOTE_ADDRESS;
        }
        return remoteAddress.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
