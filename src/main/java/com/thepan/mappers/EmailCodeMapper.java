package com.thepan.mappers;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邮箱验证码 数据库操作接口
 */
@Mapper
public interface EmailCodeMapper<T, P> extends BaseMapper<T, P> {

        public void disableCheckCode(@Param("email") String email);

    T selectByEmailAndCode(String email, String code);

    void disableEmailCode(String email);
}
