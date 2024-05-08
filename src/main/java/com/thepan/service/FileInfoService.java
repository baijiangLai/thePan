package com.thepan.service;


import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.vo.file.PaginationResultVO;

import java.util.List;

/**
 * 文件信息 业务接口
 */
public interface FileInfoService {


    Long getUserUseSpace(String userId);

    PaginationResultVO<FileInfo> findListByPage(FileInfoQuery query);

}