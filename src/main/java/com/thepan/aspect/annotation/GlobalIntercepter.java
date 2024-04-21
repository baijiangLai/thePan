package com.thepan.aspect.annotation;

import java.lang.annotation.*;

/**
 *  用该注解配合AOP对方法执行前进行拦截
 */
@Retention(RetentionPolicy.RUNTIME) // 指定注解在运行时可见
@Target(ElementType.METHOD) // 指定注解可以用于方法
@Documented // 表示该注解应该被 javadoc 工具记录
@Inherited // 表示该注解可以被子类继承
public @interface GlobalIntercepter {
    /**
     * 校验参数
     * @return
     */
    boolean checkParams() default false;
}
