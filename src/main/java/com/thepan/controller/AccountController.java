package com.thepan.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.SessionWebUserDto;
import com.thepan.entity.dao.UserInfo;
import com.thepan.enums.VerifyRegexEnum;
import com.thepan.exception.BusinessException;
import com.thepan.service.EmailCodeService;
import com.thepan.service.UserInfoService;
import com.thepan.utils.*;
import com.thepan.vo.ResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Objects;

import static com.thepan.utils.ResponseUtils.getSuccessResponseVO;

@RestController("accountController")
@Slf4j
public class AccountController {
    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private UserInfoService userInfoService;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 给前端传邮件验证码并存入session
     * @param response
     * @param session
     * @param type
     * @throws IOException
     */
    @GetMapping("/checkCode")
    public void checkcode(HttpServletResponse response, HttpSession session, Integer type) throws IOException { // 图片通过response传递给前端
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

        //  write方法里面通过使用ImageIO.write将验证码图片写入到response的输出流
        createImageCode.write(response.getOutputStream());
    }

    /**
     * 给邮箱发送验证码:
     * 1. 校验前端输入的的验证码是否正确
     * 2. 给邮箱发送验证码
     */
    @PostMapping("/sendEmailCode")
    @GlobalInterceptor
    public ResponseVO sendEmailCode(HttpSession session,
                                          @VerifyParam(required = true, regex= VerifyRegexEnum.EMAIL) String email,
                                    @VerifyParam(required = true) String checkCode,
                                    @VerifyParam(required = true)  Integer type){
      try{
            if (!session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL).equals(checkCode.toLowerCase())){
                throw new Exception("验证码输入不正确，请重新输入！！！");
            }
          emailCodeService.sendEmailCode(email,type);
          return getSuccessResponseVO(new Date());
    } catch (Exception e) {
          e.printStackTrace();
          return ResponseUtils.getErrorResponseVO(new Date());
      }finally {
          session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL); // 移除旧验证码
      }
    }

    /**
     * 注册
     *  1. 验证验证码是否正确  2. 验证邮箱是否已注册  3. 注册
     * @param httpSession
     * @param nickName
     * @param password
     * @param email
     * @param checkCode
     * @return
     */
    @PostMapping("/register")
    @GlobalInterceptor
    public ResponseVO register(HttpSession httpSession,
                               @VerifyParam(required = true)String nickName,
                               @VerifyParam(required = true,regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18)String password,
                               @VerifyParam(required = true,regex= VerifyRegexEnum.EMAIL)String email,
                               @VerifyParam(required = true)String checkCode,
                               @VerifyParam(required = true)String emailCode) {
        try {
            if (!httpSession.getAttribute(Constants.CHECK_CODE_KEY).equals(checkCode.toLowerCase())) {
                throw new BusinessException("验证码输入不正确，请重新输入！！！");
            }
            // 注册
            int rows = userInfoService.register(email, password, emailCode, nickName);

            if (rows == 1) {
                ResponseVO responseVO = new ResponseVO();
                responseVO.setInfo("注册成功！！！");
                return responseVO;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (httpSession.getAttribute(Constants.CHECK_CODE_KEY) != null) {
                httpSession.removeAttribute(Constants.CHECK_CODE_KEY); // 移除旧验证码
            }
        }

        return null;
    }


    @RequestMapping("/login")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO login(HttpSession session, HttpServletRequest request,
                            @VerifyParam(required = true) String email,
                            @VerifyParam(required = true) String password,
                            @VerifyParam(required = true) String checkCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new BusinessException("图片验证码不正确");
            }
            SessionWebUserDto sessionWebUserDto = userInfoService.login(email, password);
            session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
            return getSuccessResponseVO(sessionWebUserDto);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    @RequestMapping("/resetPwd")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO resetPwd(HttpSession session,
                               @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
                               @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18) String password,
                               @VerifyParam(required = true) String checkCode,
                               @VerifyParam(required = true) String emailCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new BusinessException("图片验证码不正确");
            }
            userInfoService.resetPwd(email, password, emailCode);
            return getSuccessResponseVO(null);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    @RequestMapping("/getAvatar/{userId}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public void getAvatar(HttpServletResponse response, @VerifyParam(required = true) @PathVariable("userId") String userId) {
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


    @RequestMapping("/getUserInfo")
    @GlobalInterceptor
    public ResponseVO getUserInfo(HttpSession session) {
        SessionWebUserDto sessionWebUserDto = SessionUtil.getUserInfoFromSession(session);
        return getSuccessResponseVO(sessionWebUserDto);
    }

    @RequestMapping("/getUseSpace")
    @GlobalInterceptor
    public ResponseVO getUseSpace(HttpSession session) {
        SessionWebUserDto sessionWebUserDto = SessionUtil.getUserInfoFromSession(session);
        return getSuccessResponseVO(redisComponent.getUserSpaceUse(sessionWebUserDto.getUserId()));
    }

    @RequestMapping("/logout")
    public ResponseVO logout(HttpSession session) {
        session.invalidate();
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/updateUserAvatar")
    @GlobalInterceptor
    public ResponseVO updateUserAvatar(HttpSession session, MultipartFile avatar) {
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
        userInfoService.updateUserInfoByUserId(userInfo, webUserDto.getUserId());
        webUserDto.setAvatar(null);
        session.setAttribute(Constants.SESSION_KEY, webUserDto);
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/updatePassword")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO updatePassword(HttpSession session,
                                     @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18) String password) {
        SessionWebUserDto sessionWebUserDto = SessionUtil.getUserInfoFromSession(session);
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(StringTools.encodeByMD5(password));
        userInfoService.updateUserInfoByUserId(userInfo, sessionWebUserDto.getUserId());
        return getSuccessResponseVO(null);
    }

}
