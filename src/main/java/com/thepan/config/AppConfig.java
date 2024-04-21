package com.thepan.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.sql.DataSourceDefinition;

@Component("AppConfig")
@Data
public class AppConfig {

    @Value("${spring.mail.username:}")
    private String sendUserName;

}
