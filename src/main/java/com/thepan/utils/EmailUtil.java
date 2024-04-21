package com.thepan.utils;


import com.thepan.dao.SysSettingsDto;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

public class EmailUtil {

    /**
     * @param sendName 发件人
     * @param name     收件人
     * @param title    邮件标题
     * @param context  邮件内容
     * @return
     */
    public static String sendEmailUtil(String sendName, String name,String title, String context){
        try {
            // 创建Properties 类用于记录邮箱的一些属性
            Properties props = new Properties();
            // 表示SMTP发送邮件，必须进行身份验证
            props.put("mail.smtp.auth", "true");
            //此处填写SMTP服务器
            props.put("mail.smtp.host", "smtp.qq.com");
            //端口号，QQ邮箱端口587
            props.put("mail.smtp.port", "587");
            // 此处填写，写信人的账号
            props.put("mail.user",sendName);
            // 此处填写16位STMP口令
            props.put("mail.password", "yelxejtdfjsfbbca");

            // 构建授权信息，用于进行SMTP进行身份验证
            Authenticator authenticator = new Authenticator() {

                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    // 用户名、密码
                    String userName = props.getProperty("mail.user");
                    String password = props.getProperty("mail.password");
                    return new PasswordAuthentication(userName, password);
                }
            };
            // 使用环境属性和授权信息，创建邮件会话
            Session mailSession = Session.getInstance(props, authenticator);
            // 创建邮件消息
            MimeMessage message = new MimeMessage(mailSession);
            // 设置发件人
            InternetAddress form = new InternetAddress(props.getProperty("mail.user"));
            message.setFrom(form);

            // 设置收件人的邮箱
            InternetAddress to = new InternetAddress(name);
            message.setRecipient(Message.RecipientType.TO, to);

            // 设置邮件标题
            message.setSubject(title);

            // 设置邮件的内容体
            message.setContent(context, "text/html;charset=UTF-8");

            // 最后当然就是发送邮件啦
            Transport.send(message);

        } catch (MessagingException e) {
            return "您的验证码发送失败======>"+ e;
        }
        return "您的验证码发送成功======>验证码为："+ context;
    }
}





