package com.thepan.service;



/**
 * 邮箱验证码 业务接口
 */
public interface EmailCodeService {
    /**
     * 发送邮箱验证码
     */
    public void sendEmailCode(String email, Integer type) throws Exception;


}