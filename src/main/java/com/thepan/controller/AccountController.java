package com.thepan.controller;

import com.thepan.aspect.annotation.GlobalIntercepter;
import com.thepan.aspect.annotation.VerifyParam;
import com.thepan.constants.Constants;
import com.thepan.enums.VerifyRegexEnum;
import com.thepan.service.EmailCodeService;
import com.thepan.utils.CreateImageCode;
import com.thepan.utils.ResponseUtils;
import com.thepan.vo.ResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.ws.Response;
import java.io.IOException;
import java.util.Date;


@RestController("accountController")
public class AccountController {
    @Autowired
    EmailCodeService emailCodeService;

    @GetMapping("/checkCode")
    public void checkcode(HttpServletResponse response, HttpSession session, Integer type) throws IOException { // 图片通过response传递给前端
        // 创建随机验证码
        CreateImageCode createImageCode = new CreateImageCode(130, 38, 5, 10);

        // 设置响应头，禁用浏览器缓存
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        //将验证码的数值存在session里面去 0:登录注册 1:邮箱验证码发送 默认0
        if (type=='0' || type==null){
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
    @GlobalIntercepter(checkParams = true)
    public ResponseVO sendEmailCode(HttpSession session,
                                          @VerifyParam(required = true, regex= VerifyRegexEnum.EMAIL) String email,
                                    @VerifyParam(required = true) String checkCode,
                                    @VerifyParam(required = true)  Integer type){
      try{
            if (!session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL).equals(checkCode.toLowerCase())){
                throw new Exception("验证码输入不正确，请重新输入！！！");
            }
          emailCodeService.sendEmailCode(email,type);
          return ResponseUtils.getSuccessResponseVO(new Date());
    } catch (Exception e) {
          e.printStackTrace();
          return ResponseUtils.getErrorResponseVO(new Date());
      }finally {
          session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL); // 移除旧验证码
      }
    }
}
