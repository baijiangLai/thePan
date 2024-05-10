package com.thepan.service;


import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.UploadResultDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.vo.file.PaginationResultVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * 文件信息 业务接口
 */
public interface FileInfoService {


    Long getUserUseSpace(String userId);

    PaginationResultVO<FileInfo> findListByPage(FileInfoQuery query);

    UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5, Integer chunkIndex, Integer chunks);


    FileInfo getFileInfoByFileIdAndUserId(String realFileId, String userId);

    void getImage(HttpServletResponse response, String imageFolder, String imageName);

    FileInfo newFolder(String filePid, String userId, String fileName);

    List<FileInfo> getFolderInfo(String path, String userId);

    FileInfo rename(String fileId, String userId, String fileName);

    List<FileInfo> loadAllFolder(HttpSession session, String filePid, String currentFileIds);

    void changeFileFolder(String fileIds, String filePid, String userId);

    String createDownloadUrl(String fileId, String userId);

    void download(HttpServletRequest request, HttpServletResponse response, String code);

    void removeFile2RecycleBatch(String userId, String fileIds);

    PaginationResultVO loadRecycleList(HttpSession session, Integer pageNo, Integer pageSize);

    void recoverFileBatch(String userId, String fileIds);
    void recoverFile(HttpSession session, String fileIds);

    void delFileBatch(String userId, String fileIds, Boolean adminOp);
    void delFile(HttpSession session, String fileIds);

    void deleteFileByUserId(String userId);

    List<FileInfo> findListByParam(FileInfoQuery fileInfoQuery);

    Integer findCountByParam(FileInfoQuery fileInfoQuery);

    void checkRootFilePid(String fileId, String shareUserId, String filePid);

    PaginationResultVO<FileInfo> loadDataList(HttpSession session, FileInfoQuery query, String category);
}