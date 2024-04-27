package com.thepan.mappers;


import com.thepan.dao.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户信息 数据库操作接口
 */
@Mapper
public interface UserInfoMapper extends BaseMapper {

    /**
     * 根据Email获取对象
     */
    UserInfo selectByEmail(@Param("email") String email);

    /**
     * 根据nick获取对象
     */
    UserInfo selectByNickname(@Param("nickName") String nickName);

}
