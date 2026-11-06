package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色响应
 */
@Schema(description = "角色信息")
@Data
public class RoleResponse {

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色标识")
    private String code;

    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "是否内置角色，内置角色不可删除")
    private Boolean builtin;

    @Schema(description = "状态 1启用 0禁用")
    private Integer status;

    @Schema(description = "已勾选的权限码")
    private List<String> permissions;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
