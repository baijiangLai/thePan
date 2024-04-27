package com.thepan.service.impl;


import com.thepan.constants.Constants;
import com.thepan.dao.UserInfo;
import com.thepan.enums.UserStatusEnum;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.EmailCodeService;
import com.thepan.service.UserInfoService;
import com.thepan.utils.RedisComponent;
import com.thepan.utils.RedisUtil;
import com.thepan.utils.StringTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 用户信息 业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {
    @Autowired
    UserInfoMapper<UserInfo, UserInfo> userInfoMapper;

    @Autowired
    RedisUtil redisUtil;


    @Autowired
    RedisComponent redisComponent;

    @Autowired
    EmailCodeService emailCodeService;
    /**
     * 注册账号
     * 1. 验证邮箱是否已注册以及昵称是否重复
     * 2. 验证邮箱验证码是否正确
     * 3. 生成用户信息
     *
     * @param email
     * @param code
     * @param nikeName
     * @throws Exception
     */
    @Override
    public void register(String email, String password, String code, String nikeName) throws Exception {
        //1. 验证邮箱是否已注册以及昵称是否重复
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo != null) {
            throw new Exception("邮箱已注册");
        }
        UserInfo userInfo1 = userInfoMapper.selectByNickname(nikeName);
        if (userInfo1 != null) {
            throw new Exception("昵称已经存在");
        }

        //2. 验证邮箱验证码是否正确,可直接从redis取验证码  、也可使用数据库中存入的验证码
//        Object emailCheckCode = redisUtil.get(Constants.CHECK_CODE_KEY_EMAIL);
//        Date date = (Date) redisUtil.get(Constants.CREATE_TIME);
        emailCodeService.vertifyEmailCode(email,code);

        //3. 生成用户信息
        userInfo = new UserInfo();
        userInfo.setEmail(email);
        userInfo.setNickName(nikeName);
        userInfo.setPassword(StringTools.encodeByMD5(password));
        userInfo.setJoinTime(new Date());
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setUserId(StringTools.getRandomNumber(Constants.LENGTH_10));
        // 邮箱初始化总空间需要从redis里面取
        userInfo.setTotalSpace(redisComponent.getSysSettingsDto().getUserInitUseSpace() * Constants.MB);
        userInfo.setUseSpace(0L);
        userInfoMapper.insert(userInfo);

    }
}