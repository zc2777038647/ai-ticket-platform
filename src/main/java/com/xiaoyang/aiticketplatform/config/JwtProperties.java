package com.xiaoyang.aiticketplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public final class JwtProperties {

    private final String issuer;
    private final Duration accessTokenTtl;
    private final String secretBase64;

    public JwtProperties(
            String issuer,
            Duration accessTokenTtl,
            String secretBase64
    ) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer 不能为空");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT Access Token 有效期必须为正数");
        }
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("JWT 密钥配置不能为空");
        }
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.secretBase64 = secretBase64;
    }

    public String getIssuer() {
        return issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public String getSecretBase64() {
        return secretBase64;
    }
}
