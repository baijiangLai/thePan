package com.thepan.service.impl;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.UploadResultDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.dto.UserSpaceDto;
import com.thepan.entity.enums.*;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.FileInfoMapper;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.FileInfoService;
import com.thepan.utils.DateUtil;
import com.thepan.utils.FolderUtil;
import com.thepan.utils.RedisComponent;
import com.thepan.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.List;

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

    private List<FileInfo> findListByParam(FileInfoQuery fileInfoQuery) {
        return fileInfoMapper.selectList(fileInfoQuery);
    }

    private int findCountByParam(FileInfoQuery fileInfoQuery) {
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

    @Async
    public void transferFile(String fileId, SessionWebUserDto webUserDto) {
        Boolean transferSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileTypeEnums fileTypeEnum = null;
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
}