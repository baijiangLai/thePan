package com.thepan.service;


import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.query.UserInfoQuery;

import java.util.List;

/**
 * 用户信息 业务接口
 */
public interface UserInfoService {

    int register(String email, String password, String code, String nikeName);

    SessionWebUserDto login(String email, String password);

    void resetPwd(String email, String password, String emailCode);

    void updateUserInfoByUserId(UserInfo userInfo, String userId);

    SessionWebUserDto qqLogin(String code);

    List<UserInfo> findListByParam(UserInfoQuery userInfoQuery);
}