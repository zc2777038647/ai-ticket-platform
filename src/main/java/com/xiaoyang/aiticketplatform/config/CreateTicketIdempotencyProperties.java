package com.xiaoyang.aiticketplatform.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.idempotency.create-ticket")
public class CreateTicketIdempotencyProperties {

    private boolean enabled = true;

    @NotBlank
    private String keyPrefix = "ai-ticket:idempotency:create-ticket";

    @Min(1)
    private long processingTtlSeconds = 120;

    @Min(1)
    private long successTtlSeconds = 86400;

    @Min(1)
    private int minimumKeyLength = 8;

    @Min(1)
    private int maximumKeyLength = 128;

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

    public long getProcessingTtlSeconds() {
        return processingTtlSeconds;
    }

    public void setProcessingTtlSeconds(long processingTtlSeconds) {
        this.processingTtlSeconds = processingTtlSeconds;
    }

    public long getSuccessTtlSeconds() {
        return successTtlSeconds;
    }

    public void setSuccessTtlSeconds(long successTtlSeconds) {
        this.successTtlSeconds = successTtlSeconds;
    }

    public int getMinimumKeyLength() {
        return minimumKeyLength;
    }

    public void setMinimumKeyLength(int minimumKeyLength) {
        this.minimumKeyLength = minimumKeyLength;
    }

    public int getMaximumKeyLength() {
        return maximumKeyLength;
    }

    public void setMaximumKeyLength(int maximumKeyLength) {
        this.maximumKeyLength = maximumKeyLength;
    }

    @AssertTrue(message = "幂等键最大长度不能小于最小长度")
    public boolean isKeyLengthRangeValid() {
        return maximumKeyLength >= minimumKeyLength;
    }
}
