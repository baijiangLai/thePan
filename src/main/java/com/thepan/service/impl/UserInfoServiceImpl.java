package com.thepan.service.impl;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.thepan.config.AdminProperties;
import com.thepan.config.QqProperties;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.QQInfoDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.dto.UserSpaceDto;
import com.thepan.entity.enums.PageSize;
import com.thepan.entity.enums.UserStatusEnum;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.query.UserInfoQuery;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.EmailCodeService;
import com.thepan.service.FileInfoService;
import com.thepan.service.UserInfoService;
import com.thepan.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

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
     */
    @Override
    public int register(HttpSession session, String nickName, String password,
                        String email, String checkCode, String emailCode) {

        if (!session.getAttribute(Constants.CHECK_CODE_KEY).equals(checkCode.toLowerCase())) {
            throw new BusinessException("验证码输入不正确，请重新输入！！！");
        }

        //1. 验证邮箱是否已注册以及昵称是否重复
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo != null) {
            throw new BusinessException("邮箱已注册");
        }
        UserInfo userInfo1 = userInfoMapper.selectByNickname(nickName);
        if (userInfo1 != null) {
            throw new BusinessException("昵称已经存在");
        }

        //2. 验证邮箱验证码是否正确,可直接从redis取验证码  、也可使用数据库中存入的验证码
//        Object emailCheckCode = redisUtil.get(Constants.CHECK_CODE_KEY_EMAIL);
//        Date date = (Date) redisUtil.get(Constants.CREATE_TIME);
        emailCodeService.checkCode(email,checkCode);

        //3. 生成用户信息
        userInfo = new UserInfo();
        userInfo.setEmail(email);
        userInfo.setNickName(nickName);
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
    @Transactional(rollbackFor = Exception.class)
    public SessionWebUserDto login(HttpSession session, String email, String password, String checkCode) {

        if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
            throw new BusinessException("图片验证码不正确");
        }

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

        session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
        return sessionWebUserDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(HttpSession session, String email, String password, String checkCode, String emailCode) {
        if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
            throw new BusinessException("图片验证码不正确");
        }

        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (null == userInfo) {
            throw new BusinessException("邮箱账号不存在");
        }

        //校验邮箱验证码
        emailCodeService.checkCode(email, emailCode);

        UserInfo updateInfo = new UserInfo();
        updateInfo.setPassword(StringTools.encodeByMD5(password));
        userInfoMapper.updateByEmail(updateInfo, email);
    }

    @Override
    public Map<String, Object> qqLogin(HttpSession session, String code, String state) {
        Map<String, Object> result = new HashMap<>();

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

        boolean isAdmin = false;
        String userEmail = user.getEmail() == null ? "" : user.getEmail();
        for (String adminEmail: adminEmailArr) {
            if (adminEmail.equals(userEmail)) {
                isAdmin = true;
                break;
            }
        }

        if (isAdmin) {
            sessionWebUserDto.setAdmin(true);
        } else {
            sessionWebUserDto.setAdmin(false);
        }

        session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);

        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setUseSpace(fileInfoService.getUserUseSpace(user.getUserId()));
        userSpaceDto.setTotalSpace(user.getTotalSpace());
        redisComponent.saveUserSpaceUse(user.getUserId(), userSpaceDto);

        result.put("callbackUrl", session.getAttribute(state));
        result.put("userInfo", sessionWebUserDto);

        return result;
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

    @Override
    public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
        int count = findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserInfo> list = findListByParam(param);
        PaginationResultVO<UserInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    private Integer findCountByParam(UserInfoQuery param) {
        return userInfoMapper.selectCount(param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(String userId, Integer status) {
        UserInfo userInfo = new UserInfo();
        userInfo.setStatus(status);
        if (UserStatusEnum.DISABLE.getStatus().equals(status)) {
            userInfo.setUseSpace(0L);
            fileInfoService.deleteFileByUserId(userId);
        }
        userInfoMapper.updateByUserId(userInfo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserSpace(String userId, Integer changeSpace) {
        Long space = changeSpace * Constants.MB;
        userInfoMapper.updateUserSpace(userId, null, space);
        redisComponent.resetUserSpaceUse(userId);
    }

    @Override
    public void checkCode(HttpServletResponse response, HttpSession session, Integer type) {
        // 创建随机验证码
        CreateImageCode createImageCode = new CreateImageCode(130, 38, 5, 10);

        // 设置响应头，禁用浏览器缓存
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        //将验证码的数值存在session里面去 0:登录注册 1:邮箱验证码发送 默认0
        if (type.intValue() == 0 || Objects.isNull(type)){
            session.setAttribute(Constants.CHECK_CODE_KEY,createImageCode.getCode());
        }else {
            session.setAttribute(Constants.CHECK_CODE_KEY_EMAIL,createImageCode.getCode());
        }

        try {
            //  write方法里面通过使用ImageIO.write将验证码图片写入到response的输出流
            createImageCode.write(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void getAvatar(HttpServletResponse response, String userId) {
        String avatarFolderName = Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
        File folder = new File(FolderUtil.getProjectFolder() + avatarFolderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String avatarPath = FolderUtil.getProjectFolder() + avatarFolderName + userId + Constants.AVATAR_SUFFIX;
        File file = new File(avatarPath);
        if (!file.exists()) {
            if (!new File(FolderUtil.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFUALT).exists()) {
                response.setHeader("Content-Type", "application/json;charset=UTF-8");
                response.setStatus(HttpStatus.OK.value());
                PrintWriter writer = null;
                try {
                    writer = response.getWriter();
                    writer.print("请在头像目录下放置默认头像default_avatar.jpg");
                    writer.close();
                } catch (Exception e) {
                    log.error("输出无默认图失败", e);
                } finally {
                    writer.close();
                }
                return;
            }
            avatarPath = FolderUtil.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFUALT;
        }
        response.setContentType("image/jpg");
        if (StrUtil.isEmpty(avatarPath)) {
            return;
        }
        try {
            OutputStream outputStream = response.getOutputStream();
            outputStream.write(FileUtil.readBytes(avatarPath));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateUserAvatar(HttpSession session, MultipartFile avatar) {
        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        String baseFolder = FolderUtil.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
        if (!targetFileFolder.exists()) {
            targetFileFolder.mkdirs();
        }
        File targetFile = new File(targetFileFolder.getPath() + "/" + webUserDto.getUserId() + Constants.AVATAR_SUFFIX);
        try {
            avatar.transferTo(targetFile);
        } catch (Exception e) {
            log.error("上传头像失败", e);
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setQqAvatar("");
        userInfoMapper.updateByUserId(userInfo, webUserDto.getUserId());
        webUserDto.setAvatar(null);
        session.setAttribute(Constants.SESSION_KEY, webUserDto);
    }

    @Override
    public void updatePassword(HttpSession session, String password) {
        SessionWebUserDto sessionWebUserDto = SessionUtil.getUserInfoFromSession(session);
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(StringTools.encodeByMD5(password));
        userInfoMapper.updateByUserId(userInfo, sessionWebUserDto.getUserId());
    }
}