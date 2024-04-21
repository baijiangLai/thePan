package com.thepan.aspect;

import com.thepan.aspect.annotation.GlobalIntercepter;
import com.thepan.aspect.annotation.VerifyParam;
import com.thepan.enums.ResponseCodeEnum;
import com.thepan.exception.BusinessException;
import com.thepan.utils.StringTools;
import com.thepan.utils.VerifyUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * AOP全局拦截器的实现，用于在方法执行前进行统一的操作，比如参数校验和登录状态检查
 */
@Aspect
@Component("globalOperationAspect")
public class GlobalOperationAspect {

    private static Logger logger = LoggerFactory.getLogger(GlobalOperationAspect.class);
    private static final String TYPE_STRING = "java.lang.String";
    private static final String TYPE_INTEGER = "java.lang.Integer";
    private static final String TYPE_LONG = "java.lang.Long";

    /**
     * 将注解@GlobalIntercepter作为切入点
     */
    @Pointcut("@annotation(com.thepan.aspect.annotation.GlobalIntercepter)")
    private void pointCut(){

    }

    @Before("pointCut()")
    private void globalPointCut(JoinPoint joinPoint) throws NoSuchMethodException {


        // 1. 获取目标方法名
        String methodName = joinPoint.getSignature().getName();

        // 2. 获取目标方法对象
        Object target = joinPoint.getTarget();
        Method method = target.getClass().getMethod(methodName, ((MethodSignature) joinPoint.getSignature()).getParameterTypes());

        // 3. 获取方法参数
        Object[] args = joinPoint.getArgs();

        // 4. 获取方法上的注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        GlobalIntercepter annotation = method.getAnnotation(GlobalIntercepter.class);

        // 5. 获取注解的参数
//        String value = annotation.value();


        if (null == annotation) {
            return;
        }

        /**
         * 校验参数
         */
        if (annotation.checkParams()) {
            validateParams(method, args);
        }
    }

    private void validateParams(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters(); // 获取形参
        // 通过形参取出注解
        for (int i = 0; i<parameters.length; i++){
            Parameter parameter = parameters[i];

            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);
            // 没有该注解时不做参数校验
            if (verifyParam == null) {
                continue;
            }
            Object arg = args[i];
            Type parameterizedType = parameter.getParameterizedType();

            // 如果传参为基本数据类型，则直接根据注解里的信息进行校验。 若传参非基本数据类型，则再将该对象中的参数挨个取出分别校验
            if (parameter.getParameterizedType().getTypeName().equals(TYPE_INTEGER)
                    ||parameter.getParameterizedType().getTypeName().equals(TYPE_LONG)
                    ||parameter.getParameterizedType().getTypeName().equals(TYPE_STRING)){
                checkValue1(arg,verifyParam);
            }else {
                checkValue2(parameter,arg);
            }
        }
    }

    /**
     *  校验基本数据类型
     * @param value
     * @param verifyParam
     */
    private void checkValue1(Object value, VerifyParam verifyParam) {
        Boolean isEmpty = value == null || StringTools.isEmpty(value.toString());
        Integer length = value == null ? 0 : value.toString().length();

        /**
         * 校验参数必填
         */
        if (isEmpty&&verifyParam.required()){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        /**
         * 校验参数长度
         */
        if (!isEmpty&&(verifyParam.max()!=-1&&length>verifyParam.max()||(verifyParam.min()!=-1&&length<verifyParam.min()))){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        /**
         * 校验正则表达式
         */
        if (!isEmpty&& !StringTools.isEmpty(verifyParam.regex().getRegex())){
            boolean verify = VerifyUtils.verify(verifyParam.regex().getRegex(), value.toString());
            if (!verify){
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }


    }

    /**
     * 校验非基本数据类型
     * @param parameter
     * @param arg
     */
    private void checkValue2(Parameter parameter, Object arg) {
        try {
            String typeName = parameter.getParameterizedType().getTypeName();
            Class classz = Class.forName(typeName);
            Field[] fields = classz.getDeclaredFields();
            for (Field field : fields) {
                VerifyParam fieldVerifyParam = field.getAnnotation(VerifyParam.class);
                if (fieldVerifyParam == null) {
                    continue;
                }
                field.setAccessible(true);
                Object resultValue = field.get(arg);
                checkValue1((Parameter) resultValue, fieldVerifyParam);
            }
        } catch (BusinessException e) {
            logger.error("校验参数失败", e);
            throw e;
        } catch (Exception e) {
            logger.error("校验参数失败", e);
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }




}
