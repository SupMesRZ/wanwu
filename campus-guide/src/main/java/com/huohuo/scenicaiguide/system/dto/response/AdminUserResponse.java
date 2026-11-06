package com.huohuo.scenicaiguide.system.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员账号响应
 *
 * <p>不包含密码哈希，任何情况下都不对外返回
 */
@Data
public class AdminUserResponse {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    /** 是否超级管理员 */
    private Boolean isRoot;

    /** 启用状态：1 启用，0 禁用 */
    private Integer status;

    /** 已分配的角色ID */
    private List<Long> roleIds;

    /** 已分配的角色名称，供列表直接展示 */
    private List<String> roleNames;

    private String lastLoginIp;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
