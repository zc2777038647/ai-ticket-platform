package com.xiaoyang.aiticketplatform.dto.response;

import com.xiaoyang.aiticketplatform.enums.UserRole;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        UserRole role
) {
}
