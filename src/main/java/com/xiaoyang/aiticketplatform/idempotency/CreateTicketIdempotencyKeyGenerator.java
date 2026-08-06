package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.config.CreateTicketIdempotencyProperties;
import com.xiaoyang.aiticketplatform.exception.InvalidIdempotencyKeyException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class CreateTicketIdempotencyKeyGenerator {

    private final CreateTicketIdempotencyProperties properties;

    public CreateTicketIdempotencyKeyGenerator(CreateTicketIdempotencyProperties properties) {
        this.properties = properties;
    }

    public String generateRedisKey(Long creatorUserId, String idempotencyKey) {
        if (creatorUserId == null || creatorUserId <= 0) {
            throw new IllegalArgumentException("创建者用户 ID 必须为正数");
        }
        if (idempotencyKey == null) {
            throw new InvalidIdempotencyKeyException();
        }

        String normalizedKey = idempotencyKey.trim();
        int length = normalizedKey.length();
        if (length < properties.getMinimumKeyLength()
                || length > properties.getMaximumKeyLength()) {
            throw new InvalidIdempotencyKeyException();
        }
        if (normalizedKey.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidIdempotencyKeyException();
        }

        return properties.getKeyPrefix() + ":" + digest(creatorUserId, normalizedKey);
    }

    private String digest(long creatorUserId, String normalizedKey) {
        try {
            byte[] keyBytes = normalizedKey.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeLong(creatorUserId);
                output.writeInt(keyBytes.length);
                output.write(keyBytes);
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成幂等 Redis Key", exception);
        }
    }
}
