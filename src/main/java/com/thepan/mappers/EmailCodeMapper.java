package com.thepan.mappers;


import com.thepan.entity.dao.EmailCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邮箱验证码 数据库操作接口
 */
@Mapper
public interface EmailCodeMapper {

    void disableCheckCode(@Param("email") String email);

    EmailCode selectByEmailAndCode(@Param("email") String email, @Param("code") String code);

    void disableEmailCode(@Param("email") String email);

    Integer insert(@Param("emailCode") EmailCode emailCode);
}
