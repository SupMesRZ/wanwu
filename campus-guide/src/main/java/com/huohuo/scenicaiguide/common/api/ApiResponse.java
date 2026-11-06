package com.huohuo.scenicaiguide.common.api;

import com.huohuo.scenicaiguide.common.enums.ErrorCode;

/**
 * 统一接口响应对象
 *
 * @param code    业务响应码，成功固定为 OK
 * @param message 响应描述
 * @param data    响应数据
 * @param <T>     响应数据类型
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId
) {

    public ApiResponse(String code, String message, T data) {
        this(code, message, data, null);
    }

    /**
     * 构造成功响应
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, null);
    }

    /**
     * 构造无数据的成功响应
     *
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应
     *
     * @param code    业务响应码
     * @param message 响应描述
     * @param <T>     响应数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    /**
     * 通过错误码枚举构造失败响应
     *
     * @param errorCode 错误码枚举
     * @param <T>       响应数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.name(), errorCode.getMessage(), null, null);
    }
}
