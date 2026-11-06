package com.huohuo.scenicaiguide.system.security;

import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.entity.SysOperationLog;
import com.huohuo.scenicaiguide.system.service.OperationLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志切面测试
 * <p>
 * 纯逻辑离线测试，自造 {@link ProceedingJoinPoint} 与请求上下文。
 * 重点钉住"自动兜底 + 注解增强"这一取舍的三条边界：后台写接口一律记、
 * 非后台接口不记（除非显式标注）、失败也要记且不吞异常
 */
class OperationLogAspectTest {

    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final OperationLogAspect aspect = new OperationLogAspect(operationLogService);

    private final AdminFixture adminFixture = new AdminFixture();
    private final PublicFixture publicFixture = new PublicFixture();
    private final PlainFixture plainFixture = new PlainFixture();

    @BeforeEach
    void enableLogging() {
        when(operationLogService.enabled()).thenReturn(true);
        when(operationLogService.resolveUsername(anyLong(), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(operationLogService.sanitizeParams(any())).thenReturn("[]");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ========== 被切目标 ==========

    @Tag(name = "角色权限")
    static class AdminFixture {

        @PostMapping
        public String createRole() {
            return "created";
        }

        @PostMapping
        public String assign() {
            return "assigned";
        }

        @PostMapping
        @OperationLog(module = "自定义模块", action = "自定义动作", withoutParams = true)
        public String annotated() {
            return "annotated";
        }

        @PostMapping
        @OperationLog(ignore = true)
        public String ignored() {
            return "ignored";
        }
    }

    /**
     * 游客端接口：URI 不在 {@code /api/v1/admin/} 下，默认不该进审计
     */
    @Tag(name = "游客导览")
    static class PublicFixture {

        @PostMapping
        public String sendMessage() {
            return "sent";
        }

        @PostMapping
        @OperationLog(module = "认证", action = "管理员登录")
        public String adminLogin() {
            return "logged-in";
        }
    }

    /**
     * 没有 Swagger {@code @Tag} 的类，用于验证模块名的最后一级兜底
     */
    static class PlainFixture {

        @PostMapping
        public String updateSomething() {
            return "updated";
        }
    }

    // ========== 记录范围 ==========

    @Test
    void adminWriteEndpointIsRecordedWithoutAnnotation() throws Throwable {
        bindRequest("POST", "/api/v1/admin/roles");
        authenticateAdmin(3L, "admin_3");

        assertThat(invoke(AdminFixture.class, adminFixture, "createRole")).isEqualTo("proceeded");

        SysOperationLog log = captureRecorded();
        // 审计完整性由结构保证：新接口忘了标注也照样进日志
        assertThat(log.getModule()).isEqualTo("角色权限");
        assertThat(log.getAction()).isEqualTo("新增");
        assertThat(log.getHttpMethod()).isEqualTo("POST");
        assertThat(log.getUri()).isEqualTo("/api/v1/admin/roles");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getUserId()).isEqualTo(3L);
        assertThat(log.getUsername()).isEqualTo("admin_3");
        assertThat(log.getDurationMs()).isNotNull();
    }

    @Test
    void nonAdminEndpointIsSkippedByDefault() throws Throwable {
        bindRequest("POST", "/api/v1/tourist/sessions/1/message");

        invoke(PublicFixture.class, publicFixture, "sendMessage");

        // 游客每问一句都记一条，审计表会被淹掉，真正的后台动作反而找不到
        verify(operationLogService, never()).record(any());
    }

    @Test
    void annotationPullsNonAdminEndpointIntoScope() throws Throwable {
        bindRequest("POST", "/api/v1/system/auth/admin/login");

        invoke(PublicFixture.class, publicFixture, "adminLogin");

        SysOperationLog log = captureRecorded();
        assertThat(log.getModule()).isEqualTo("认证");
        assertThat(log.getAction()).isEqualTo("管理员登录");
    }

    @Test
    void ignoreFlagSuppressesRecording() throws Throwable {
        bindRequest("POST", "/api/v1/admin/roles");

        invoke(AdminFixture.class, adminFixture, "ignored");

        verify(operationLogService, never()).record(any());
    }

    @Test
    void switchOffStopsRecording() throws Throwable {
        when(operationLogService.enabled()).thenReturn(false);
        bindRequest("POST", "/api/v1/admin/roles");

        assertThat(invoke(AdminFixture.class, adminFixture, "createRole")).isEqualTo("proceeded");

        verify(operationLogService, never()).record(any());
    }

    @Test
    void missingRequestContextSkipsUnannotatedMethod() throws Throwable {
        // 定时任务里调到同一个方法时没有 HTTP 上下文，无从审计
        invoke(AdminFixture.class, adminFixture, "createRole");

        verify(operationLogService, never()).record(any());
    }

    // ========== 失败记录 ==========

    @Test
    void businessFailureIsRecordedAndRethrown() {
        bindRequest("DELETE", "/api/v1/admin/roles/1");
        authenticateAdmin(3L, "admin_3");

        assertThatThrownBy(() -> invokeThrowing(AdminFixture.class, adminFixture, "createRole",
            new BusinessException(ErrorCode.FORBIDDEN)))
            .isInstanceOf(BusinessException.class);

        SysOperationLog log = captureRecorded();
        // "谁试图删了不该删的东西"比成功记录更有审计价值
        assertThat(log.getStatus()).isEqualTo("FAILURE");
        assertThat(log.getErrorMsg()).startsWith("FORBIDDEN:");
    }

    @Test
    void unexpectedFailureRecordsOnlyExceptionType() {
        bindRequest("POST", "/api/v1/admin/roles");

        assertThatThrownBy(() -> invokeThrowing(AdminFixture.class, adminFixture, "createRole",
            new IllegalStateException("数据库连接串 jdbc:mysql://user:pwd@host")))
            .isInstanceOf(IllegalStateException.class);

        SysOperationLog log = captureRecorded();
        // 异常消息可能含敏感细节，不整条落库
        assertThat(log.getErrorMsg()).isEqualTo("IllegalStateException");
        assertThat(log.getErrorMsg()).doesNotContain("pwd");
    }

    // ========== 模块与动作推导 ==========

    @Test
    void moduleFallsBackToSwaggerTagThenClassName() throws Throwable {
        bindRequest("PUT", "/api/v1/admin/things/1");

        invoke(PlainFixture.class, plainFixture, "updateSomething");

        SysOperationLog log = captureRecorded();
        assertThat(log.getModule()).isEqualTo("PlainFixture");
        assertThat(log.getAction()).isEqualTo("更新");
    }

    @Test
    void actionFallsBackToHttpMethodWhenNamePrefixUnknown() throws Throwable {
        bindRequest("PATCH", "/api/v1/admin/roles/1/users");

        invoke(AdminFixture.class, adminFixture, "assign");

        assertThat(captureRecorded().getAction()).isEqualTo("变更");
    }

    @Test
    void annotationOverridesDerivedNamesAndCanDropParams() throws Throwable {
        bindRequest("POST", "/api/v1/admin/roles");

        invoke(AdminFixture.class, adminFixture, "annotated");

        SysOperationLog log = captureRecorded();
        assertThat(log.getModule()).isEqualTo("自定义模块");
        assertThat(log.getAction()).isEqualTo("自定义动作");
        assertThat(log.getParams()).isNull();
        verify(operationLogService, never()).sanitizeParams(any());
    }

    // ========== 客户端 IP ==========

    @Test
    void forwardedForTakesFirstHopAsClientIp() throws Throwable {
        MockHttpServletRequest request = bindRequest("POST", "/api/v1/admin/roles");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1, 10.0.0.2");

        invoke(AdminFixture.class, adminFixture, "createRole");

        assertThat(captureRecorded().getIp()).isEqualTo("203.0.113.9");
    }

    @Test
    void realIpHeaderIsUsedWhenForwardedForAbsent() throws Throwable {
        MockHttpServletRequest request = bindRequest("POST", "/api/v1/admin/roles");
        request.addHeader("X-Real-IP", "203.0.113.20");

        invoke(AdminFixture.class, adminFixture, "createRole");

        assertThat(captureRecorded().getIp()).isEqualTo("203.0.113.20");
    }

    @Test
    void remoteAddrIsUsedWhenNoProxyHeaders() throws Throwable {
        MockHttpServletRequest request = bindRequest("POST", "/api/v1/admin/roles");
        request.setRemoteAddr("192.168.1.7");

        invoke(AdminFixture.class, adminFixture, "createRole");

        assertThat(captureRecorded().getIp()).isEqualTo("192.168.1.7");
    }

    // ========== 容错 ==========

    @Test
    void recordingFailureDoesNotBreakBusiness() throws Throwable {
        bindRequest("POST", "/api/v1/admin/roles");
        when(operationLogService.sanitizeParams(any())).thenThrow(new RuntimeException("boom"));

        // 日志出问题不能让管理员的正常操作跟着失败
        assertThat(invoke(AdminFixture.class, adminFixture, "createRole")).isEqualTo("proceeded");
    }

    // ========== 辅助方法 ==========

    private SysOperationLog captureRecorded() {
        ArgumentCaptor<SysOperationLog> captor = ArgumentCaptor.forClass(SysOperationLog.class);
        verify(operationLogService).record(captor.capture());
        return captor.getValue();
    }

    private MockHttpServletRequest bindRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private void authenticateAdmin(Long userId, String subject) {
        AuthPrincipal principal = new AuthPrincipal(subject, "ADMIN", null, false, userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Object invoke(Class<?> targetClass, Object target, String methodName) throws Throwable {
        return aspect.around(joinPoint(targetClass, target, methodName, null));
    }

    private Object invokeThrowing(Class<?> targetClass, Object target, String methodName,
                                  Throwable error) throws Throwable {
        return aspect.around(joinPoint(targetClass, target, methodName, error));
    }

    private ProceedingJoinPoint joinPoint(Class<?> targetClass, Object target, String methodName,
                                          Throwable error) throws Throwable {
        Method method = targetClass.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(target);
        when(point.getArgs()).thenReturn(new Object[]{"arg"});
        if (error == null) {
            when(point.proceed()).thenReturn("proceeded");
        } else {
            when(point.proceed()).thenThrow(error);
        }
        return point;
    }
}
