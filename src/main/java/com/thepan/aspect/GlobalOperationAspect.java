package com.thepan.aspect;

import com.thepan.annotation.GlobalInterceptor;
import com.thepan.annotation.VerifyParam;
import com.thepan.constants.Constants;
import com.thepan.entity.dao.UserInfo;
import com.thepan.entity.dto.SessionWebUserDto;
import com.thepan.entity.enums.ResponseCodeEnum;
import com.thepan.entity.query.UserInfoQuery;
import com.thepan.exception.BusinessException;
import com.thepan.service.UserInfoService;
import com.thepan.utils.SessionUtil;
import com.thepan.utils.StringTools;
import com.thepan.utils.VerifyUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

/**
 * AOP全局拦截器的实现，用于在方法执行前进行统一的操作，比如参数校验和登录状态检查
 */
@Component("operationAspect")
@Aspect
@Slf4j
public class GlobalOperationAspect {

    private static final String TYPE_STRING = "java.lang.String";
    private static final String TYPE_INTEGER = "java.lang.Integer";
    private static final String TYPE_LONG = "java.lang.Long";

    @Resource
    private UserInfoService userInfoService;


    /**
     * 捕获所有被GlobalInterceptor注解的方法，并添加前置通知
     */
    @Pointcut("@annotation(com.thepan.annotation.GlobalInterceptor)")
    private void requestInterceptor() {
    }

    @Before("requestInterceptor()")
    public void interceptorDo(JoinPoint point) throws BusinessException {
        try {
            Object target = point.getTarget();
            Object[] arguments = point.getArgs();
            String methodName = point.getSignature().getName();
            Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
            if (null == interceptor) {
                return;
            }
            /**
             * 校验登录
             */
            if (interceptor.checkLogin() || interceptor.checkAdmin()) {
                checkLogin(interceptor.checkAdmin());
            }
            /**
             * 校验参数
             */
            if (interceptor.checkParams()) {
                validateParams(method, arguments);
            }
        } catch (BusinessException e) {
            log.error("全局拦截器异常", e);
            throw e;
        } catch (Exception e) {
            log.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        } catch (Throwable e) {
            log.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }


    //校验登录
    private void checkLogin(Boolean checkAdmin) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        SessionWebUserDto sessionUser = SessionUtil.getUserInfoFromSession(session);
        if (sessionUser == null) {
            List<UserInfo> userInfoList = userInfoService.findListByParam(new UserInfoQuery());
            if (!userInfoList.isEmpty()) {
                // 默认第一个是管理员
                UserInfo userInfo = userInfoList.get(0);
                sessionUser = new SessionWebUserDto();
                sessionUser.setUserId(userInfo.getUserId());
                sessionUser.setNickName(userInfo.getNickName());
                sessionUser.setAdmin(true);
                session.setAttribute(Constants.SESSION_KEY, sessionUser);
            }
        }
        // 此时sessionUser还是null，那就是没登陆
        if (null == sessionUser) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        // 普通用户
        if (checkAdmin && !sessionUser.getAdmin()) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }


    private void validateParams(Method m, Object[] arguments) throws BusinessException {
        Parameter[] parameters = m.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i]; //得到的是当前参数对象
            Object value = arguments[i];    //获取当前参数值
            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);
            if (verifyParam == null) {
                continue;
            }
            //基本数据类型
            if (TYPE_STRING.equals(parameter.getParameterizedType().getTypeName()) || TYPE_LONG.equals(parameter.getParameterizedType().getTypeName()) || TYPE_INTEGER.equals(parameter.getParameterizedType().getTypeName())) {
                checkValue(value, verifyParam);
            } else {
                //如果传递的是对象
                checkObjValue(parameter, value);
            }
        }
    }

    private void checkObjValue(Parameter parameter, Object value) {
        try {
            // 获取参数类型
            String typeName = parameter.getParameterizedType().getTypeName();
            //获取对应的Class对象
            Class classz = Class.forName(typeName);
            //获取该类下面所有字段
            Field[] fields = classz.getDeclaredFields();
            //遍历字段
            for (Field field : fields) {
                // 检查是否有@Verify注解
                VerifyParam fieldVerifyParam = field.getAnnotation(VerifyParam.class);
                if (fieldVerifyParam == null) {
                    continue;
                }
                // 设置字段可访问
                field.setAccessible(true);
                //字段值
                Object resultValue = field.get(value);
                // 校验值
                checkValue(resultValue, fieldVerifyParam);
            }
        } catch (BusinessException e) {
            log.error("校验参数失败", e);
            throw e;
        } catch (Exception e) {
            log.error("校验参数失败", e);
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    /**
     * 校验参数
     *
     * @param value
     * @param verifyParam
     * @throws BusinessException
     */
    private void checkValue(Object value, VerifyParam verifyParam) throws BusinessException {
        Boolean isEmpty = value == null || StringTools.isEmpty(value.toString());
        Integer length = value == null ? 0 : value.toString().length();

        /**
         * 校验空
         */
        if (isEmpty && verifyParam.required()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        /**
         * 校验长度
         */
        if (!isEmpty && (verifyParam.max() != -1 && verifyParam.max() < length || verifyParam.min() != -1 && verifyParam.min() > length)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        /**
         * 校验正则
         */
        if (!isEmpty && !StringTools.isEmpty(verifyParam.regex().getRegex()) && !VerifyUtils.verify(verifyParam.regex(), String.valueOf(value))) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }
}
