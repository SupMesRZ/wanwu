package com.huohuo.scenicaiguide.common.trace;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import org.springframework.lang.NonNull;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * TraceId 响应注入
 * <p>
 * 在每个 ApiResponse 响应中自动填入 traceId
 */
@ControllerAdvice
public class TraceIdResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body instanceof ApiResponse) {
            ApiResponse<Object> apiResponse = (ApiResponse<Object>) body;
            return new ApiResponse<>(apiResponse.code(), apiResponse.message(),
                apiResponse.data(), MDC.get("traceId"));
        }
        return body;
    }
}
