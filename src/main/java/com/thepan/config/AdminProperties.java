package com.thepan.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class AdminProperties {
    @Value("${admin.emails:}")
    private String adminEmails;
}
