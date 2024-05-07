package com.thepan.utils;

import com.thepan.constants.Constants;
import com.thepan.entity.dao.SysSettingsDto;
import com.thepan.entity.dao.UserSpaceDto;
import com.thepan.mappers.FileInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 *  用于将数据存在redis数据库中， 以及从redis数据库中取用数据
 */
@Component
public class RedisComponent {
    @Autowired
    private RedisUtil redisUtil;

    @Resource
    private FileInfoMapper fileInfoMapper;

    /**
     *  从redis数据库中提取提取系统设置sysSettingsDto，没有的情况直接初始化后存放于redis中
     */
    public SysSettingsDto getSysSettingsDto(){
        SysSettingsDto sysSettingsDto = (SysSettingsDto) redisUtil.get(Constants.REDIS_KEY_SYS_SETTING);
        if (sysSettingsDto==null){
            sysSettingsDto = new SysSettingsDto();
            redisUtil.set(Constants.REDIS_KEY_SYS_SETTING,sysSettingsDto);
//            sysSettingsDto = (SysSettingsDto) redisUtil.get(Constants.REDIS_KEY_SYS_SETTING);
        }
        return sysSettingsDto;
    }

    /**
     * 保存已使用的空间
     *
     * @param userId
     */
    public void saveUserSpaceUse(String userId, UserSpaceDto userSpaceDto) {
        redisUtil.set(Constants.REDIS_KEY_USER_SPACE_USE + userId, userSpaceDto, 1, TimeUnit.DAYS);
    }

    public UserSpaceDto getUserSpaceUse(String userId) {
        UserSpaceDto spaceDto = (UserSpaceDto) redisUtil.get(Constants.REDIS_KEY_USER_SPACE_USE + userId);
        if (null == spaceDto) {
            spaceDto = new UserSpaceDto();
            Long useSpace = fileInfoMapper.selectUseSpace(userId);
            spaceDto.setUseSpace(useSpace);
            spaceDto.setTotalSpace(getSysSettingsDto().getUserInitUseSpace() * Constants.MB);
            redisUtil.set(Constants.REDIS_KEY_USER_SPACE_USE + userId, spaceDto, 1, TimeUnit.DAYS);
        }
        return spaceDto;
    }
}
