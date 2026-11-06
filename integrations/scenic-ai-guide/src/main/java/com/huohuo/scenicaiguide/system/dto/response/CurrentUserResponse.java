package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 当前登录管理员的身份与权限
 *
 * <p>权限集随请求实时下发，不放进 Token，前端每次进入后台重新拉取即可拿到最新授权
 */
@Schema(description = "当前登录管理员信息")
@Data
public class CurrentUserResponse {

    private Long id;

    private String username;

    private String nickname;

    @Schema(description = "是否超级管理员，为 true 时拥有全部权限")
    private Boolean isRoot;

    @Schema(description = "已分配的角色名称")
    private List<String> roleNames;

    @Schema(description = "持有的权限码，用于控制菜单与按钮可见性")
    private List<String> permissions;
}
