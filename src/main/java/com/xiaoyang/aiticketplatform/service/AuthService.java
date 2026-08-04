package com.xiaoyang.aiticketplatform.service;

import com.xiaoyang.aiticketplatform.dto.request.LoginRequest;
import com.xiaoyang.aiticketplatform.dto.request.RegisterRequest;
import com.xiaoyang.aiticketplatform.dto.response.LoginResponse;
import com.xiaoyang.aiticketplatform.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
