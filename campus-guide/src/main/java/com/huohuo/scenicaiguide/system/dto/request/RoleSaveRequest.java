package com.huohuo.scenicaiguide.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色新增/编辑请求
 */
@Schema(description = "角色新增或编辑请求")
@Data
public class RoleSaveRequest {

    @Schema(description = "角色标识，新增必填，编辑时忽略", example = "content-editor")
    @Size(max = 50, message = "角色标识长度不能超过 50")
    private String code;

    @Schema(description = "角色名称", example = "内容编辑")
    @Size(max = 50, message = "角色名称长度不能超过 50")
    private String name;

    @Schema(description = "角色说明")
    @Size(max = 255, message = "角色说明长度不能超过 255")
    private String description;

    @Schema(description = "状态 1启用 0禁用", example = "1")
    private Integer status;

    @Schema(description = "权限码列表，null 表示不改动权限勾选")
    private List<String> permissions;
}
