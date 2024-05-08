package com.thepan.mappers;

import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.query.FileInfoQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件信息 数据库操作接口
 */
@Mapper
public interface FileInfoMapper {


    Long selectUseSpace(@Param("userId") String userId);

    List<FileInfo> selectList(@Param("fileInfoQuery") FileInfoQuery fileInfoQuery);

    Integer selectCount(@Param("fileInfoQuery") FileInfoQuery fileInfoQuery);

    Integer insert(@Param("fileInfo") FileInfo fileInfo);

    FileInfo selectByFileIdAndUserId(@Param("fileId") String fileId, @Param("userId") String userId);

    Integer updateFileStatusWithOldStatus(@Param("fileId") String fileId, @Param("userId") String userId, @Param("fileInfo") FileInfo fileInfo, @Param("status") Integer status);
}
