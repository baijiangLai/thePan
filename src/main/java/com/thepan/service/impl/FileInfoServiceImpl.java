package com.thepan.service.impl;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.UploadResultDto;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.dto.UserSpaceDto;
import com.thepan.entity.enums.*;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.vo.file.DownloadFileDto;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.FileInfoMapper;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.FileInfoService;
import com.thepan.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.util.*;

/**
 * 文件信息 业务接口实现
 */
@Service
@Slf4j
public class FileInfoServiceImpl implements FileInfoService {

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    @Lazy
    private FileInfoServiceImpl fileInfoService;

    @Override
    public Long getUserUseSpace(String userId) {
        return fileInfoMapper.selectUseSpace(userId);
    }

    @Override
    public PaginationResultVO<FileInfo> findListByPage(FileInfoQuery param) {
        int count = findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileInfo> list = findListByParam(param);
        PaginationResultVO<FileInfo> result = new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    @Override
    public List<FileInfo> findListByParam(FileInfoQuery fileInfoQuery) {
        return fileInfoMapper.selectList(fileInfoQuery);
    }

    @Override
    public Integer findCountByParam(FileInfoQuery fileInfoQuery) {
        return fileInfoMapper.selectCount(fileInfoQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5,
                                      Integer chunkIndex, Integer chunks) {
        // 使用一个临时存放位置存储分片文件
        File tempFileFolder = null;
        Boolean uploadSuccess = true;

        try {
            if (StrUtil.isEmpty(fileId)) {
                fileId = StringTools.getRandomString(Constants.LENGTH_10);
            }

            UploadResultDto resultDto = new UploadResultDto();
            resultDto.setFileId(fileId);

            Date curDate = new Date();
            UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());

            // 第一个分片
            if (chunkIndex == 0) {
                FileInfoQuery fileInfoQuery = new FileInfoQuery();
                fileInfoQuery.setFileMd5(fileMd5);
                fileInfoQuery.setSimplePage(new SimplePage(0, 1));
                fileInfoQuery.setStatus(FileStatusEnums.USING.getStatus());

                // 如果数据库中有，那么就直接使用秒传即可
                List<FileInfo> dbFileList = this.fileInfoMapper.selectList(fileInfoQuery);
                //秒传
                if (!dbFileList.isEmpty()) {
                    FileInfo dbFile = dbFileList.get(0);
                    //判断文件状态
                    if (dbFile.getFileSize() + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                        throw new BusinessException(ResponseCodeEnum.CODE_904);
                    }
                    dbFile.setFileId(fileId);
                    dbFile.setFilePid(filePid);
                    dbFile.setUserId(webUserDto.getUserId());
                    dbFile.setFileMd5(null);
                    dbFile.setCreateTime(curDate);
                    dbFile.setLastUpdateTime(curDate);
                    dbFile.setStatus(FileStatusEnums.USING.getStatus());
                    dbFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
                    dbFile.setFileMd5(fileMd5);

                    fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
                    dbFile.setFileName(fileName);

                    fileInfoMapper.insert(dbFile);
                    resultDto.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
                    //更新用户空间使用
                    updateUserSpace(webUserDto, dbFile.getFileSize());

                    return resultDto;
                }
            }

            //暂存在临时目录
            String tempFolderName = FolderUtil.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            //创建临时目录
            tempFileFolder = new File(tempFolderName + currentUserFolderName);
            if (!tempFileFolder.exists()) {
                tempFileFolder.mkdirs();
            }

            //判断磁盘空间
            Long currentTempSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            if (file.getSize() + currentTempSize + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }

            File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
            file.transferTo(newFile);
            //保存临时大小
            redisComponent.saveFileTempSize(webUserDto.getUserId(), fileId, file.getSize());
            //不是最后一个分片，直接返回
            if (chunkIndex < chunks - 1) {
                resultDto.setStatus(UploadStatusEnums.UPLOADING.getCode());
                return resultDto;
            }

            //最后一个分片上传完成，记录数据库，异步合并分片
            String month = DateUtil.format(curDate, DateTimePatternEnum.YYYYMM.getPattern());
            String fileSuffix = StringTools.getFileSuffix(fileName);

            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            FileTypeEnums fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);

            //自动重命名
            fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileId(fileId);
            fileInfo.setUserId(webUserDto.getUserId());
            fileInfo.setFileMd5(fileMd5);
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(month + "/" + realFileName);
            fileInfo.setFilePid(filePid);
            fileInfo.setCreateTime(curDate);
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
            fileInfo.setFileType(fileTypeEnum.getType());
            fileInfo.setStatus(FileStatusEnums.TRANSFER.getStatus());
            fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());

            fileInfoMapper.insert(fileInfo);

            Long totalSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            updateUserSpace(webUserDto, totalSize);

            resultDto.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());
            //事务提交后调用异步方法
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileInfoService.transferFile(fileInfo.getFileId(), webUserDto);
                }
            });
            return resultDto;
        } catch (BusinessException e) {
            uploadSuccess = false;
            log.error("文件上传失败", e);
            throw e;
        } catch (Exception e) {
            uploadSuccess = false;
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败");
        } finally {
            //如果上传失败，清除临时目录
            if (tempFileFolder != null && !uploadSuccess) {
                try {
                    FileUtils.deleteDirectory(tempFileFolder);
                } catch (IOException e) {
                    log.error("删除临时目录失败");
                }
            }
        }
    }

    @Override
    public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
        return fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
    }

    @Override
    public void getImage(HttpServletResponse response, String imageFolder, String imageName) {
        if (StringTools.isEmpty(imageFolder) || StringUtils.isBlank(imageName)) {
            return;
        }

        String imageSuffix = StringTools.getFileSuffix(imageName);
        String filePath = FolderUtil.getProjectFolder() + Constants.FILE_FOLDER_FILE + imageFolder + "/" + imageName;
        imageSuffix = imageSuffix.replace(".", "");

        String contentType = "image/" + imageSuffix;
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "max-age=2592000");

        if (!FileUtil.exist(filePath)) {
            return;
        }

        try {
            File file = FileUtil.file(filePath);
            FileUtil.writeToStream(file, response.getOutputStream());
        } catch (IOException e) {
            log.error("读取文件异常", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo newFolder(String filePid, String userId, String folderName) {
        boolean checkResult = checkFileName(filePid, userId, folderName, FileFolderTypeEnums.FOLDER.getType());
        FileInfo fileInfo = new FileInfo();
        Date curDate = new Date();
        if (checkResult) {
            fileInfo.setFileId(StringTools.getRandomString(Constants.LENGTH_10));
            fileInfo.setUserId(userId);
            fileInfo.setFilePid(filePid);
            fileInfo.setFileName(folderName);
            fileInfo.setFolderType(FileFolderTypeEnums.FOLDER.getType());
            fileInfo.setCreateTime(curDate);
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setStatus(FileStatusEnums.USING.getStatus());
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            fileInfoMapper.insert(fileInfo);

            // 多线程环境确保只能创建一个
            FileInfoQuery fileInfoQuery = new FileInfoQuery();
            fileInfoQuery.setFilePid(filePid);
            fileInfoQuery.setUserId(userId);
            fileInfoQuery.setFileName(folderName);
            fileInfoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());
            fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
            Integer count = fileInfoMapper.selectCount(fileInfoQuery);
            if (count > 1) {
                throw new BusinessException("文件夹" + folderName + "已经存在");
            }
            fileInfo.setFileName(folderName);
            fileInfo.setLastUpdateTime(curDate);
        }
        return fileInfo;
    }

    private boolean checkFileName(String filePid, String userId, String fileName, Integer folderType) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFolderType(folderType);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        Integer count = fileInfoMapper.selectCount(fileInfoQuery);
        return count == 0;
    }

    @Async
    public void transferFile(String fileId, SessionWebUserDto webUserDto) {
        Boolean transferSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(fileId, webUserDto.getUserId());
        try {

            if (fileInfo == null || !FileStatusEnums.TRANSFER.getStatus().equals(fileInfo.getStatus())) {
                return;
            }

            //临时目录
            String tempFolderName = FolderUtil.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + currentUserFolderName);
            if (!fileFolder.exists()) {
                fileFolder.mkdirs();
            }

            //文件后缀
            String fileSuffix = StringTools.getFileSuffix(fileInfo.getFileName());
            String month = DateUtil.format(fileInfo.getCreateTime(), DateTimePatternEnum.YYYYMM.getPattern());
            //目标目录
            String targetFolderName = FolderUtil.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFolder = new File(targetFolderName + "/" + month);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }

            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            //真实文件路径
            targetFilePath = targetFolder.getPath() + "/" + realFileName;
            //合并文件
            union(fileFolder.getPath(), targetFilePath, fileInfo.getFileName(), true);

        } catch (Exception e) {
            log.error("文件转码失败，文件Id:{},userId:{}", fileId, webUserDto.getUserId(), e);
            transferSuccess = false;
        } finally {
            FileInfo updateInfo = new FileInfo();
            updateInfo.setFileSize(new File(targetFilePath).length());
            updateInfo.setFileCover(cover);
            updateInfo.setStatus(transferSuccess ? FileStatusEnums.USING.getStatus() : FileStatusEnums.TRANSFER_FAIL.getStatus());
            fileInfoMapper.updateFileStatusWithOldStatus(fileId, webUserDto.getUserId(), updateInfo, FileStatusEnums.TRANSFER.getStatus());
        }
    }

    private void updateUserSpace(SessionWebUserDto webUserDto, Long totalSize) {
        Integer count = userInfoMapper.updateUserSpace(webUserDto.getUserId(), totalSize, null);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }
        UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());
        spaceDto.setUseSpace(spaceDto.getUseSpace() + totalSize);
        redisComponent.saveUserSpaceUse(webUserDto.getUserId(), spaceDto);
    }

    private String autoRename(String filePid, String userId, String fileName) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoQuery.setFileName(fileName);
        Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            return StringTools.rename(fileName);
        }

        return fileName;
    }

    public static void union(String dirPath, String toFilePath, String fileName, boolean delSource) throws BusinessException {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new BusinessException("目录不存在");
        }
        File fileList[] = dir.listFiles();
        File targetFile = new File(toFilePath);
        RandomAccessFile writeFile = null;
        try {
            writeFile = new RandomAccessFile(targetFile, "rw");
            byte[] b = new byte[1024 * 10];
            for (int i = 0; i < fileList.length; i++) {
                int len = -1;
                //创建读块文件的对象
                File chunkFile = new File(dirPath + File.separator + i);
                RandomAccessFile readFile = null;
                try {
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(b)) != -1) {
                        writeFile.write(b, 0, len);
                    }
                } catch (Exception e) {
                    log.error("合并分片失败", e);
                    throw new BusinessException("合并文件失败");
                } finally {
                    readFile.close();
                }
            }
        } catch (Exception e) {
            log.error("合并文件:{}失败", fileName, e);
            throw new BusinessException("合并文件" + fileName + "出错了");
        } finally {
            try {
                if (null != writeFile) {
                    writeFile.close();
                }
            } catch (IOException e) {
                log.error("关闭流失败", e);
            }
            if (delSource) {
                if (dir.exists()) {
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public List<FileInfo> getFolderInfo(String path, String userId) {
        // path: /folder1/folder2/file.txt
        // orderBy: field(file_id,"folder1","folder2","file.txt")
        String[] pathArray = path.split("/");
        String orderBy = "field(file_id,\"" + StringUtils.join(pathArray, "\",\"") + "\")";

        FileInfoQuery infoQuery = new FileInfoQuery();
        infoQuery.setUserId(userId);
        infoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        infoQuery.setFileIdArray(pathArray);
        infoQuery.setOrderBy(orderBy);

        return fileInfoService.findListByParam(infoQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo rename(String fileId, String userId, String fileName) {
        FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }
        if (fileInfo.getFileName().equals(fileName)) {
            return fileInfo;
        }
        String filePid = fileInfo.getFilePid();

        boolean checkResult = checkFileName(filePid, userId, fileName, fileInfo.getFolderType());

        //文件获取后缀
        if (FileFolderTypeEnums.FILE.getType().equals(fileInfo.getFolderType())) {
            fileName = fileName + StringTools.getFileSuffix(fileInfo.getFileName());
        }

        Date curDate = new Date();
        if (checkResult) {
            FileInfo dbInfo = new FileInfo();
            dbInfo.setFileName(fileName);
            dbInfo.setLastUpdateTime(curDate);
            fileInfoMapper.updateByFileIdAndUserId(dbInfo, fileId, userId);

            // 多线程环境确保只有一个
            FileInfoQuery fileInfoQuery = new FileInfoQuery();
            fileInfoQuery.setFilePid(filePid);
            fileInfoQuery.setUserId(userId);
            fileInfoQuery.setFileName(fileName);
            fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
            Integer count = fileInfoMapper.selectCount(fileInfoQuery);
            if (count > 1) {
                throw new BusinessException("文件名" + fileName + "已经存在");
            }
            fileInfo.setFileName(fileName);
            fileInfo.setLastUpdateTime(curDate);
        }
        return fileInfo;
    }

    @Override
    public List<FileInfo> loadAllFolder(HttpSession session, String filePid, String currentFileIds) {
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(SessionUtil.getUserInfoFromSession(session).getUserId());
        query.setFilePid(filePid);
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());

        if (!StringTools.isEmpty(currentFileIds)) {
            query.setExcludeFileIdArray(currentFileIds.split(","));
        }
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        query.setOrderBy("create_time desc");
        return fileInfoService.findListByParam(query);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeFileFolder(String fileIds, String filePid, String userId) {
        if (fileIds.equals(filePid)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        if (!Constants.ZERO_STR.equals(filePid)) {
            FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(filePid, userId);
            if (fileInfo == null || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }

        String[] fileIdArray = fileIds.split(",");

        FileInfoQuery query = new FileInfoQuery();
        query.setFilePid(filePid);
        query.setUserId(userId);
        List<FileInfo> dbFileList = fileInfoService.findListByParam(query);

        //查询选中的文件
        Map<String, FileInfo> dbFileNameMap = new HashMap<>();
        for (FileInfo fileInfo : dbFileList) {
            // 将文件名作为键，文件信息对象作为值放入 Map 中
            dbFileNameMap.put(fileInfo.getFileName(), fileInfo);
        }

        query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        List<FileInfo> selectFileList = fileInfoService.findListByParam(query);

        //将所选文件重命名
        for (FileInfo item : selectFileList) {
            FileInfo rootFileInfo = dbFileNameMap.get(item.getFileName());
            //文件名已经存在，重命名被还原的文件名
            FileInfo newFileInfo = new FileInfo();
            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                newFileInfo.setFileName(fileName);
            }
            newFileInfo.setFilePid(filePid);
            fileInfoMapper.updateByFileIdAndUserId(newFileInfo, item.getFileId(), userId);
        }
    }

    @Override
    public String createDownloadUrl(String fileId, String userId) {
        FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, userId);

        if (fileInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        String code = StringTools.getRandomString(Constants.LENGTH_50);
        DownloadFileDto downloadFileDto = new DownloadFileDto();
        downloadFileDto.setDownloadCode(code);
        downloadFileDto.setFilePath(fileInfo.getFilePath());
        downloadFileDto.setFileName(fileInfo.getFileName());

        redisComponent.saveDownloadCode(code, downloadFileDto);
        return  code;
    }

    @Override
    public void download(HttpServletRequest request, HttpServletResponse response, String code) {
        DownloadFileDto downloadFileDto = redisComponent.getDownloadCode(code);
        if (null == downloadFileDto) {
            return;
        }
        String filePath = FolderUtil.getProjectFolder() + Constants.FILE_FOLDER_FILE + downloadFileDto.getFilePath();
        String fileName = downloadFileDto.getFileName();
        response.setContentType("application/x-msdownload; charset=UTF-8");
        try {
            if (request.getHeader("User-Agent").toLowerCase().indexOf("msie") > 0) {//IE浏览器
                fileName = URLEncoder.encode(fileName, "UTF-8");
            } else {
                fileName = new String(fileName.getBytes("UTF-8"), "ISO8859-1");
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");

        try {
            File file = FileUtil.file(filePath);
            FileUtil.writeToStream(file, response.getOutputStream());
        } catch (IOException e) {
            log.error("读取文件异常", e);
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFile2RecycleBatch(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");

        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);

        if (fileInfoList.isEmpty()) {
            return;
        }

        List<String> delFilePidList = new ArrayList<>();
        for (FileInfo fileInfo : fileInfoList) {
            findAllSubFolderFileIdList(delFilePidList, userId, fileInfo.getFileId(), FileDelFlagEnums.USING.getFlag());
        }

        //将目录下的所有文件更新为已删除
        if (!delFilePidList.isEmpty()) {
            FileInfo updateInfo = new FileInfo();
            updateInfo.setDelFlag(FileDelFlagEnums.DEL.getFlag());
            fileInfoMapper.updateFileDelFlagBatch(updateInfo, userId, delFilePidList, null, FileDelFlagEnums.USING.getFlag());
        }

        //将选中的文件更新为回收站
        List<String> delFileIdList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setRecoveryTime(new Date());
        fileInfo.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList, FileDelFlagEnums.USING.getFlag());
    }

    @Override
    public PaginationResultVO loadRecycleList(HttpSession session, Integer pageNo, Integer pageSize) {
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        FileInfoQuery query = new FileInfoQuery();
        query.setPageSize(pageSize);
        query.setPageNo(pageNo);
        query.setUserId(userId);
        query.setOrderBy("recovery_time desc");
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        PaginationResultVO result = fileInfoService.findListByPage(query);
        return result;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFileBatch(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");

        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);

        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        //找到所选文件子目录文件ID
        for (FileInfo fileInfo : fileInfoList) {
            if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
                findAllSubFolderFileIdList(delFileSubFolderFileIdList, userId, fileInfo.getFileId(), FileDelFlagEnums.DEL.getFlag());
            }
        }
        //查询所有跟目录的文件
        query = new FileInfoQuery();
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        query.setFilePid(Constants.ZERO_STR);
        List<FileInfo> allRootFileList = this.fileInfoMapper.selectList(query);
        Map<String, FileInfo> rootFileMap = new HashMap<>();

        for (FileInfo fileInfo: allRootFileList) {
            rootFileMap.put(fileInfo.getFileName(), fileInfo);
        }

        //查询所有所选文件
        //将目录下的所有删除的文件更新为正常
        if (!delFileSubFolderFileIdList.isEmpty()) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, delFileSubFolderFileIdList, null, FileDelFlagEnums.DEL.getFlag());
        }
        //将选中的文件更新为正常,且父级目录到跟目录
        List<String> delFileIdList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfo.setFilePid(Constants.ZERO_STR);
        fileInfo.setLastUpdateTime(new Date());
        fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList, FileDelFlagEnums.RECYCLE.getFlag());

        //将所选文件重命名
        for (FileInfo item : fileInfoList) {
            FileInfo rootFileInfo = rootFileMap.get(item.getFileName());
            //文件名已经存在，重命名被还原的文件名
            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                FileInfo updateInfo = new FileInfo();
                updateInfo.setFileName(fileName);
                fileInfoMapper.updateByFileIdAndUserId(updateInfo, item.getFileId(), userId);
            }
        }
    }
    @Override
    public void recoverFile(HttpSession session, String fileIds) {
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        recoverFileBatch(userId, fileIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFileBatch(String userId, String fileIds, Boolean adminOp) {
        String[] fileIdArray = fileIds.split(",");

        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        if (!adminOp) {
            query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        }

        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);

        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        //找到所选文件子目录文件ID
        for (FileInfo fileInfo : fileInfoList) {
            if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
                findAllSubFolderFileIdList(delFileSubFolderFileIdList, userId, fileInfo.getFileId(), FileDelFlagEnums.DEL.getFlag());
            }
        }

        //删除所选文件，子目录中的文件
        if (!delFileSubFolderFileIdList.isEmpty()) {
            fileInfoMapper.delFileBatch(userId, delFileSubFolderFileIdList, null, adminOp ? null : FileDelFlagEnums.DEL.getFlag());
        }
        //删除所选文件
        fileInfoMapper.delFileBatch(userId, null, Arrays.asList(fileIdArray), adminOp ? null : FileDelFlagEnums.RECYCLE.getFlag());

        Long useSpace = fileInfoMapper.selectUseSpace(userId);
        UserInfo userInfo = new UserInfo();
        userInfo.setUseSpace(useSpace);
        userInfoMapper.updateByUserId(userInfo, userId);

        //设置缓存
        UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(userId);
        userSpaceDto.setUseSpace(useSpace);
        redisComponent.saveUserSpaceUse(userId, userSpaceDto);
    }

    @Override
    public void delFile(HttpSession session, String fileIds) {
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        delFileBatch(userId, fileIds,false);
    }

    private void findAllSubFolderFileIdList(List<String> fileIdList, String userId, String fileId, Integer delFlag) {
        fileIdList.add(fileId);
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFilePid(fileId);
        query.setDelFlag(delFlag);
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);
        for (FileInfo fileInfo : fileInfoList) {
            findAllSubFolderFileIdList(fileIdList, userId, fileInfo.getFileId(), delFlag);
        }
    }

    @Override
    public void deleteFileByUserId(String userId) {
        fileInfoMapper.deleteFileByUserId(userId);
    }

    @Override
    public void checkRootFilePid(String rootFilePid, String userId, String fileId) {
        if (StringTools.isEmpty(fileId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (rootFilePid.equals(fileId)) {
            return;
        }
        checkFilePid(rootFilePid, fileId, userId);
    }

    @Override
    public PaginationResultVO<FileInfo> loadDataList(HttpSession session, FileInfoQuery query, String category) {
        FileCategoryEnums categoryEnum = FileCategoryEnums.getByCode(category);
        if (categoryEnum != null) {
            query.setFileCategory(categoryEnum.getCategory());
        }
        query.setUserId(SessionUtil.getUserInfoFromSession(session).getUserId());
        query.setOrderBy("last_update_time desc");
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        return findListByPage(query);
    }

    private void checkFilePid(String rootFilePid, String fileId, String userId) {
        FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (Constants.ZERO_STR.equals(fileInfo.getFilePid())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (fileInfo.getFilePid().equals(rootFilePid)) {
            return;
        }
        checkFilePid(rootFilePid, fileInfo.getFilePid(), userId);
    }
}