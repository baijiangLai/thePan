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
        // 3.像邮箱发送新验证码
        sendEmailCode(email, code);
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

    private   void  sendEmailCode(String email, String code) throws Exception {
        try {
            JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
            // 不用配置文件的，直接在这儿设置参数。 配置文件的似乎读不进去
            javaMailSender.setHost("smtp.qq.com");
            javaMailSender.setPort(465);
            javaMailSender.setUsername("1330123181@qq.com");
            javaMailSender.setPassword("gfddqcbuntzfjecj");
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage,true);

            // 从redis中获取邮箱配置
            SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();

            // 发件人邮箱  从配置文件中获取
            messageHelper.setFrom(appConfig.getSendUserName());
            // 收件邮箱
            messageHelper.setTo(email);
            // 标题 也可直接设置常量，没必要走redis
            messageHelper.setSubject(sysSettingsDto.getRegisterEmailTitle());
            // 邮件内容 也可直接设置常量，没必要走redis
            messageHelper.setText(String.format(sysSettingsDto.getRegisterEmailContent(),code));
            messageHelper.setSentDate(new Date());

            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            e.printStackTrace();
            throw  new Exception("邮件发送失败");
        }

    }


}