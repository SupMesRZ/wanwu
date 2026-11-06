package com.huohuo.scenicaiguide.system.dto.response;

import com.huohuo.scenicaiguide.system.entity.SysOperationLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志响应
 */
@Data
public class OperationLogResponse {

    private Long id;

    private Long userId;

    private String username;

    private String module;

    private String action;

    private String httpMethod;

    private String uri;

    private String params;

    private String ip;

    /**
     * SUCCESS / FAILURE
     */
    private String status;

    private String errorMsg;

    private Long durationMs;

    private LocalDateTime createdAt;

    public static OperationLogResponse from(SysOperationLog entity) {
        OperationLogResponse response = new OperationLogResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setUsername(entity.getUsername());
        response.setModule(entity.getModule());
        response.setAction(entity.getAction());
        response.setHttpMethod(entity.getHttpMethod());
        response.setUri(entity.getUri());
        response.setParams(entity.getParams());
        response.setIp(entity.getIp());
        response.setStatus(entity.getStatus());
        response.setErrorMsg(entity.getErrorMsg());
        response.setDurationMs(entity.getDurationMs());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
