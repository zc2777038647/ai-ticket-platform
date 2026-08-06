package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class CreateTicketRequestFingerprint {

    public String fingerprint(CreateTicketRequest request) {
        Objects.requireNonNull(request, "创建工单请求不能为空");

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeField(output, "title", request.title());
                writeField(output, "description", request.description());
                writeField(output, "creatorName", request.creatorName());
                writeField(output, "priority", request.priority().name());
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成创建工单请求指纹", exception);
        }
    }

    private void writeField(DataOutputStream output, String fieldName, String value)
            throws IOException {
        writeBytes(output, fieldName.getBytes(StandardCharsets.UTF_8));
        writeBytes(output, value.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }
}
