package com.thepan.mappers;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户信息 数据库操作接口
 */
@Mapper
public interface UserInfoMapper<T, P> extends BaseMapper<T, P> {

    /**
     * 根据Email获取对象
     */
    T selectByEmail(@Param("email") String email);

    /**
     * 根据nick获取对象
     */
    T selectByNickname(@Param("nickName") String nickName);

}
