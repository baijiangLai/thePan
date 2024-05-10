package com.thepan.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class SenderProperties {

    /**
     * 发送人
     */
    @Value("${spring.mail.username:}")
    private String sendUserName;


    /**
     * 发件人密码
     */
    @Value("${spring.mail.password}")
    private String password;

}
