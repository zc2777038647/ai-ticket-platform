package com.xiaoyang.aiticketplatform.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class ApplicationJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                "ROLE_" + jwt.getClaimAsString("role")
        );
        return new JwtAuthenticationToken(jwt, List.of(authority), jwt.getSubject());
    }
}
