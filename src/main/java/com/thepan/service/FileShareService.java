package com.thepan.service;


import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.FileShare;
import com.thepan.entity.dto.SessionShareDto;
import com.thepan.entity.query.FileShareQuery;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.file.ShareInfoVO;

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

    ShareInfoVO getShareLoginInfo(HttpSession session, String shareId);

    ShareInfoVO getShareInfo(String shareId);

    void checkShareCode(HttpSession session, String shareId, String code);

    PaginationResultVO loadFileList(HttpSession session, String shareId, String filePid);

    List<FileInfo> getFolderInfo(HttpSession session, String shareId, String path);

    void saveShare(HttpSession session, String shareId, String shareFileIds, String myFolderId);

    SessionShareDto checkShare(HttpSession session, String shareId);
}