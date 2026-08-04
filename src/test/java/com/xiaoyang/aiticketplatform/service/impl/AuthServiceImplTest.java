package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.xiaoyang.aiticketplatform.service.JwtTokenService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String TEST_HASH = "{test-hash}encoded-value";

    @BeforeAll
    static void initializeMybatisPlusTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                UserAccount.class
        );
    }

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldRegisterNormalizedUserAndReturnSafeResponse() {
        RegisterRequest request = validRequest();
        when(userAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(request.password())).thenReturn(TEST_HASH);
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount userAccount = invocation.getArgument(0);
            assertNull(userAccount.getId());
            userAccount.setId(100L);
            return 1;
        });

        UserResponse response = authService.register(request);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper, times(1)).selectCount(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, times(1)).encode(request.password());
        verify(userAccountMapper, times(1)).insert(userCaptor.capture());
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder);

        UserAccount insertedUser = userCaptor.getValue();
        assertAll(
                () -> assertEquals("test_user", insertedUser.getUsername()),
                () -> assertTrue(TEST_HASH.equals(insertedUser.getPasswordHash()),
                        "应保存 PasswordEncoder 返回的结果"),
                () -> assertEquals("测试用户", insertedUser.getDisplayName()),
                () -> assertEquals(UserRole.USER, insertedUser.getRole()),
                () -> assertNull(insertedUser.getCreatedAt()),
                () -> assertNull(insertedUser.getUpdatedAt()),
                () -> assertEquals(100L, response.id()),
                () -> assertEquals("test_user", response.username()),
                () -> assertEquals("测试用户", response.displayName()),
                () -> assertEquals(UserRole.USER, response.role())
        );
    }

    @Test
    void shouldRejectUsernameFoundByPrecheckWithoutEncodingOrInsert() {
        when(userAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(validRequest())
        );

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(userAccountMapper, times(1)).selectCount(any(LambdaQueryWrapper.class));
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
        verifyNoMoreInteractions(userAccountMapper);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldConvertDuplicateKeyRaceToBusinessException() {
        RegisterRequest request = validRequest();
        when(userAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(request.password())).thenReturn(TEST_HASH);
        when(userAccountMapper.insert(any(UserAccount.class)))
                .thenThrow(new DuplicateKeyException("duplicate test username"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(request)
        );

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(userAccountMapper, times(1)).selectCount(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, times(1)).encode(request.password());
        verify(userAccountMapper, times(1)).insert(any(UserAccount.class));
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder);
    }

    @Test
    void shouldThrowWhenInsertDoesNotAffectExactlyOneRow() {
        RegisterRequest request = validRequest();
        when(userAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(request.password())).thenReturn(TEST_HASH);
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> authService.register(request)
        );

        assertEquals("注册用户失败：数据库插入影响行数不是 1", exception.getMessage());
        verify(userAccountMapper, times(1)).selectCount(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, times(1)).encode(request.password());
        verify(userAccountMapper, times(1)).insert(any(UserAccount.class));
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder);
    }

    @Test
    void shouldThrowWhenGeneratedIdIsNotBackfilled() {
        RegisterRequest request = validRequest();
        when(userAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(request.password())).thenReturn(TEST_HASH);
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> authService.register(request)
        );

        assertEquals("注册用户失败：数据库自增 ID 未回填", exception.getMessage());
        verify(userAccountMapper, times(1)).selectCount(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, times(1)).encode(request.password());
        verify(userAccountMapper, times(1)).insert(any(UserAccount.class));
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder);
    }

    @Test
    void shouldLoginWithNormalizedUsernameAndReturnAccessToken() {
        LoginRequest request = new LoginRequest("Test_User", " raw-test-secret ");
        UserAccount userAccount = existingUser();
        IssuedAccessToken issuedToken = issuedToken();
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userAccount);
        when(passwordEncoder.matches(request.password(), TEST_HASH)).thenReturn(true);
        when(jwtTokenService.issueAccessToken(userAccount)).thenReturn(issuedToken);

        LoginResponse response = authService.login(request);

        ArgumentCaptor<LambdaQueryWrapper<UserAccount>> wrapperCaptor = ArgumentCaptor.forClass(
                LambdaQueryWrapper.class
        );
        verify(userAccountMapper, times(1)).selectOne(wrapperCaptor.capture());
        verify(passwordEncoder, times(1)).matches(request.password(), TEST_HASH);
        verify(jwtTokenService, times(1)).issueAccessToken(userAccount);
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder, jwtTokenService);

        LambdaQueryWrapper<UserAccount> queryWrapper = wrapperCaptor.getValue();
        queryWrapper.getSqlSegment();
        assertAll(
                () -> assertTrue(queryWrapper.getParamNameValuePairs().containsValue("test_user")),
                () -> assertTrue(issuedToken.tokenValue().equals(response.accessToken()),
                        "登录响应应包含签发的 Access Token"),
                () -> assertEquals("Bearer", response.tokenType()),
                () -> assertEquals(7200L, response.expiresIn()),
                () -> assertEquals(100L, response.user().id()),
                () -> assertEquals("test_user", response.user().username()),
                () -> assertEquals("测试用户", response.user().displayName()),
                () -> assertEquals(UserRole.USER, response.user().role())
        );
    }

    @Test
    void shouldRejectLoginWhenUserDoesNotExist() {
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("Missing_User", " raw-test-secret "))
        );

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verify(userAccountMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
        verifyNoMoreInteractions(userAccountMapper);
        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void shouldRejectLoginWhenPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("Test_User", " wrong-test-secret ");
        UserAccount userAccount = existingUser();
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userAccount);
        when(passwordEncoder.matches(request.password(), TEST_HASH)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(request)
        );

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verify(userAccountMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, times(1)).matches(request.password(), TEST_HASH);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void shouldPropagateTokenIssuanceSystemFailure() {
        LoginRequest request = new LoginRequest("Test_User", " raw-test-secret ");
        UserAccount userAccount = existingUser();
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userAccount);
        when(passwordEncoder.matches(request.password(), TEST_HASH)).thenReturn(true);
        when(jwtTokenService.issueAccessToken(userAccount))
                .thenThrow(new IllegalStateException("token encoding failed"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> authService.login(request)
        );

        assertEquals("token encoding failed", exception.getMessage());
        verify(userAccountMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, times(1)).matches(request.password(), TEST_HASH);
        verify(jwtTokenService, times(1)).issueAccessToken(userAccount);
        verifyNoMoreInteractions(userAccountMapper, passwordEncoder, jwtTokenService);
    }

    private static RegisterRequest validRequest() {
        return new RegisterRequest(
                "Test_User",
                "P5_2_test_secret_value",
                " 测试用户 "
        );
    }

    private static UserAccount existingUser() {
        UserAccount userAccount = new UserAccount();
        userAccount.setId(100L);
        userAccount.setUsername("test_user");
        userAccount.setPasswordHash(TEST_HASH);
        userAccount.setDisplayName("测试用户");
        userAccount.setRole(UserRole.USER);
        return userAccount;
    }

    private static IssuedAccessToken issuedToken() {
        Instant issuedAt = Instant.parse("2026-08-04T12:00:00Z");
        return new IssuedAccessToken(
                "test.jwt.token",
                issuedAt,
                issuedAt.plusSeconds(7200)
        );
    }
}
