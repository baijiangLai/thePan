package com.thepan.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.config.QqProperties;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.enums.VerifyRegexEnum;
import com.thepan.entity.vo.response.ResponseVO;
import com.thepan.exception.BusinessException;
import com.thepan.service.EmailCodeService;
import com.thepan.service.UserInfoService;
import com.thepan.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.thepan.utils.ResponseUtil.getSuccessResponseVO;

@RestController
@Slf4j
public class AccountController {
    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private UserInfoService userInfoService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private QqProperties qqProperties;

    /**
     * 给前端传邮件验证码并存入session
     * @param response
     * @param session
     * @param type
     * @throws IOException
     */
    @GetMapping("/checkCode")
    public void checkCode(HttpServletResponse response, HttpSession session, Integer type) throws IOException { // 图片通过response传递给前端
        userInfoService.checkCode(response,session,type);
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
          emailCodeService.sendEmailCode(session, email, checkCode, type);
          return getSuccessResponseVO(new Date());
    } catch (Exception e) {
          e.printStackTrace();
          return getSuccessResponseVO(null);
      }finally {
          session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL); // 移除旧验证码
      }
    }

    /**
     * 注册
     *  1. 验证验证码是否正确  2. 验证邮箱是否已注册  3. 注册
     * @param session
     * @param nickName
     * @param password
     * @param email
     * @param checkCode
     * @return
     */
    @PostMapping("/register")
    @GlobalInterceptor
    public ResponseVO register(HttpSession session,
                               @VerifyParam(required = true)String nickName,
                               @VerifyParam(required = true,regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18)String password,
                               @VerifyParam(required = true,regex= VerifyRegexEnum.EMAIL)String email,
                               @VerifyParam(required = true)String checkCode,
                               @VerifyParam(required = true)String emailCode) {
        try {
            int rows = userInfoService.register(session, nickName, password, email, checkCode, emailCode);

            if (rows == 1) {
                ResponseVO responseVO = new ResponseVO();
                responseVO.setInfo("注册成功！！！");
                return responseVO;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (session.getAttribute(Constants.CHECK_CODE_KEY) != null) {
                session.removeAttribute(Constants.CHECK_CODE_KEY); // 移除旧验证码
            }
        }
        return ResponseUtil.getSuccessResponseVO(null);
    }


    @RequestMapping("/login")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO login(HttpSession session,
                            @VerifyParam(required = true) String email,
                            @VerifyParam(required = true) String password,
                            @VerifyParam(required = true) String checkCode) {
        try {
            SessionWebUserDto sessionWebUserDto = userInfoService.login(session, email, password, checkCode);
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
            userInfoService.resetPwd(session,email, password, checkCode, emailCode);
            return getSuccessResponseVO(null);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    @RequestMapping("/getAvatar/{userId}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public void getAvatar(HttpServletResponse response, @VerifyParam(required = true) @PathVariable("userId") String userId) {
        userInfoService.getAvatar(response, userId);
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
        userInfoService.updateUserAvatar(session, avatar);
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/updatePassword")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO updatePassword(HttpSession session,
                                     @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18) String password) {
        userInfoService.updatePassword(session, password);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("qqlogin")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO qqlogin(HttpSession session, String callbackUrl) throws UnsupportedEncodingException {
        String state = StringTools.getRandomString(Constants.LENGTH_30);
        if (!StrUtil.isEmpty(callbackUrl)) {
            session.setAttribute(state, callbackUrl);
        }
        String url = String.format(qqProperties.getQqUrlAuthorization(), qqProperties.getQqAppId(), URLEncoder.encode(qqProperties.getQqUrlRedirect(), "utf-8"), state);
        return getSuccessResponseVO(url);
    }

    @RequestMapping("qqlogin/callback")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO qqLoginCallback(HttpSession session,
                                      @VerifyParam(required = true) String code,
                                      @VerifyParam(required = true) String state) {
        Map<String, Object> resultMap = userInfoService.qqLogin(session, code, state);
        return getSuccessResponseVO(resultMap);
    }

}
