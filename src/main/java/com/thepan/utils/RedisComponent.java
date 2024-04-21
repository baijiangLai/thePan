package com.thepan.utils;

import com.thepan.constants.Constants;
import com.thepan.dao.SysSettingsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *  用于将数据存在redis数据库中， 以及从redis数据库中取用数据
 */
@Component
public class RedisComponent {
    @Autowired
    RedisUtil redisUtil;

    /**
     *  从redis数据库中提取提取系统设置sysSettingsDto，没有的情况直接初始化后存放于redis中
     */
    public SysSettingsDto getSysSettingsDto(){
        SysSettingsDto sysSettingsDto = (SysSettingsDto) redisUtil.get(Constants.REDIS_KEY_SYS_SETTING);
        if (sysSettingsDto==null){
            SysSettingsDto sysSettingsDto1 = new SysSettingsDto();
            redisUtil.set(Constants.REDIS_KEY_SYS_SETTING,sysSettingsDto1);
            sysSettingsDto = (SysSettingsDto) redisUtil.get(Constants.REDIS_KEY_SYS_SETTING);
        }
        return sysSettingsDto;
    }
}
