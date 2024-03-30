package com.thePan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

//@EnableAsync
//@EnableTransactionManagement
//@EnableScheduling // 定时任务
@SpringBootApplication(scanBasePackages = {"com.thePan"})
public class ThePanApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThePanApplication.class, args);
    }
}
