package com.xiaoyang.aiticketplatform.service.impl;

import com.xiaoyang.aiticketplatform.config.JwtProperties;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.security.IssuedAccessToken;
import com.xiaoyang.aiticketplatform.service.JwtTokenService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenServiceImpl(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issueAccessToken(UserAccount userAccount) {
        Objects.requireNonNull(userAccount, "userAccount 不能为空");
        Long userId = Objects.requireNonNull(userAccount.getId(), "用户 ID 不能为空");
        String username = Objects.requireNonNull(userAccount.getUsername(), "用户名不能为空");
        String role = Objects.requireNonNull(userAccount.getRole(), "用户角色不能为空").name();

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.getAccessTokenTtl());
        String jwtId = UUID.randomUUID().toString();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(jwtId)
                .claim("username", username)
                .claim("role", role)
                .build();

        String tokenValue = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
        return new IssuedAccessToken(tokenValue, issuedAt, expiresAt);
    }
}
