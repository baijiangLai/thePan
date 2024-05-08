package com.thepan.utils;

import com.thepan.entity.enums.ResponseCodeEnum;
import com.thepan.entity.vo.file.PaginationResultVO;
import com.thepan.entity.vo.response.ResponseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseUtil {
    private static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);

    protected static final String STATUC_SUCCESS = "success";

    protected static final String STATUC_ERROR = "error";

    public static <T> ResponseVO getSuccessResponseVO(T t) {
        ResponseVO<T> responseVO = new ResponseVO<>();
        responseVO.setStatus(STATUC_SUCCESS);
        responseVO.setCode(ResponseCodeEnum.CODE_200.getCode());
        responseVO.setInfo(ResponseCodeEnum.CODE_200.getMsg());
        responseVO.setData(t);
        return responseVO;
    }

    public static PaginationResultVO convert2PaginationVO(PaginationResultVO result, Class classz) {
        PaginationResultVO resultVO = new PaginationResultVO();
        resultVO.setList(CopyTools.copyList(result.getList(), classz));
        resultVO.setPageNo(result.getPageNo());
        resultVO.setPageSize(result.getPageSize());
        resultVO.setPageTotal(result.getPageTotal());
        resultVO.setTotalCount(result.getTotalCount());
        return resultVO;
    }
}
