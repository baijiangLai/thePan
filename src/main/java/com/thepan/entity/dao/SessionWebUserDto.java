package com.thepan.entity.dao;

import lombok.Data;

@Data
public class SessionWebUserDto {
    private String nickName;
    private String userId;
    private Boolean admin;
    private String avatar;
}
