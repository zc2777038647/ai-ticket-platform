package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.LoginRequest;
import com.xiaoyang.aiticketplatform.dto.request.RegisterRequest;
import com.xiaoyang.aiticketplatform.dto.response.LoginResponse;
import com.xiaoyang.aiticketplatform.dto.response.UserResponse;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import com.xiaoyang.aiticketplatform.security.IssuedAccessToken;
import com.xiaoyang.aiticketplatform.service.AuthService;
import com.xiaoyang.aiticketplatform.service.JwtTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthServiceImpl(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = request.displayName().trim();

        long existingUsers = userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getUsername, normalizedUsername)
        );
        if (existingUsers > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(normalizedUsername);
        userAccount.setPasswordHash(passwordEncoder.encode(request.password()));
        userAccount.setDisplayName(normalizedDisplayName);
        userAccount.setRole(UserRole.USER);

        int affectedRows;
        try {
            affectedRows = userAccountMapper.insert(userAccount);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        if (affectedRows != 1) {
            throw new IllegalStateException("注册用户失败：数据库插入影响行数不是 1");
        }
        if (userAccount.getId() == null) {
            throw new IllegalStateException("注册用户失败：数据库自增 ID 未回填");
        }

        return toUserResponse(userAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getUsername, normalizedUsername)
        );
        if (userAccount == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                userAccount.getPasswordHash()
        );
        if (!passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        IssuedAccessToken issuedToken = jwtTokenService.issueAccessToken(userAccount);
        long expiresIn = Duration.between(
                issuedToken.issuedAt(),
                issuedToken.expiresAt()
        ).toSeconds();

        return new LoginResponse(
                issuedToken.tokenValue(),
                "Bearer",
                expiresIn,
                toUserResponse(userAccount)
        );
    }

    private UserResponse toUserResponse(UserAccount userAccount) {
        return new UserResponse(
                userAccount.getId(),
                userAccount.getUsername(),
                userAccount.getDisplayName(),
                userAccount.getRole()
        );
    }
}
