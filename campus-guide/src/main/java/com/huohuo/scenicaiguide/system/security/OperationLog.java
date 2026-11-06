package com.huohuo.scenicaiguide.system.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志标注
 * <p>
 * <b>可选注解</b>。后台写操作由 {@link OperationLogAspect} 自动记录，不标也不会漏；
 * 本注解只负责把 {@code POST /api/v1/admin/roles} 这种机器味的记录换成"角色权限 / 新增角色"
 * <p>
 * 另有两个用途：
 * <ul>
 *   <li>把 admin 前缀之外的关键动作（如管理员登录）显式纳入审计</li>
 *   <li>用 {@link #ignore()} 排除高频无审计价值的写接口</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 业务模块中文名，标在类上可被同类方法共用
     */
    String module() default "";

    /**
     * 操作动作中文名，留空时从方法名推导
     */
    String action() default "";

    /**
     * 不记录请求参数，用于参数体积大或含敏感内容的接口
     */
    boolean withoutParams() default false;

    /**
     * 显式排除，标注后该接口不进审计
     */
    boolean ignore() default false;
}
