package com.thepan.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.UploadResultDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.enums.FileCategoryEnums;
import com.thepan.entity.enums.FileDelFlagEnums;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.vo.file.FileInfoVO;
import com.thepan.entity.vo.file.FolderVO;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.response.ResponseVO;
import com.thepan.service.FileInfoService;
import com.thepan.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 文件信息 Controller
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileInfoController {

    @Resource
    private FileInfoService fileInfoService;
    /**
     * 根据条件分页查询
     */
    @RequestMapping("/loadDataList")
    @GlobalInterceptor
    public ResponseVO loadDataList(HttpSession session, FileInfoQuery query, String category) {
        FileCategoryEnums categoryEnum = FileCategoryEnums.getByCode(category);
        if (categoryEnum != null) {
            query.setFileCategory(categoryEnum.getCategory());
        }
        query.setUserId(SessionUtil.getUserInfoFromSession(session).getUserId());
        query.setOrderBy("last_update_time desc");
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        PaginationResultVO result = fileInfoService.findListByPage(query);

        return ResponseUtil.getSuccessResponseVO(ResponseUtil.convert2PaginationVO(result, FileInfoVO.class));
    }

    /**
     *
     * @param session
     * @param fileId
     * @param file
     * @param fileName
     * @param filePid
     * @param fileMd5
     * @param chunkIndex 切片索引
     * @param chunks    总共有多少切片
     * @return
     */
    @RequestMapping("/uploadFile")
    @GlobalInterceptor
    public ResponseVO uploadFile(HttpSession session,
                                 String fileId,
                                 MultipartFile file,
                                 @VerifyParam(required = true) String fileName,
                                 @VerifyParam(required = true) String filePid,
                                 @VerifyParam(required = true) String fileMd5,
                                 @VerifyParam(required = true) Integer chunkIndex,
                                 @VerifyParam(required = true) Integer chunks) {

        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        UploadResultDto resultDto = fileInfoService.uploadFile(webUserDto, fileId, file, fileName, filePid, fileMd5, chunkIndex, chunks);
        return ResponseUtil.getSuccessResponseVO(resultDto);
    }

    @RequestMapping("/getImage/{imageFolder}/{imageName}")
    public void getImage(HttpServletResponse response, @PathVariable("imageFolder") String imageFolder, @PathVariable("imageName") String imageName) {
        fileInfoService.getImage(response, imageFolder, imageName);
    }

    @RequestMapping("/getFile/{fileId}")
    public void getFile(HttpServletResponse response, HttpSession session, @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        fileInfoService.getfile(response, session, fileId);
    }

    @RequestMapping("/newFoloder")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO newFoloder(HttpSession session,
                                 @VerifyParam(required = true) String filePid,
                                 @VerifyParam(required = true) String fileName) {
        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        FileInfo fileInfo = fileInfoService.newFolder(filePid, webUserDto.getUserId(), fileName);
        return ResponseUtil.getSuccessResponseVO(fileInfo);
    }

    @RequestMapping("/getFolderInfo")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO getFolderInfo(HttpSession session, @VerifyParam(required = true) String path) {
        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        List<FileInfo> fileInfoList = fileInfoService.getFolderInfo(path, webUserDto.getUserId());
        return ResponseUtil.getSuccessResponseVO(BeanUtil.copyToList(fileInfoList, FolderVO.class));
    }

    @RequestMapping("/rename")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO rename(HttpSession session,
                             @VerifyParam(required = true) String fileId,
                             @VerifyParam(required = true) String fileName) {
        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        FileInfo fileInfo = fileInfoService.rename(fileId, webUserDto.getUserId(), fileName);
        return ResponseUtil.getSuccessResponseVO(CopyTools.copy(fileInfo, FileInfoVO.class));
    }

    @RequestMapping("/loadAllFolder")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO loadAllFolder(HttpSession session, @VerifyParam(required = true) String filePid, String currentFileIds) {
        List<FileInfo> fileInfoList = fileInfoService.loadAllFolder(session, filePid, currentFileIds);
        return ResponseUtil.getSuccessResponseVO(CopyTools.copyList(fileInfoList, FileInfoVO.class));
    }

    @RequestMapping("/changeFileFolder")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO changeFileFolder(HttpSession session,
                                       @VerifyParam(required = true) String fileIds,
                                       @VerifyParam(required = true) String filePid) {
        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        fileInfoService.changeFileFolder(fileIds, filePid, webUserDto.getUserId());
        return ResponseUtil.getSuccessResponseVO(null);
    }

    @RequestMapping("/createDownloadUrl/{fileId}")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO createDownloadUrl(HttpSession session, @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        String code = fileInfoService.createDownloadUrl(fileId, userId);
        return ResponseUtil.getSuccessResponseVO(code);
    }

    @RequestMapping("/download/{code}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public void download(HttpServletRequest request, HttpServletResponse response, @PathVariable("code") @VerifyParam(required = true) String code) throws Exception {
        fileInfoService.download(request, response, code);
    }

    @RequestMapping("/delFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO delFile(HttpSession session, @VerifyParam(required = true) String fileIds) {
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        fileInfoService.removeFile2RecycleBatch(userId, fileIds);
        return ResponseUtil.getSuccessResponseVO(null);
    }
}