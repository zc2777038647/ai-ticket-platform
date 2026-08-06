package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.config.CreateTicketIdempotencyProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class RedisCreateTicketIdempotencyStore {

    private static final String ACQUIRE_SCRIPT_PATH = "redis/create_ticket_idempotency_acquire.lua";
    private static final String COMPLETE_SCRIPT_PATH = "redis/create_ticket_idempotency_complete.lua";
    private static final String RELEASE_SCRIPT_PATH = "redis/create_ticket_idempotency_release.lua";
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAXIMUM_OWNER_TOKEN_LENGTH = 128;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CreateTicketIdempotencyProperties properties;
    private final CreateTicketIdempotencyKeyGenerator keyGenerator;
    private final DefaultRedisScript<String> acquireScript;
    private final DefaultRedisScript<Long> completeScript;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisCreateTicketIdempotencyStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            CreateTicketIdempotencyProperties properties,
            CreateTicketIdempotencyKeyGenerator keyGenerator
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.keyGenerator = keyGenerator;
        this.acquireScript = loadScript(ACQUIRE_SCRIPT_PATH, String.class);
        this.completeScript = loadScript(COMPLETE_SCRIPT_PATH, Long.class);
        this.releaseScript = loadScript(RELEASE_SCRIPT_PATH, Long.class);
    }

    public IdempotencyAcquireResult acquire(
            Long creatorUserId,
            String idempotencyKey,
            String fingerprint,
            String ownerToken
    ) {
        if (!properties.isEnabled()) {
            return new IdempotencyAcquireResult(IdempotencyAcquireStatus.DISABLED, 0, null);
        }

        String normalizedFingerprint = validateFingerprint(fingerprint);
        validateOwnerToken(ownerToken);
        String redisKey = keyGenerator.generateRedisKey(creatorUserId, idempotencyKey);
        String scriptResult = stringRedisTemplate.execute(
                acquireScript,
                List.of(redisKey),
                normalizedFingerprint,
                ownerToken,
                Long.toString(properties.getProcessingTtlSeconds())
        );
        if (scriptResult == null) {
            throw new IllegalStateException("Redis 幂等获取脚本未返回结果");
        }

        AcquireScriptResult parsed = parseAcquireResult(scriptResult);
        try {
            IdempotencyAcquireStatus status = IdempotencyAcquireStatus.valueOf(parsed.status());
            return new IdempotencyAcquireResult(
                    status,
                    parsed.retryAfterSeconds(),
                    parsed.responsePayload()
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Redis 幂等获取脚本返回了非法结果");
        }
    }

    public void markSucceeded(
            Long creatorUserId,
            String idempotencyKey,
            String fingerprint,
            String ownerToken,
            String responsePayload
    ) {
        if (!properties.isEnabled()) {
            return;
        }

        String normalizedFingerprint = validateFingerprint(fingerprint);
        validateOwnerToken(ownerToken);
        if (responsePayload == null || responsePayload.isBlank()) {
            throw new IllegalArgumentException("成功响应载荷不能为空");
        }
        String redisKey = keyGenerator.generateRedisKey(creatorUserId, idempotencyKey);
        Long result = stringRedisTemplate.execute(
                completeScript,
                List.of(redisKey),
                normalizedFingerprint,
                ownerToken,
                responsePayload,
                Long.toString(properties.getSuccessTtlSeconds())
        );
        requireCompleteSuccess(result);
    }

    public boolean releaseProcessing(
            Long creatorUserId,
            String idempotencyKey,
            String fingerprint,
            String ownerToken
    ) {
        if (!properties.isEnabled()) {
            return false;
        }

        String normalizedFingerprint = validateFingerprint(fingerprint);
        validateOwnerToken(ownerToken);
        String redisKey = keyGenerator.generateRedisKey(creatorUserId, idempotencyKey);
        Long result = stringRedisTemplate.execute(
                releaseScript,
                List.of(redisKey),
                normalizedFingerprint,
                ownerToken
        );
        if (result == null) {
            throw new IllegalStateException("Redis 幂等释放脚本未返回结果");
        }
        if (result == 1) {
            return true;
        }
        if (result == 0 || result == -1 || result == -2 || result == -3) {
            return false;
        }
        throw new IllegalStateException("Redis 幂等释放脚本返回了未知状态码");
    }

    private AcquireScriptResult parseAcquireResult(String scriptResult) {
        try {
            AcquireScriptResult parsed = objectMapper.readValue(scriptResult, AcquireScriptResult.class);
            if (parsed == null) {
                throw new IllegalStateException("Redis 幂等获取脚本返回了空解析结果");
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Redis 幂等获取脚本结果无法解析");
        }
    }

    private void requireCompleteSuccess(Long result) {
        if (result == null) {
            throw new IllegalStateException("Redis 幂等完成脚本未返回结果");
        }
        switch (result.intValue()) {
            case 1 -> { return; }
            case 0 -> throw new IllegalStateException("Redis 幂等完成失败：记录不存在");
            case -1 -> throw new IllegalStateException("Redis 幂等完成失败：状态不是处理中");
            case -2 -> throw new IllegalStateException("Redis 幂等完成失败：请求指纹不一致");
            case -3 -> throw new IllegalStateException("Redis 幂等完成失败：处理所有者不一致");
            default -> throw new IllegalStateException("Redis 幂等完成脚本返回了未知状态码");
        }
    }

    private String validateFingerprint(String fingerprint) {
        if (fingerprint == null || !SHA_256_HEX.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("请求指纹必须是 64 位十六进制 SHA-256");
        }
        return fingerprint.toLowerCase(Locale.ROOT);
    }

    private void validateOwnerToken(String ownerToken) {
        if (ownerToken == null || ownerToken.isBlank()
                || ownerToken.length() > MAXIMUM_OWNER_TOKEN_LENGTH) {
            throw new IllegalArgumentException("处理所有者 Token 不合法");
        }
    }

    private <T> DefaultRedisScript<T> loadScript(String path, Class<T> resultType) {
        try {
            String scriptText = new ClassPathResource(path)
                    .getContentAsString(StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(scriptText, resultType);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载 Redis 幂等脚本", exception);
        }
    }

    record AcquireScriptResult(
            String status,
            long retryAfterSeconds,
            String responsePayload
    ) {
    }
}
