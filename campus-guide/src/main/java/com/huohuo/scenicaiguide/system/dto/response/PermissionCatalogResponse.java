package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 权限清单响应
 * <p>
 * 权限码由后端枚举定义，前端只负责渲染勾选，不能自行新增
 */
@Schema(description = "按模块分组的权限清单")
@Data
public class PermissionCatalogResponse {

    @Schema(description = "模块名称", example = "校园资产")
    private String module;

    @Schema(description = "该模块下的权限项")
    private List<PermissionItem> permissions;

    @Schema(description = "权限项")
    @Data
    public static class PermissionItem {

        @Schema(description = "权限码", example = "scenic:read")
        private String code;

        @Schema(description = "权限说明", example = "查看校区、地点、设施、地图草稿")
        private String description;
    }
}
