package com.thepan.controller;

import com.thepan.annotation.GlobalInterceptor;
import com.thepan.entity.enums.FileCategoryEnums;
import com.thepan.entity.enums.FileDelFlagEnums;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.vo.file.FileInfoVO;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.response.ResponseVO;
import com.thepan.service.FileInfoService;
import com.thepan.utils.ResponseUtil;
import com.thepan.utils.SessionUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * 文件信息 Controller
 */
@RestController
@RequestMapping("/file")
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
}