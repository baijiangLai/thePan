package com.thepan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@EnableAsync
//@EnableTransactionManagement
//@EnableScheduling // 定时任务
@SpringBootApplication(scanBasePackages = {"com.thepan"})
@MapperScan(basePackages = {"com.thepan.mappers"})
public class ThePanApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThePanApplication.class, args);
    }
}
