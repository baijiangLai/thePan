package com.thepan.service.impl;


import com.thepan.config.AppConfig;
import com.thepan.constants.Constants;
import com.thepan.dao.EmailCode;
import com.thepan.dao.SysSettingsDto;
import com.thepan.dao.UserInfo;
import com.thepan.mappers.EmailCodeMapper;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.EmailCodeService;
import com.thepan.utils.RedisComponent;
import com.thepan.utils.StringTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Date;


/**
 * 邮箱验证码 业务接口实现
 */
@Service("emailCodeService")
public class EmailCodeServiceImpl implements EmailCodeService {

    @Autowired
    private UserInfoMapper<UserInfo,UserInfo> userInfoMapper;

    @Autowired
    private EmailCodeMapper<EmailCode,EmailCode> emailCodeMapper;


    @Autowired
    private AppConfig appConfig;

    @Autowired
    private RedisComponent redisComponent;



    /**
     * 1. 若是注册，先在用户信息表里验证是否存在该邮箱
     * 2. 生成验证码
     * 3. 向邮箱发送验证码
     * 4. 将以前的验证码置为失效
     * 5. 存入新验证码
     */
    @Override
    public void sendEmailCode(String email, Integer type) throws Exception {
        // 1. 是否为注册
        if (Constants.ZERO.equals(type)) {
            UserInfo userInfo = userInfoMapper.selectByEmail(email);
            if (userInfo!=null){
                throw new Exception("该邮箱已被注册！！！");
            }
        }
        // 2. 生成验证码
        String code = StringTools.getRandomNumber(Constants.LENGTH_5);
        // 3. TODO 使用EmailUtil发送验证码
        // TODO 发送验证码之后将验证码存入redis中，之后等登录的时候比较两个验证码

        // 4.将以前的验证码置为失效
        emailCodeMapper.disableCheckCode(email);
        // 5.插入新的验证码
        EmailCode emailCode = new EmailCode();
        emailCode.setCode(code);
        emailCode.setEmail(email);
        emailCode.setStatus(Constants.ZERO);
        emailCode.setCreateTime(new Date());
        emailCodeMapper.insert(emailCode);
    }


}