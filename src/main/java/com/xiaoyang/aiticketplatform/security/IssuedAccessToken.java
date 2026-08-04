package com.xiaoyang.aiticketplatform.security;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccessToken(
        String tokenValue,
        Instant issuedAt,
        Instant expiresAt
) {
    public IssuedAccessToken {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new IllegalArgumentException("Access Token 不能为空");
        }
        Objects.requireNonNull(issuedAt, "issuedAt 不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 issuedAt");
        }
    }
}
