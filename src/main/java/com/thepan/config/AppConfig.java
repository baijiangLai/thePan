package com.thepan.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("AppConfig")
@Data
public class AppConfig {

    /**
     * 发送人
     */
    @Value("${spring.mail.username:}")
    private String sendUserName;


    /**
     * 发件人密码
     */
    @Value("${spring.mail.pring.mail.password:}")
    private String password;

}
