package com.thepan.service;


import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.query.UserInfoQuery;
import com.thepan.entity.vo.file.PaginationResultVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * 用户信息 业务接口
 */
public interface UserInfoService {

    int register(HttpSession session, String nickName, String password, String email, String checkCode, String emailCode);

    SessionWebUserDto login(HttpSession session, String email, String password, String checkCode);

    void resetPwd(HttpSession session,String email, String password, String checkCode, String emailCode);

    default void updateUserInfoByUserId(UserInfo userInfo, String userId){}

    Map<String, Object> qqLogin(HttpSession session, String code, String state);

    List<UserInfo> findListByParam(UserInfoQuery userInfoQuery);

    PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param);

    void updateUserStatus(String userId, Integer status);

    void changeUserSpace(String userId, Integer changeSpace);

    void checkCode(HttpServletResponse response, HttpSession session, Integer type);

    void getAvatar(HttpServletResponse response, String userId);

    void updateUserAvatar(HttpSession session, MultipartFile avatar);

    void updatePassword(HttpSession session, String password);
}