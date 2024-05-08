package com.thepan.service.impl;


import com.thepan.config.SenderProperties;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.EmailCode;
import com.thepan.entity.dto.SysSettingsDto;
import com.thepan.entity.dao.UserInfo;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.EmailCodeMapper;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.EmailCodeService;
import com.thepan.utils.EmailUtil;
import com.thepan.utils.RedisComponent;
import com.thepan.utils.RedisUtil;
import com.thepan.utils.StringTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;


/**
 * 邮箱验证码 业务接口实现
 */
@Service("emailCodeService")
public class EmailCodeServiceImpl implements EmailCodeService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private EmailCodeMapper emailCodeMapper;


    @Autowired
    private SenderProperties senderProperties;

    @Autowired
    private RedisComponent redisComponent;

    @Autowired
    RedisUtil redisUtil;



    /**
     * 1. 若是注册，先在用户信息表里验证是否存在该邮箱
     * 2. 生成验证码
     * 3. 向邮箱发送验证码
     * 4. 将以前的验证码置为失效
     * 5. 存入新验证码
     */
    @Override
    public void sendEmailCode(String email, Integer type) {
        // 1. 是否为注册
        if (Constants.ZERO.equals(type)) {
            UserInfo userInfo = userInfoMapper.selectByEmail(email);
            if (userInfo!=null){
                throw new BusinessException("该邮箱已被注册！！！");
            }
        }
        // 2. 生成验证码
        String code = StringTools.getRandomNumber(Constants.LENGTH_5);
        // 3. 使用EmailUtil发送验证码
        SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
        EmailUtil.sendEmailUtil(senderProperties.getSendUserName(),email,sysSettingsDto.getRegisterEmailTitle(),String.format(sysSettingsDto.getRegisterEmailContent(),code));
        // 4. 将验证码相关信息存入redis，也可将验证码存入数据库，这里两种方法都试一下
        redisUtil.set(Constants.CHECK_CODE_KEY_EMAIL,code);
        redisUtil.set(Constants.CREATE_TIME,new Date());
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

    @Override
    public void checkCode(String email, String code) {
        // 查找该邮箱是否有生效的验证码
        EmailCode emailCode = emailCodeMapper.selectByEmailAndCode(email, code);
        if (null == emailCode) {
            throw new BusinessException("邮箱验证码不正确或已失效");
        }

        if (System.currentTimeMillis() - emailCode.getCreateTime().getTime() > Constants.LENGTH_15 * 1000 * 60) {
            throw new BusinessException("邮箱验证码已失效");
        }
        emailCodeMapper.disableEmailCode(email);
    }
}