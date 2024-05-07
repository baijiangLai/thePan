package com.thepan.service.impl;


import com.thepan.constants.Constants;
import com.thepan.entity.dao.SessionWebUserDto;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dao.UserSpaceDto;
import com.thepan.enums.UserStatusEnum;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.EmailCodeService;
import com.thepan.service.UserInfoService;
import com.thepan.utils.RedisComponent;
import com.thepan.utils.RedisUtil;
import com.thepan.utils.StringTools;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 用户信息 业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {
    @Autowired
    UserInfoMapper userInfoMapper;

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
    public int register(String email, String password, String code, String nikeName) {
        //1. 验证邮箱是否已注册以及昵称是否重复
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo != null) {
            throw new BusinessException("邮箱已注册");
        }
        UserInfo userInfo1 = userInfoMapper.selectByNickname(nikeName);
        if (userInfo1 != null) {
            throw new BusinessException("昵称已经存在");
        }

        //2. 验证邮箱验证码是否正确,可直接从redis取验证码  、也可使用数据库中存入的验证码
//        Object emailCheckCode = redisUtil.get(Constants.CHECK_CODE_KEY_EMAIL);
//        Date date = (Date) redisUtil.get(Constants.CREATE_TIME);
        emailCodeService.checkCode(email,code);

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
        Integer rows = userInfoMapper.insert(userInfo);
        if (rows != 1) {
            throw new BusinessException("注册失败");
        }
        return rows;
    }

    @Override
    public SessionWebUserDto login(String email, String password) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (null == userInfo || !userInfo.getPassword().equals(password)) {
            throw new BusinessException("账号或者密码错误");
        }
        if (UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())) {
            throw new BusinessException("账号已禁用");
        }
        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByUserId(updateInfo, userInfo.getUserId());
        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setNickName(userInfo.getNickName());
        sessionWebUserDto.setUserId(userInfo.getUserId());

        //用户空间
        UserSpaceDto userSpaceDto = new UserSpaceDto();
        // TODO: 后续需要计算出使用了的存储空间大小
        userSpaceDto.setUseSpace(0L);
        userSpaceDto.setTotalSpace(userInfo.getTotalSpace());
        redisComponent.saveUserSpaceUse(userInfo.getUserId(), userSpaceDto);
        return sessionWebUserDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {
        UserInfo userInfo = this.userInfoMapper.selectByEmail(email);
        if (null == userInfo) {
            throw new BusinessException("邮箱账号不存在");
        }
        //校验邮箱验证码
        emailCodeService.checkCode(email, emailCode);

        UserInfo updateInfo = new UserInfo();
        updateInfo.setPassword(StringTools.encodeByMD5(password));
        userInfoMapper.updateByEmail(updateInfo, email);
    }

    public void updateUserInfoByUserId(UserInfo bean, String userId) {
        userInfoMapper.updateByUserId(bean, userId);
    }
}