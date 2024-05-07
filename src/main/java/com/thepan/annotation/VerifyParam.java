package com.thepan.annotation;
import com.thepan.enums.VerifyRegexEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 该注解与用于配合AOP完成参数校验
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface VerifyParam {


    /**
     * 最小长度
     * @return
     */
    int min() default -1;

    /**
     * 最大长度
     * @return
     */
    int max() default -1;

    /**
     * 是否必输
     * @return
     */
    boolean required() default false;

    /**
     * 校验正则
     * @return
     */
    VerifyRegexEnum regex() default VerifyRegexEnum.NO;
}
