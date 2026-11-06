package com.huohuo.scenicaiguide.system.security;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.service.UserPermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 权限码切面判定测试
 * <p>
 * 纯逻辑测试，不起 Spring 容器：直接构造 {@link ProceedingJoinPoint} 与 SecurityContext，
 * 验证 {@link RequirePermission} 的四类判定——root 直通、命中放行、未命中拒绝、
 * 以及它与 {@link RequireAdmin} 的叠加关系
 */
class AuthAspectPermissionTest {

    private final UserPermissionService userPermissionService = mock(UserPermissionService.class);
    private final AuthAspect aspect = new AuthAspect(userPermissionService);

    private final AdminFixture adminFixture = new AdminFixture();
    private final LooseFixture looseFixture = new LooseFixture();
    private final ClassScopedFixture classScopedFixture = new ClassScopedFixture();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ========== 被切目标 ==========

    /**
     * 常规后台接口：类级管理员门禁 + 方法级权限码
     */
    @RequireAdmin
    static class AdminFixture {

        @RequirePermission(PermissionCode.SCENIC_READ)
        public String read() {
            return "read";
        }

        @RequirePermission({PermissionCode.QA_READ, PermissionCode.QA_WRITE})
        public String anyOf() {
            return "anyOf";
        }

        public String withoutPermissionCode() {
            return "withoutPermissionCode";
        }
    }

    /**
     * 漏标 {@link RequireAdmin} 的接口：只有类级 {@link RequireAuth} 与方法级权限码，
     * 用于验证切面自身的 ADMIN 复核是否兜得住
     */
    @RequireAuth
    static class LooseFixture {

        @RequirePermission(PermissionCode.SCENIC_READ)
        public String read() {
            return "read";
        }
    }

    /**
     * 权限码标在类上，方法不再重复标注
     */
    @RequireAdmin
    @RequirePermission(PermissionCode.ANALYTICS_READ)
    static class ClassScopedFixture {

        public String read() {
            return "read";
        }
    }

    // ========== Root 直通 ==========

    @Test
    void rootBypassesPermissionLookupEntirely() throws Throwable {
        authenticateAdmin("admin_1", true, 1L);

        assertThat(invoke(AdminFixture.class, adminFixture, "read")).isEqualTo("proceeded");
        // root 不查权限集：既省一次缓存读，也保证任何授权配置错误都锁不死超级管理员
        verifyNoInteractions(userPermissionService);
    }

    // ========== 普通管理员 ==========

    @Test
    void adminWithRequiredPermissionPasses() throws Throwable {
        authenticateAdmin("admin_9", false, 9L);
        when(userPermissionService.hasAnyPermission(eq(9L), anyList())).thenReturn(true);

        assertThat(invoke(AdminFixture.class, adminFixture, "read")).isEqualTo("proceeded");
        verify(userPermissionService).hasAnyPermission(9L, List.of("scenic:read"));
    }

    @Test
    void adminWithoutRequiredPermissionIsForbidden() {
        authenticateAdmin("admin_9", false, 9L);
        when(userPermissionService.hasAnyPermission(anyLong(), anyList())).thenReturn(false);

        assertThatThrownBy(() -> invoke(AdminFixture.class, adminFixture, "read"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void multiplePermissionCodesAreCheckedAsAnyOf() throws Throwable {
        authenticateAdmin("admin_9", false, 9L);
        when(userPermissionService.hasAnyPermission(anyLong(), anyList())).thenReturn(true);

        invoke(AdminFixture.class, adminFixture, "anyOf");

        // 多个权限码是"任一满足"，读接口因此能同时接受 read 和 write
        verify(userPermissionService).hasAnyPermission(9L, List.of("qa:read", "qa:write"));
    }

    @Test
    void adminWithoutUserIdIsForbidden() {
        // 老 token 或异常 subject 解析不出 userId，此时无从判定权限，只能拒绝
        authenticateAdmin("admin", false, null);

        assertThatThrownBy(() -> invoke(AdminFixture.class, adminFixture, "read"))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(userPermissionService);
    }

    @Test
    void methodWithoutPermissionCodeOnlyNeedsAdmin() throws Throwable {
        authenticateAdmin("admin_9", false, 9L);

        assertThat(invoke(AdminFixture.class, adminFixture, "withoutPermissionCode")).isEqualTo("proceeded");
        verifyNoInteractions(userPermissionService);
    }

    @Test
    void classLevelPermissionCodeAppliesWhenMethodHasNone() throws Throwable {
        authenticateAdmin("admin_9", false, 9L);
        when(userPermissionService.hasAnyPermission(anyLong(), anyList())).thenReturn(true);

        invoke(ClassScopedFixture.class, classScopedFixture, "read");

        verify(userPermissionService).hasAnyPermission(9L, List.of("analytics:read"));
    }

    // ========== 与角色门槛的叠加关系 ==========

    @Test
    void permissionCodeDoesNotOverrideClassLevelRequireAdmin() {
        // 方法上只有 @RequirePermission，类上的 @RequireAdmin 必须继续生效，否则门槛不升反降
        authenticateTourist("user_9", null, 9L);
        when(userPermissionService.hasAnyPermission(anyLong(), anyList())).thenReturn(true);

        assertThatThrownBy(() -> invoke(AdminFixture.class, adminFixture, "read"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void touristWithCollidingUserIdCannotBorrowAdminPermission() {
        // 游客 user_9 与管理员 admin_9 解析出同一个 userId=9，接口漏标 @RequireAdmin 时，
        // 切面内的 ADMIN 复核是最后一道防线：即便权限集查询会放行，也必须拒绝
        authenticateTourist("user_9", "AUTHENTICATED", 9L);
        when(userPermissionService.hasAnyPermission(anyLong(), anyList())).thenReturn(true);

        assertThatThrownBy(() -> invoke(LooseFixture.class, looseFixture, "read"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("FORBIDDEN"));
    }

    // ========== 未认证 ==========

    @Test
    void missingAuthenticationIsUnauthorized() {
        assertThatThrownBy(() -> invoke(AdminFixture.class, adminFixture, "read"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("UNAUTHORIZED"));
    }

    // ========== 辅助方法 ==========

    private void authenticateAdmin(String subject, boolean isRoot, Long userId) {
        authenticate(new AuthPrincipal(subject, "ADMIN", null, isRoot, userId));
    }

    private void authenticateTourist(String subject, String authLevel, Long userId) {
        authenticate(new AuthPrincipal(subject, "TOURIST", authLevel, null, userId));
    }

    private void authenticate(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))));
    }

    private Object invoke(Class<?> targetClass, Object target, String methodName) throws Throwable {
        Method method = targetClass.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(target);
        when(point.proceed()).thenReturn("proceeded");
        return aspect.around(point);
    }
}
