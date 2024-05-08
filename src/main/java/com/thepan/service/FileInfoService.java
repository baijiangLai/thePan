package com.thepan.service;


import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.UploadResultDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.vo.file.PaginationResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件信息 业务接口
 */
public interface FileInfoService {


    Long getUserUseSpace(String userId);

    PaginationResultVO<FileInfo> findListByPage(FileInfoQuery query);

    UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5, Integer chunkIndex, Integer chunks);
}