package com.thepan.mappers;

import org.apache.ibatis.annotations.Param;

/**
 * 文件信息 数据库操作接口
 */
public interface FileInfoMapper extends BaseMapper {


    Long selectUseSpace(@Param("userId") String userId);
}
