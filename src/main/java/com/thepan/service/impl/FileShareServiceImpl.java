package com.thepan.service.impl;


import com.thepan.entity.dao.FileShare;
import com.thepan.entity.enums.PageSize;
import com.thepan.entity.query.FileShareQuery;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.mappers.FileShareMapper;
import com.thepan.service.FileShareService;
import com.thepan.utils.SessionUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
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
}