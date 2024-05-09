package com.thepan.controller;


import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.entity.query.FileInfoQuery;
import com.thepan.entity.vo.file.FileInfoVO;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.response.ResponseVO;
import com.thepan.service.FileInfoService;
import com.thepan.utils.ResponseUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController("recycleController")
@RequestMapping("/recycle")
public class RecycleController {

    @Resource
    private FileInfoService fileInfoService;

    /**
     * 根据条件分页查询
     */
    @RequestMapping("/loadRecycleList")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO loadRecycleList(HttpSession session, Integer pageNo, Integer pageSize) {
        PaginationResultVO result = fileInfoService.loadRecycleList(session, pageNo, pageSize);
        return ResponseUtil.getSuccessResponseVO(ResponseUtil.convert2PaginationVO(result, FileInfoVO.class));
    }

    @RequestMapping("/recoverFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO recoverFile(HttpSession session, @VerifyParam(required = true) String fileIds) {
        fileInfoService.recoverFile(session, fileIds);
        return ResponseUtil.getSuccessResponseVO(null);
    }

    @RequestMapping("/delFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO delFile(HttpSession session, @VerifyParam(required = true) String fileIds) {
        fileInfoService.delFile(session, fileIds);
        return ResponseUtil.getSuccessResponseVO(null);
    }
}
