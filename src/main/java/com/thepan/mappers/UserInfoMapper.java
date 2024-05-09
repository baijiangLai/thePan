package com.thepan.mappers;


import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.query.UserInfoQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户信息 数据库操作接口
 */
@Mapper
public interface UserInfoMapper {

    /**
     * 根据Email获取对象
     */
    UserInfo selectByEmail(@Param("email") String email);

    /**
     * 根据nick获取对象
     */
    UserInfo selectByNickname(@Param("nickName") String nickName);


    Integer updateByUserId(@Param("userInfo") UserInfo userInfo, @Param("userId") String userId);


    Integer updateByEmail(@Param("userInfo") UserInfo userInfo, @Param("email") String email);

    UserInfo selectByQqOpenId(@Param("qqOpenId") String qqOpenId);

    Integer insertUserInfo(@Param("userInfo") UserInfo userInfo);

    Integer updateByQqOpenId(@Param("userInfo") UserInfo userInfo, @Param("qqOpenId") String qqOpenId);

    List<UserInfo> selectList(@Param("userInfoQuery")UserInfoQuery userInfoQuery);

    Integer updateUserSpace(@Param("userId") String userId, @Param("useSpace") Long useSpace, @Param("totalSpace") Long totalSpace);

    Integer selectCount(@Param("userInfoQuery") UserInfoQuery userInfoQuery);

    UserInfo selectByUserId(@Param("userId") String userId);
}
