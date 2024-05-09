package com.thepan.service;


import com.thepan.entity.dao.FileShare;
import com.thepan.entity.query.FileShareQuery;
import com.thepan.entity.vo.file.PaginationResultVO;

import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * 分享信息 业务接口
 */
public interface FileShareService {

    Integer findCountByParam(FileShareQuery param);

    List<FileShare> findListByParam(FileShareQuery param);

    PaginationResultVO<FileShare> findListByPage(FileShareQuery param);

    PaginationResultVO loadShareList(HttpSession session, FileShareQuery query);

    FileShare shareFile(HttpSession session, String fileId, Integer validType, String code);

    void deleteFileShareBatch(String shareIds, String userId);
}