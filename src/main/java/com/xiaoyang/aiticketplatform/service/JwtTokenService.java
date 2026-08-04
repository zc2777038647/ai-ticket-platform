package com.xiaoyang.aiticketplatform.service;

import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.security.IssuedAccessToken;

public interface JwtTokenService {

    IssuedAccessToken issueAccessToken(UserAccount userAccount);
}
