package com.thepan.service.impl;


import com.thepan.constants.Constants;
import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dao.FileShare;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.SessionShareDto;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.dto.UserSpaceDto;
import com.thepan.entity.enums.*;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.query.FileShareQuery;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.file.ShareInfoVO;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.FileInfoMapper;
import com.thepan.mappers.FileShareMapper;
import com.thepan.mappers.UserInfoMapper;
import com.thepan.service.FileInfoService;
import com.thepan.service.FileShareService;
import com.thepan.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分享信息 业务接口实现
 */
@Service
public class FileShareServiceImpl implements FileShareService {
    @Resource
    private FileShareMapper fileShareMapper;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private FileInfoService fileInfoService;

    @Resource
    private RedisComponent redisComponent;

    @Override
    public Integer findCountByParam(FileShareQuery fileShareQuery) {
        return fileShareMapper.selectCount(fileShareQuery);
    }

    @Override
    public List<FileShare> findListByParam(FileShareQuery fileShareQuery) {
        return fileShareMapper.selectList(fileShareQuery);
    }

    @Override
    public PaginationResultVO<FileShare> findListByPage(FileShareQuery param) {
        int count = findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileShare> list = findListByParam(param);
        PaginationResultVO<FileShare> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    @Override
    public PaginationResultVO loadShareList(HttpSession session, FileShareQuery query) {
        query.setOrderBy("share_time desc");
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        query.setUserId(userId);
        query.setQueryFileName(true);
        return findListByPage(query);
    }

    @Override
    public FileShare shareFile(HttpSession session, String fileId, Integer validType, String code) {
        ShareValidTypeEnums typeEnum = ShareValidTypeEnums.getByType(validType);
        if (null == typeEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        FileShare share = new FileShare();
        share.setFileId(fileId);
        share.setValidType(validType);
        share.setCode(code);
        share.setUserId(userId);

        if (typeEnum != ShareValidTypeEnums.FOREVER) {
            share.setExpireTime(DateUtil.getAfterDate(typeEnum.getDays()));
        }
        Date curDate = new Date();
        share.setShareTime(curDate);

        if (StringTools.isEmpty(share.getCode())) {
            share.setCode(StringTools.getRandomString(Constants.LENGTH_5));
        }
        share.setShareId(StringTools.getRandomString(Constants.LENGTH_20));
        fileShareMapper.insert(share);
        return share;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileShareBatch(String shareIds, String userId) {
        String[] shareIdArray = shareIds.split(",");
        Integer count = fileShareMapper.deleteFileShareBatch(shareIdArray, userId);
        if (count != shareIdArray.length) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    @Override
    public ShareInfoVO getShareLoginInfo(HttpSession session, String shareId) {
        SessionShareDto shareSessionDto = SessionUtil.getSessionShareFromSession(session, shareId);
        if (shareSessionDto == null) {
            return null;
        }
        ShareInfoVO shareInfoVO = getShareInfoCommon(shareId);
        //判断是否是当前用户分享的文件
        SessionWebUserDto userDto = SessionUtil.getUserInfoFromSession(session);
        if (userDto != null && userDto.getUserId().equals(shareSessionDto.getShareUserId())) {
            shareInfoVO.setCurrentUser(true);
        } else {
            shareInfoVO.setCurrentUser(false);
        }
        return shareInfoVO;
    }

    @Override
    public ShareInfoVO getShareInfo(String shareId) {
        return getShareInfoCommon(shareId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkShareCode(HttpSession session, String shareId, String code) {
        FileShare share = fileShareMapper.selectByShareId(shareId);

        if (null == share || (share.getExpireTime() != null && new Date().after(share.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }

        if (!share.getCode().equals(code)) {
            throw new BusinessException("提取码错误");
        }

        //更新浏览次数
        fileShareMapper.updateShareShowCount(shareId);
        SessionShareDto shareSessionDto = new SessionShareDto();
        shareSessionDto.setShareId(shareId);
        shareSessionDto.setShareUserId(share.getUserId());
        shareSessionDto.setFileId(share.getFileId());
        shareSessionDto.setExpireTime(share.getExpireTime());

        session.setAttribute(Constants.SESSION_SHARE_KEY + shareId, shareSessionDto);
    }

    @Override
    public PaginationResultVO loadFileList(HttpSession session, String shareId, String filePid) {
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        FileInfoQuery query = new FileInfoQuery();
        if (!StringTools.isEmpty(filePid) && !Constants.ZERO_STR.equals(filePid)) {
            fileInfoService.checkRootFilePid(shareSessionDto.getFileId(), shareSessionDto.getShareUserId(), filePid);
            query.setFilePid(filePid);
        } else {
            query.setFileId(shareSessionDto.getFileId());
        }
        query.setUserId(shareSessionDto.getShareUserId());
        query.setOrderBy("last_update_time desc");
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        PaginationResultVO resultVO = fileInfoService.findListByPage(query);
        return resultVO;
    }

    @Override
    public List<FileInfo> getFolderInfo(HttpSession session, String shareId, String path) {
        SessionShareDto sessionShareDto = checkShare(session, shareId);
        String[] pathArray = path.split("/");
        FileInfoQuery infoQuery = new FileInfoQuery();
        infoQuery.setUserId(sessionShareDto.getShareUserId());
        infoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        infoQuery.setFileIdArray(pathArray);
        String orderBy = "field(file_id,\"" + StringUtils.join(pathArray, "\",\"") + "\")";
        infoQuery.setOrderBy(orderBy);
        return fileInfoService.findListByParam(infoQuery);
    }

    @Override
    @Transactional
    public void saveShare(HttpSession session, String shareId, String shareFileIds, String myFolderId) {

        SessionShareDto shareSessionDto = checkShare(session, shareId);
        SessionWebUserDto webUserDto = SessionUtil.getUserInfoFromSession(session);
        String cureentUserId = webUserDto.getUserId();
        String shareUserId = shareSessionDto.getShareUserId();
        if (shareSessionDto.getShareUserId().equals(webUserDto.getUserId())) {
            throw new BusinessException("自己分享的文件无法保存到自己的网盘");
        }

        String[] shareFileIdArray = shareFileIds.split(",");
        //目标目录文件列表
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(cureentUserId);
        fileInfoQuery.setFilePid(myFolderId);
        List<FileInfo> currentFileList = fileInfoMapper.selectList(fileInfoQuery);
        Map<String, FileInfo> currentFileMap = currentFileList.stream().collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //选择的文件
        fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(shareUserId);
        fileInfoQuery.setFileIdArray(shareFileIdArray);
        List<FileInfo> shareFileList = fileInfoMapper.selectList(fileInfoQuery);
        //重命名选择的文件
        List<FileInfo> copyFileList = new ArrayList<>();
        Date curDate = new Date();
        for (FileInfo item : shareFileList) {
            FileInfo haveFile = currentFileMap.get(item.getFileName());
            if (haveFile != null) {
                item.setFileName(StringTools.rename(item.getFileName()));
            }
            findAllSubFile(copyFileList, item, shareUserId, cureentUserId, curDate, myFolderId);
        }
        fileInfoMapper.insertBatch(copyFileList);

        //更新空间
        Long useSpace = fileInfoMapper.selectUseSpace(cureentUserId);
        UserInfo dbUserInfo = userInfoMapper.selectByUserId(cureentUserId);
        if (useSpace > dbUserInfo.getTotalSpace()) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUseSpace(useSpace);
        userInfoMapper.updateByUserId(userInfo, cureentUserId);
        //设置缓存
        UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(cureentUserId);
        userSpaceDto.setUseSpace(useSpace);
        redisComponent.saveUserSpaceUse(cureentUserId, userSpaceDto);
    }

    private void findAllSubFile(List<FileInfo> copyFileList, FileInfo fileInfo, String sourceUserId, String currentUserId, Date curDate, String newFilePid) {
        String sourceFileId = fileInfo.getFileId();
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setFilePid(newFilePid);
        fileInfo.setUserId(currentUserId);
        String newFileId = StringTools.getRandomString(Constants.LENGTH_10);
        fileInfo.setFileId(newFileId);
        copyFileList.add(fileInfo);
        if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
            FileInfoQuery query = new FileInfoQuery();
            query.setFilePid(sourceFileId);
            query.setUserId(sourceUserId);
            List<FileInfo> sourceFileList = this.fileInfoMapper.selectList(query);
            for (FileInfo item : sourceFileList) {
                findAllSubFile(copyFileList, item, sourceUserId, currentUserId, curDate, newFileId);
            }
        }
    }

    @Override
    public SessionShareDto checkShare(HttpSession session, String shareId) {
        SessionShareDto shareSessionDto = SessionUtil.getSessionShareFromSession(session, shareId);
        if (shareSessionDto == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_903);
        }
        if (shareSessionDto.getExpireTime() != null && new Date().after(shareSessionDto.getExpireTime())) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        return shareSessionDto;
    }

    private ShareInfoVO getShareInfoCommon(String shareId) {
        FileShare share = fileShareMapper.selectByShareId(shareId);
        if (null == share || (share.getExpireTime() != null && new Date().after(share.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902.getMsg());
        }

        FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(share.getFileId(), share.getUserId());
        if (fileInfo == null || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())) {
            throw new BusinessException(ResponseCodeEnum.CODE_902.getMsg());
        }

        UserInfo userInfo = userInfoMapper.selectByUserId(share.getUserId());
        ShareInfoVO shareInfoVO = CopyTools.copy(share, ShareInfoVO.class);
        shareInfoVO.setFileName(fileInfo.getFileName());
        shareInfoVO.setNickName(userInfo.getNickName());
        shareInfoVO.setAvatar(userInfo.getQqAvatar());
        shareInfoVO.setUserId(userInfo.getUserId());

        return shareInfoVO;
    }
}