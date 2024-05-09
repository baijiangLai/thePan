package com.thepan.service.impl;


import com.thepan.constants.Constants;
import com.thepan.entity.dao.FileShare;
import com.thepan.entity.enums.PageSize;
import com.thepan.entity.enums.ResponseCodeEnum;
import com.thepan.entity.enums.ShareValidTypeEnums;
import com.thepan.entity.query.FileShareQuery;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.exception.BusinessException;
import com.thepan.mappers.FileShareMapper;
import com.thepan.service.FileShareService;
import com.thepan.utils.DateUtil;
import com.thepan.utils.SessionUtil;
import com.thepan.utils.StringTools;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;

/**
 * 分享信息 业务接口实现
 */
@Service("fileShareService")
public class FileShareServiceImpl implements FileShareService {
    @Resource
    private FileShareMapper fileShareMapper;

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
        PaginationResultVO<FileShare> resultVO = findListByPage(query);
        return resultVO;
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
}