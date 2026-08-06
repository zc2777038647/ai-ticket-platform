package com.xiaoyang.aiticketplatform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.rate-limit.login")
public class LoginRateLimitProperties {

    private boolean enabled = true;

    @NotBlank
    private String keyPrefix = "ai-ticket:rate-limit:login";

    @Valid
    @NotNull
    private Limit ip = new Limit(20, 60);

    @Valid
    @NotNull
    private Limit username = new Limit(10, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Limit getIp() {
        return ip;
    }

    public void setIp(Limit ip) {
        this.ip = ip;
    }

    public Limit getUsername() {
        return username;
    }

    public void setUsername(Limit username) {
        this.username = username;
    }

    public static class Limit {

        @Min(1)
        private long maxRequests;

        @Min(1)
        private long windowSeconds;

        public Limit() {
        }

        public Limit(long maxRequests, long windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }

        public long getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(long maxRequests) {
            this.maxRequests = maxRequests;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
