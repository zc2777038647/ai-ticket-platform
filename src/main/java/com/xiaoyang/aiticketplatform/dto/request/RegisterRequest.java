package com.xiaoyang.aiticketplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 64, message = "用户名长度必须在4到64个字符之间")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须在8到64个字符之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName
) {
}
