package com.xiaoyang.aiticketplatform.dto.response;

import com.xiaoyang.aiticketplatform.enums.UserRole;

public record CurrentUserResponse(
        Long id,
        String username,
        UserRole role
) {
}
