package com.huohuo.scenicaiguide.common.exception;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 横切所有 Controller，将异常统一包装为 ApiResponse，业务代码无需手写 try-catch
 * 三层处理策略：业务异常 → 参数校验异常 → 未预期异常兜底
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 — 预期内的错误，如"景点不存在""当前状态不允许操作"
     * 使用 warn 级别，不打印堆栈，避免日志噪音
     * 根据错误码映射对应的 HTTP 状态码
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[{}] Business error: {}", request.getRequestURI(), e.getMessage());
        HttpStatus status = resolveHttpStatus(e.getCode());
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    private HttpStatus resolveHttpStatus(String code) {
        try {
            return switch (ErrorCode.valueOf(code)) {
                case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case BAD_REQUEST, PARAM_MISSING, PARAM_INVALID -> HttpStatus.BAD_REQUEST;
                case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
                default -> HttpStatus.OK;
            };
        } catch (IllegalArgumentException ex) {
            return HttpStatus.OK;
        }
    }

    /**
     * 配合 Spring Validation（参数校验）
     * 参数校验失败 — @Valid / @Validated 触发，如字段为空、格式不符
     * 统一返回 BAD_REQUEST
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("[{}] Validation error: {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.fail(ErrorCode.BAD_REQUEST);
    }

    /**
     * 未预期异常兜底 — NPE、数组越界等未知问题
     * 使用 error 级别打印完整堆栈，便于排查；对外仅暴露 SYSTEM_ERROR，防止信息泄露
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[{}] System error", request.getRequestURI(), e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR);
    }
}
