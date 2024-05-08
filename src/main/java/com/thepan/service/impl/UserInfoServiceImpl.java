package com.thepan.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.thepan.config.AdminProperties;
import com.thepan.config.QqProperties;
import com.thepan.constants.Constants;
import com.thepan.entity.dto.QQInfoDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.UserSpaceDto;
import com.thepan.entity.enums.UserStatusEnum;
import com.thepan.entity.query.UserInfoQuery;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.EmailCodeService;
import com.thepan.service.FileInfoService;
import com.thepan.service.UserInfoService;
import com.thepan.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户信息 业务接口实现
 */
@Service
@Slf4j
public class UserInfoServiceImpl implements UserInfoService {
    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private AdminProperties adminProperties;

    @Resource
    private FileInfoService fileInfoService;


    @Resource
    private QqProperties qqProperties;
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
        Integer rows = userInfoMapper.insertUserInfo(userInfo);
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
        userSpaceDto.setUseSpace(fileInfoService.getUserUseSpace(sessionWebUserDto.getUserId()));
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

    @Override
    public SessionWebUserDto qqLogin(String code) {
        String accessToken = getQQAccessToken(code);
        String openId = getQQOpenId(accessToken);
        UserInfo user = userInfoMapper.selectByQqOpenId(openId);
        String avatar = null;

        if (null == user) {
            QQInfoDto qqInfo = getQQUserInfo(accessToken, openId);
            user = new UserInfo();

            String nickName = qqInfo.getNickname();
            nickName = nickName.length() > Constants.LENGTH_150 ? nickName.substring(0, 150) : nickName;
            avatar = StrUtil.isEmpty(qqInfo.getFigureurl_qq_2()) ? qqInfo.getFigureurl_qq_1() : qqInfo.getFigureurl_qq_2();
            Date curDate = new Date();

            //上传头像到本地
            user.setQqOpenId(openId);
            user.setJoinTime(curDate);
            user.setNickName(nickName);
            user.setQqAvatar(avatar);
            user.setUserId(StringTools.getRandomString(Constants.LENGTH_10));
            user.setLastLoginTime(curDate);
            user.setStatus(UserStatusEnum.ENABLE.getStatus());
            user.setUseSpace(0L);
            user.setTotalSpace(redisComponent.getSysSettingsDto().getUserInitUseSpace() * Constants.MB);
            userInfoMapper.insertUserInfo(user);
        } else {
            UserInfo updateInfo = new UserInfo();
            updateInfo.setLastLoginTime(new Date());
            avatar = user.getQqAvatar();
            userInfoMapper.updateByQqOpenId(updateInfo, openId);
        }
        if (UserStatusEnum.DISABLE.getStatus().equals(user.getStatus())) {
            throw new BusinessException("账号被禁用无法登录");
        }
        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setUserId(user.getUserId());
        sessionWebUserDto.setNickName(user.getNickName());
        sessionWebUserDto.setAvatar(avatar);
        String[] adminEmailArr = adminProperties.getAdminEmails().split(",");

        if (ArrayUtils.contains(adminEmailArr, user.getEmail() == null ? "" : user.getEmail())) {
            sessionWebUserDto.setAdmin(true);
        } else {
            sessionWebUserDto.setAdmin(false);
        }

        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setUseSpace(fileInfoService.getUserUseSpace(user.getUserId()));
        userSpaceDto.setTotalSpace(user.getTotalSpace());
        redisComponent.saveUserSpaceUse(user.getUserId(), userSpaceDto);
        return sessionWebUserDto;
    }

    private QQInfoDto getQQUserInfo(String accessToken, String qqOpenId) throws BusinessException {
        String url = String.format(qqProperties.getQqUrlUserInfo(), accessToken, qqProperties.getQqAppId(), qqOpenId);
        String response = OKHttpUtils.getRequest(url);
        if (StringUtils.isNotBlank(response)) {
            QQInfoDto qqInfo = JSONUtil.toBean(response, QQInfoDto.class);
            if (qqInfo.getRet() != 0) {
                log.error("qqInfo:{}", response);
                throw new BusinessException("调qq接口获取用户信息异常");
            }
            return qqInfo;
        }
        throw new BusinessException("调qq接口获取用户信息异常");
    }

    private String getQQOpenId(String accessToken) throws BusinessException {
        // 获取openId
        String url = String.format(qqProperties.getQqUrlOpenId(), accessToken);
        String openIDResult = OKHttpUtils.getRequest(url);
        String tmpJson = getQQResp(openIDResult);
        if (tmpJson == null) {
            log.error("调qq接口获取openID失败:tmpJson{}", tmpJson);
            throw new BusinessException("调qq接口获取openID失败");
        }

        Map map = JSONUtil.toBean(tmpJson, Map.class);
        if (map == null || map.containsKey(Constants.VIEW_OBJ_RESULT_KEY)) {
            log.error("调qq接口获取openID失败:{}", map);
            throw new BusinessException("调qq接口获取openID失败");
        }
        return String.valueOf(map.get("openid"));
    }

    private String getQQResp(String result) {
        if (StrUtil.isNotEmpty(result)) {
            int pos = result.indexOf("callback");
            if (pos != -1) {
                int start = result.indexOf("(");
                int end = result.lastIndexOf(")");
                String jsonStr = result.substring(start + 1, end - 1);
                return jsonStr;
            }
        }
        return null;
    }

    private String getQQAccessToken(String code) {
        /**
         * 返回结果是字符串
         * access_token=*&expires_in=7776000&refresh_token=*
         * 返回错误 callback({UcWebConstants.VIEW_OBJ_RESULT_KEY:111,error_description:"error msg"})
         */
        String accessToken = null;
        String url = null;
        try {
            url = String.format(qqProperties.getQqUrlAccessToken(), qqProperties.getQqAppId(), qqProperties.getQqAppKey(), code,
                    URLEncoder.encode(qqProperties.getQqUrlRedirect(), "utf-8"));
        } catch (UnsupportedEncodingException e) {
            log.error("encode失败");
        }
        String response = OKHttpUtils.getRequest(url);
        if (response == null || response.indexOf(Constants.VIEW_OBJ_RESULT_KEY) != -1) {
            log.error("获取qqToken失败:{}", response);
            throw new BusinessException("获取qqToken失败");
        }
        String[] params = response.split("&");

        if (params != null && params.length > 0) {
            for (String p : params) {
                if (p.indexOf("access_token") != -1) {
                    accessToken = p.split("=")[1];
                    break;
                }
            }
        }
        return accessToken;
    }

    @Override
    public List<UserInfo> findListByParam(UserInfoQuery userInfoQuery) {
        return userInfoMapper.selectList(userInfoQuery);
    }
}