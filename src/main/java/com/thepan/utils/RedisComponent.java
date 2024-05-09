package com.thepan.utils;

import com.thepan.constants.Constants;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.SysSettingsDto;
import com.thepan.entity.dto.UserSpaceDto;
import com.thepan.entity.vo.file.DownloadFileDto;
import com.thepan.mappers.FileInfoMapper;
import com.thepan.mappers.UserInfoMapper;
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

    @Resource
    private UserInfoMapper userInfoMapper;

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

    public Long getFileTempSize(String userId, String fileId) {
        Long currentSize = getFileSizeFromRedis(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId);
        return currentSize;
    }

    private Long getFileSizeFromRedis(String key) {
        Object sizeObj = redisUtil.get(key);
        if (sizeObj == null) {
            return 0L;
        }
        if (sizeObj instanceof Integer) {
            return ((Integer) sizeObj).longValue();
        } else if (sizeObj instanceof Long) {
            return (Long) sizeObj;
        }

        return 0L;
    }


    public void saveFileTempSize(String userId, String fileId, Long fileSize) {
        Long currentSize = getFileTempSize(userId, fileId);
        redisUtil.set(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId, currentSize + fileSize, 1, TimeUnit.HOURS);
    }

    public void saveDownloadCode(String code, DownloadFileDto downloadFileDto) {
        redisUtil.set(Constants.REDIS_KEY_DOWNLOAD + code, downloadFileDto, 1, TimeUnit.HOURS);
    }

    public DownloadFileDto getDownloadCode(String code) {
        return (DownloadFileDto) redisUtil.get(Constants.REDIS_KEY_DOWNLOAD + code);
    }

    public void saveSysSettingsDto(SysSettingsDto sysSettingsDto) {
        redisUtil.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto);
    }

    public UserSpaceDto resetUserSpaceUse(String userId) {
        UserSpaceDto spaceDto = new UserSpaceDto();
        Long useSpace = fileInfoMapper.selectUseSpace(userId);
        spaceDto.setUseSpace(useSpace);

        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        spaceDto.setTotalSpace(userInfo.getTotalSpace());
        redisUtil.set(Constants.REDIS_KEY_USER_SPACE_USE + userId, spaceDto, 1, TimeUnit.DAYS);
        return spaceDto;
    }
}
