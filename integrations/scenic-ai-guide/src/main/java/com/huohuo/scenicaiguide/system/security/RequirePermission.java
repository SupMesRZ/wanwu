package com.huohuo.scenicaiguide.system.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求持有指定权限之一
 * <p>
 * 叠加在 {@link RequireAdmin} 之上做细粒度控制：先过管理员门禁，再校验权限码。
 * 列多个权限码时任一满足即放行（例如读接口同时接受 read 和 write）
 * <p>
 * Root 账号直通，不受权限勾选影响
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    PermissionCode[] value();
}
