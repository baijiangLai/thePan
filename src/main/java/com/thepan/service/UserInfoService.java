package com.thepan.service;


/**
 * 用户信息 业务接口
 */
public interface UserInfoService {

    int register(String email, String password, String code, String nikeName);

}