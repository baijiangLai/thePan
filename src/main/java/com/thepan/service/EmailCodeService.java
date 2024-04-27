package com.thepan.service;



/**
 * 邮箱验证码 业务接口
 */
public interface EmailCodeService {
    /**
     * 发送邮箱验证码
     */
    void sendEmailCode(String email, Integer type);

    /**
     * 验证邮箱验证码
     * @param email
     * @param code
     */
    void vertifyEmailCode(String email, String code) ;

}