package com.huohuo.scenicaiguide.system.security;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.entity.SysOperationLog;
import com.huohuo.scenicaiguide.system.service.OperationLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 操作日志切面
 *
 * <p>采用"自动兜底 + 注解增强"：默认捕获 {@code /api/v1/admin/**} 下的全部非 GET 请求，
 * {@link OperationLog} 只用来补中文模块名与动作名。纯注解式漏标一个接口就是审计空洞，
 * 而审计的价值恰恰建立在"没有例外"上
 *
 * <p>切面顺序刻意排在 {@link AuthAspect} 外层（后者未标 {@code @Order}，默认最低优先级）：
 * 这样越权被拒的请求会以异常形式经过本切面，能记成 FAILURE。
 * "谁试图删了不该删的东西"比成功记录更有审计价值，漏掉它等于只审计守规矩的人
 */
@Aspect
@Component
@Order(1)
public class OperationLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final String ADMIN_URI_PREFIX = "/api/v1/admin/";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILURE = "FAILURE";

    /**
     * 方法名前缀 → 中文动作，未命中时退回按 HTTP 方法推导
     */
    private static final Map<String, String> ACTION_BY_METHOD_PREFIX = Map.ofEntries(
        Map.entry("create", "新增"),
        Map.entry("add", "新增"),
        Map.entry("save", "保存"),
        Map.entry("update", "更新"),
        Map.entry("edit", "编辑"),
        Map.entry("modify", "修改"),
        Map.entry("delete", "删除"),
        Map.entry("remove", "删除"),
        Map.entry("publish", "发布"),
        Map.entry("import", "导入"),
        Map.entry("export", "导出"),
        Map.entry("upload", "上传"),
        Map.entry("reset", "重置"),
        Map.entry("enable", "启用"),
        Map.entry("disable", "停用"),
        Map.entry("login", "登录"),
        Map.entry("logout", "登出"));

    private static final Map<String, String> ACTION_BY_HTTP_METHOD = Map.of(
        "POST", "新增",
        "PUT", "更新",
        "PATCH", "变更",
        "DELETE", "删除");

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping) "
              + "|| @annotation(org.springframework.web.bind.annotation.PutMapping) "
              + "|| @annotation(org.springframework.web.bind.annotation.PatchMapping) "
              + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping) "
              + "|| @annotation(com.huohuo.scenicaiguide.system.security.OperationLog)")
    public void writeOperationPointcut() {
    }

    @Around("writeOperationPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        Class<?> targetClass = point.getTarget().getClass();
        OperationLog annotation = resolveAnnotation(method, targetClass);

        HttpServletRequest request = currentRequest();
        if (!shouldRecord(annotation, request)) {
            return point.proceed();
        }

        long startedAt = System.currentTimeMillis();
        Object[] args = point.getArgs();
        try {
            Object result = point.proceed();
            safeRecord(annotation, method, targetClass, request, args, startedAt, null);
            return result;
        } catch (Throwable error) {
            safeRecord(annotation, method, targetClass, request, args, startedAt, error);
            throw error;
        }
    }

    /**
     * 记录范围：显式标注的接口一律记；未标注的只记后台写接口
     */
    private boolean shouldRecord(OperationLog annotation, HttpServletRequest request) {
        if (annotation != null && annotation.ignore()) {
            return false;
        }
        if (!operationLogService.enabled()) {
            return false;
        }
        if (annotation != null) {
            return true;
        }
        // 拿不到请求上下文说明不是 HTTP 调用（如定时任务内部调用），无从审计
        return request != null && request.getRequestURI() != null
            && request.getRequestURI().startsWith(ADMIN_URI_PREFIX);
    }

    /**
     * 组装并写入，整个过程不允许影响业务结果
     */
    private void safeRecord(OperationLog annotation, Method method, Class<?> targetClass,
                            HttpServletRequest request, Object[] args, long startedAt, Throwable error) {
        try {
            SysOperationLog log = new SysOperationLog();
            log.setModule(resolveModule(annotation, targetClass));
            log.setAction(resolveAction(annotation, method, request));
            log.setHttpMethod(request != null ? request.getMethod() : null);
            log.setUri(request != null ? request.getRequestURI() : null);
            log.setIp(resolveIp(request));
            log.setDurationMs(System.currentTimeMillis() - startedAt);
            log.setStatus(error == null ? STATUS_SUCCESS : STATUS_FAILURE);
            log.setErrorMsg(error == null ? null : describeError(error));

            if (annotation == null || !annotation.withoutParams()) {
                log.setParams(operationLogService.sanitizeParams(args));
            }

            fillOperator(log);
            operationLogService.record(log);
        } catch (Exception e) {
            LOGGER.warn("操作日志组装失败，已跳过：{}", e.getMessage());
        }
    }

    private void fillOperator(SysOperationLog log) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            log.setUserId(principal.userId());
            log.setUsername(operationLogService.resolveUsername(principal.userId(), principal.subject()));
        }
    }

    // ========== 解析 ==========

    private OperationLog resolveAnnotation(Method method, Class<?> clazz) {
        OperationLog onMethod = method.getAnnotation(OperationLog.class);
        return onMethod != null ? onMethod : clazz.getAnnotation(OperationLog.class);
    }

    /**
     * 模块名优先取注解，其次取 Swagger 的 {@code @Tag}——每个 Controller 都已经写了中文名，
     * 没必要为审计再标一遍
     */
    private String resolveModule(OperationLog annotation, Class<?> targetClass) {
        if (annotation != null && !annotation.module().isBlank()) {
            return annotation.module();
        }
        Tag tag = targetClass.getAnnotation(Tag.class);
        if (tag != null && !tag.name().isBlank()) {
            return tag.name();
        }
        return targetClass.getSimpleName().replace("Controller", "");
    }

    private String resolveAction(OperationLog annotation, Method method, HttpServletRequest request) {
        if (annotation != null && !annotation.action().isBlank()) {
            return annotation.action();
        }
        String methodName = method.getName();
        for (Map.Entry<String, String> entry : ACTION_BY_METHOD_PREFIX.entrySet()) {
            if (methodName.toLowerCase(java.util.Locale.ROOT).startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        String httpMethod = request != null ? request.getMethod() : null;
        return ACTION_BY_HTTP_METHOD.getOrDefault(httpMethod, methodName);
    }

    /**
     * 业务异常记录错误码，其余异常记录类名——异常消息可能含敏感细节，不整条落库
     */
    private String describeError(Throwable error) {
        if (error instanceof BusinessException business) {
            return business.getCode() + ": " + business.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 代理链取第一个，那才是真实客户端
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
