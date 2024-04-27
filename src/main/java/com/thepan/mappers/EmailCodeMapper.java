package com.thepan.mappers;


import com.thepan.dao.EmailCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邮箱验证码 数据库操作接口
 */
@Mapper
public interface EmailCodeMapper extends BaseMapper {

    void disableCheckCode(@Param("email") String email);

    EmailCode selectByEmailAndCode(String email, String code);

    void disableEmailCode(String email);
}
