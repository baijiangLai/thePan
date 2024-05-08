package com.thepan.service.impl;


import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.enums.PageSize;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.query.SimplePage;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.mappers.FileInfoMapper;
import com.thepan.service.FileInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 文件信息 业务接口实现
 */
@Service
@Slf4j
public class FileInfoServiceImpl implements FileInfoService {

    @Resource
    private FileInfoMapper fileInfoMapper;

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
}