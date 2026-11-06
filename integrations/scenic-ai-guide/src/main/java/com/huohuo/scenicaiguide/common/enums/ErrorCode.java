package com.huohuo.scenicaiguide.common.enums;

import lombok.Getter;

/**
 * 业务错误码枚举
 */
@Getter
public enum ErrorCode {

    // 系统级错误
    SYSTEM_ERROR("系统内部错误"),
    SERVICE_UNAVAILABLE("服务暂不可用"),

    // 参数与请求错误
    BAD_REQUEST("请求参数错误"),
    PARAM_MISSING("缺少必要参数"),
    PARAM_INVALID("参数格式不正确"),

    // 认证授权错误
    UNAUTHORIZED("未登录或令牌无效"),
    FORBIDDEN("无权限访问该资源"),
    TOKEN_EXPIRED("登录已过期，请重新登录"),

    // 资源错误
    NOT_FOUND("资源不存在"),
    RESOURCE_ALREADY_EXISTS("资源已存在"),

    // 业务逻辑错误
    BUSINESS_ERROR("业务逻辑错误"),
    OPERATION_NOT_ALLOWED("当前状态不允许该操作"),

    // 外部服务错误
    THIRD_PARTY_ERROR("外部服务调用失败"),

    // 限流与并发
    RATE_LIMIT("请求过于频繁，请稍后再试"),

    // 文件相关
    FILE_UPLOAD_ERROR("文件上传失败"),
    FILE_TOO_LARGE("文件大小超出限制"),
    FILE_TYPE_NOT_SUPPORTED("不支持的文件类型");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }
}
