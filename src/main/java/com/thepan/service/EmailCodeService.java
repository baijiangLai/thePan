package com.thepan.service;


import javax.servlet.http.HttpSession;

/**
 * 邮箱验证码 业务接口
 */
public interface EmailCodeService {
    /**
     * 发送邮箱验证码
     */
    void sendEmailCode(HttpSession session, String email, String checkCode, Integer type);

    /**
     * 验证邮箱验证码
     * @param email
     * @param code
     */
    void checkCode(String email, String code) ;

}