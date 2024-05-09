package com.thepan.mappers;

import com.thepan.entity.dao.FileShare;
import com.thepan.entity.query.FileShareQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分享信息 数据库操作接口
 */
public interface FileShareMapper {

    Integer selectCount(@Param("fileShareQuery") FileShareQuery fileShareQuery);

    List<FileShare> selectList(@Param("fileShareQuery") FileShareQuery fileShareQuery);

    Integer insert(@Param("fileShare") FileShare share);

    Integer deleteFileShareBatch(@Param("shareIdArray") String[] shareIdArray, @Param("userId") String userId);
}
