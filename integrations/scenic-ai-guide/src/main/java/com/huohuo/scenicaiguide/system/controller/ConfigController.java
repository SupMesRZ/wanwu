package com.huohuo.scenicaiguide.system.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.system.dto.request.ConfigUpdateRequest;
import com.huohuo.scenicaiguide.system.dto.response.ConfigDomainResponse;
import com.huohuo.scenicaiguide.system.dto.response.ConfigItemResponse;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import com.huohuo.scenicaiguide.system.service.ConfigAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 全局配置管理接口
 *
 * <p>管理后台维护系统运行时配置，修改后实时生效，无需重启应用
 */
@Tag(name = "全局配置管理", description = "系统配置查询与热更新")
@RestController
@RequestMapping("/api/v1/admin/configs")
@RequireAdmin
@RequirePermission(PermissionCode.CONFIG_READ)
public class ConfigController {

    private final ConfigAdminService configAdminService;

    @Autowired
    public ConfigController(ConfigAdminService configAdminService) {
        this.configAdminService = configAdminService;
    }

    /**
     * 查询所有配置
     */
    @Operation(summary = "查询所有配置", description = "返回全部系统配置，按分类排序")
    @GetMapping
    public ApiResponse<List<ConfigItemResponse>> listAll() {
        return ApiResponse.ok(configAdminService.listAll());
    }

    /**
     * 查询所有配置域
     */
    @Operation(summary = "查询所有配置域", description = "返回系统与业务域配置分组")
    @GetMapping("/domains")
    public ApiResponse<List<ConfigDomainResponse>> listDomains() {
        return ApiResponse.ok(configAdminService.listDomains());
    }

    /**
     * 查询指定配置域的配置
     */
    @Operation(summary = "查询指定配置域的配置", description = "domain：system/scenic/qa/route/tourist/digitalhuman/analytics")
    @GetMapping("/domains/{domain}")
    public ApiResponse<List<ConfigItemResponse>> listByDomain(
            @Parameter(description = "配置域") @PathVariable String domain) {
        return ApiResponse.ok(configAdminService.listByDomain(domain));
    }

    /**
     * 查询指定配置域下的单个配置
     */
    @Operation(summary = "查询指定配置域下的单个配置")
    @GetMapping("/domains/{domain}/{key}")
    public ApiResponse<ConfigItemResponse> getByDomain(
            @Parameter(description = "配置域") @PathVariable String domain,
            @Parameter(description = "配置键，可省略域前缀") @PathVariable String key) {
        return ApiResponse.ok(configAdminService.getByDomain(domain, key));
    }

    /**
     * 更新指定配置域下的单个配置
     */
    @Operation(summary = "更新指定配置域下的单个配置", description = "修改后自动刷新 Redis 缓存，实时生效")
    @PutMapping("/domains/{domain}/{key}")
    @RequirePermission(PermissionCode.CONFIG_WRITE)
    public ApiResponse<Void> updateByDomain(
            @Parameter(description = "配置域") @PathVariable String domain,
            @Parameter(description = "配置键，可省略域前缀") @PathVariable String key,
            @Valid @RequestBody ConfigUpdateRequest request) {
        configAdminService.updateByDomain(domain, key, request.getValue(), request.getDescription());
        return ApiResponse.ok();
    }

    /**
     * 批量更新指定配置域下的配置
     */
    @Operation(summary = "批量更新指定配置域下的配置")
    @PutMapping("/domains/{domain}/batch")
    @RequirePermission(PermissionCode.CONFIG_WRITE)
    public ApiResponse<Void> batchUpdateByDomain(
            @Parameter(description = "配置域") @PathVariable String domain,
            @Valid @RequestBody ConfigUpdateRequest.Batch request) {
        configAdminService.batchUpdateByDomain(domain, request.getConfigs());
        return ApiResponse.ok();
    }
}
