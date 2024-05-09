package com.thepan.controller;

import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.entity.dao.FileShare;
import com.thepan.entity.query.FileShareQuery;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.response.ResponseVO;
import com.thepan.service.FileShareService;
import com.thepan.utils.ResponseUtil;
import com.thepan.utils.SessionUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController("shareController")
@RequestMapping("/share")
public class ShareController {
    @Resource
    private FileShareService fileShareService;


    @RequestMapping("/loadShareList")
    @GlobalInterceptor
    public ResponseVO loadShareList(HttpSession session, FileShareQuery query) {
        PaginationResultVO resultVO = fileShareService.loadShareList(session, query);
        return ResponseUtil.getSuccessResponseVO(resultVO);
    }

    @RequestMapping("/shareFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO shareFile(HttpSession session,
                                @VerifyParam(required = true) String fileId,
                                @VerifyParam(required = true) Integer validType,
                                String code) {
        FileShare shareFile = fileShareService.shareFile(session, fileId, validType, code);
        return ResponseUtil.getSuccessResponseVO(shareFile);
    }

    @RequestMapping("/cancelShare")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO cancelShare(HttpSession session, @VerifyParam(required = true) String shareIds) {
        String userId = SessionUtil.getUserInfoFromSession(session).getUserId();
        fileShareService.deleteFileShareBatch(shareIds, userId);
        return ResponseUtil.getSuccessResponseVO(null);
    }
}
