package com.thepan.controller;


import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.entity.dao.FileInfo;
import com.thepan.entity.dto.SessionShareDto;
import com.thepan.entity.vo.file.FileInfoVO;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.file.ShareInfoVO;
import com.thepan.entity.vo.response.ResponseVO;
import com.thepan.service.FileInfoService;
import com.thepan.service.FileShareService;
import com.thepan.utils.FileGetUtil;
import com.thepan.utils.ResponseUtil;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping("/showShare")
public class WebShareController {

    @Resource
    private FileShareService fileShareService;

    @Resource
    private FileInfoService fileInfoService;


    /**
     * 获取分享登录信息
     *
     * @param session
     * @param shareId
     * @return
     */
    @RequestMapping("/getShareLoginInfo")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO getShareLoginInfo(HttpSession session, @VerifyParam(required = true) String shareId) {
        ShareInfoVO shareInfoVO = fileShareService.getShareLoginInfo(session, shareId);
        return ResponseUtil.getSuccessResponseVO(shareInfoVO);
    }

    /**
     * 获取分享信息
     *
     * @param shareId
     * @return
     */
    @RequestMapping("/getShareInfo")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO getShareInfo(@VerifyParam(required = true) String shareId) {
        ShareInfoVO shareInfoVO = fileShareService.getShareInfo(shareId);
        return ResponseUtil.getSuccessResponseVO(shareInfoVO);
    }

    /**
     * 校验分享码
     *
     * @param session
     * @param shareId
     * @param code
     * @return
     */
    @RequestMapping("/checkShareCode")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO checkShareCode(HttpSession session,
                                     @VerifyParam(required = true) String shareId,
                                     @VerifyParam(required = true) String code) {
        fileShareService.checkShareCode(session,shareId, code);
        return ResponseUtil.getSuccessResponseVO(null);
    }

    /**
     * 获取文件列表
     *
     * @param session
     * @param shareId
     * @return
     */
    @RequestMapping("/loadFileList")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO loadFileList(HttpSession session,
                                   @VerifyParam(required = true) String shareId, String filePid) {
        PaginationResultVO resultVO = fileShareService.loadFileList(session, shareId, filePid);
        return ResponseUtil.getSuccessResponseVO(ResponseUtil.convert2PaginationVO(resultVO, FileInfoVO.class));
    }



    /**
     * 获取目录信息
     *
     * @param session
     * @param shareId
     * @param path
     * @return
     */
    @RequestMapping("/getFolderInfo")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO getFolderInfo(HttpSession session,
                                    @VerifyParam(required = true) String shareId,
                                    @VerifyParam(required = true) String path) {
        List<FileInfo> folderInfoList = fileShareService.getFolderInfo(session, shareId, path);
        return ResponseUtil.getSuccessResponseVO(folderInfoList);
    }

    @RequestMapping("/getFile/{shareId}/{fileId}")
    public void getFile(HttpServletResponse response, HttpSession session,
                        @PathVariable("shareId") @VerifyParam(required = true) String shareId,
                        @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        SessionShareDto shareSessionDto = fileShareService.checkShare(session, shareId);
        FileGetUtil fileGetUtil = new FileGetUtil(fileInfoService);
        fileGetUtil.getFile(response, fileId, shareSessionDto.getShareUserId());
    }

    @RequestMapping("/ts/getVideoInfo/{shareId}/{fileId}")
    public void getVideoInfo(HttpServletResponse response,
                             HttpSession session,
                             @PathVariable("shareId") @VerifyParam(required = true) String shareId,
                             @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        SessionShareDto shareSessionDto = fileShareService.checkShare(session, shareId);
        FileGetUtil fileGetUtil = new FileGetUtil(fileInfoService);
        fileGetUtil.getFile(response, fileId, shareSessionDto.getShareUserId());
    }

    @RequestMapping("/createDownloadUrl/{shareId}/{fileId}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO createDownloadUrl(HttpSession session,
                                        @PathVariable("shareId") @VerifyParam(required = true) String shareId,
                                        @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        SessionShareDto shareSessionDto = fileShareService.checkShare(session, shareId);
        String downloadUrl = fileInfoService.createDownloadUrl(fileId, shareSessionDto.getShareUserId());
        return ResponseUtil.getSuccessResponseVO(downloadUrl);
    }

    /**
     * 下载
     *
     * @param request
     * @param response
     * @throws Exception
     */
    @RequestMapping("/download/{code}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public void download(HttpServletRequest request, HttpServletResponse response,
                         @PathVariable("code") @VerifyParam(required = true) String code) throws Exception {
        fileInfoService.download(request, response, code);
    }

    /**
     * 保存分享
     *
     * @param session
     * @param shareId
     * @param shareFileIds
     * @param myFolderId
     * @return
     */
    @RequestMapping("/saveShare")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO saveShare(HttpSession session,
                                @VerifyParam(required = true) String shareId,
                                @VerifyParam(required = true) String shareFileIds,
                                @VerifyParam(required = true) String myFolderId) {
        fileShareService.saveShare(session, shareId, shareFileIds, myFolderId);
        return ResponseUtil.getSuccessResponseVO(null);
    }
}
