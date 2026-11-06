package com.huohuo.scenicaiguide.system.security;

import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.service.UserPermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 权限校验切面
 * <p>
 * 拦截标注了鉴权注解的方法/类，在方法执行前进行权限校验
 * <p>
 * 优先级规则：方法级注解覆盖类级注解
 */
@Aspect
@Component
public class AuthAspect {

    private final UserPermissionService userPermissionService;

    public AuthAspect(UserPermissionService userPermissionService) {
        this.userPermissionService = userPermissionService;
    }

    @Pointcut("@within(com.huohuo.scenicaiguide.system.security.RequireAuth) " +
              "|| @annotation(com.huohuo.scenicaiguide.system.security.RequireAuth) " +
              "|| @within(com.huohuo.scenicaiguide.system.security.RequireAdmin) " +
              "|| @annotation(com.huohuo.scenicaiguide.system.security.RequireAdmin) " +
              "|| @within(com.huohuo.scenicaiguide.system.security.RequireRoot) " +
              "|| @annotation(com.huohuo.scenicaiguide.system.security.RequireRoot) " +
              "|| @within(com.huohuo.scenicaiguide.system.security.RequireAuthenticated) " +
              "|| @annotation(com.huohuo.scenicaiguide.system.security.RequireAuthenticated) " +
              "|| @within(com.huohuo.scenicaiguide.system.security.RequireFence) " +
              "|| @annotation(com.huohuo.scenicaiguide.system.security.RequireFence) " +
              "|| @within(com.huohuo.scenicaiguide.system.security.RequirePermission) " +
              "|| @annotation(com.huohuo.scenicaiguide.system.security.RequirePermission)")
    public void authPointcut() {
    }

    @Around("authPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = point.getTarget().getClass();

        // 方法级有任意鉴权注解时，忽略类级注解
        boolean anyAnnotationOnMethod = hasAnyAnnotation(method);

        // 校验管理员权限
        if (resolveRequireAdmin(method, targetClass, anyAnnotationOnMethod)) {
            if (!"ADMIN".equals(principal.role())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        // 校验 Root 权限
        if (resolveRequireRoot(method, targetClass, anyAnnotationOnMethod)) {
            if (!"ADMIN".equals(principal.role()) || !Boolean.TRUE.equals(principal.isRoot())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        // 校验已认证游客（L1 及以上）
        if (resolveRequireAuthenticated(method, targetClass, anyAnnotationOnMethod)) {
            if (!"TOURIST".equals(principal.role())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            String level = principal.authLevel();
            if (!"AUTHENTICATED".equals(level) && !"FENCE".equals(level)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        // 校验在园游客（L2）
        if (resolveRequireFence(method, targetClass, anyAnnotationOnMethod)) {
            if (!"TOURIST".equals(principal.role()) || !"FENCE".equals(principal.authLevel())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        // 校验细粒度权限码
        RequirePermission requirePermission = resolveRequirePermission(method, targetClass);
        if (requirePermission != null) {
            checkPermission(principal, requirePermission);
        }

        return point.proceed();
    }

    /**
     * 权限码校验：仅对管理员生效，Root 直接放行
     * <p>
     * 显式复核 ADMIN 角色是必要的：权限集按 userId 查询，而游客与管理员的 userId 取自不同的表，
     * 数字可能撞车，不复核角色就等于把管理员的权限借给了同号游客
     */
    private void checkPermission(AuthPrincipal principal, RequirePermission annotation) {
        if (!"ADMIN".equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (Boolean.TRUE.equals(principal.isRoot())) {
            return;
        }
        if (principal.userId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<String> required = Arrays.stream(annotation.value())
            .map(PermissionCode::getCode)
            .toList();
        if (!userPermissionService.hasAnyPermission(principal.userId(), required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    // ========== 注解解析（方法级优先） ==========

    /**
     * 角色门槛类注解，方法级出现任意一个就整体覆盖类级
     * <p>
     * {@link RequirePermission} 不在此列：它是叠加在角色门槛之上的附加校验，
     * 若把它算进来，类上的 {@code @RequireAdmin} 会被方法上的权限码注解顶掉，反而降低了门槛
     */
    private boolean hasAnyAnnotation(Method method) {
        return method.isAnnotationPresent(RequireAuth.class)
            || method.isAnnotationPresent(RequireAdmin.class)
            || method.isAnnotationPresent(RequireRoot.class)
            || method.isAnnotationPresent(RequireAuthenticated.class)
            || method.isAnnotationPresent(RequireFence.class);
    }

    private RequirePermission resolveRequirePermission(Method method, Class<?> clazz) {
        RequirePermission onMethod = method.getAnnotation(RequirePermission.class);
        return onMethod != null ? onMethod : clazz.getAnnotation(RequirePermission.class);
    }

    private boolean resolveRequireAdmin(Method method, Class<?> clazz, boolean anyOnMethod) {
        if (anyOnMethod) {
            return method.isAnnotationPresent(RequireAdmin.class);
        }
        return clazz.isAnnotationPresent(RequireAdmin.class);
    }

    private boolean resolveRequireRoot(Method method, Class<?> clazz, boolean anyOnMethod) {
        if (anyOnMethod) {
            return method.isAnnotationPresent(RequireRoot.class);
        }
        return clazz.isAnnotationPresent(RequireRoot.class);
    }

    private boolean resolveRequireAuthenticated(Method method, Class<?> clazz, boolean anyOnMethod) {
        if (anyOnMethod) {
            return method.isAnnotationPresent(RequireAuthenticated.class);
        }
        return clazz.isAnnotationPresent(RequireAuthenticated.class);
    }

    private boolean resolveRequireFence(Method method, Class<?> clazz, boolean anyOnMethod) {
        if (anyOnMethod) {
            return method.isAnnotationPresent(RequireFence.class);
        }
        return clazz.isAnnotationPresent(RequireFence.class);
    }
}
