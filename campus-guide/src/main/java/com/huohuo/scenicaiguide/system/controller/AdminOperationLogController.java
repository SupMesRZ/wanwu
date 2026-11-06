package com.huohuo.scenicaiguide.system.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.system.dto.response.OperationLogResponse;
import com.huohuo.scenicaiguide.system.security.OperationLog;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import com.huohuo.scenicaiguide.system.security.RequireRoot;
import com.huohuo.scenicaiguide.system.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志查询接口
 *
 * <p>日志只读不可改：没有编辑接口是有意的，能改的审计记录等于没有审计。
 * 唯一的写操作是按保留期清理，且限 root
 */
@Tag(name = "操作日志", description = "后台写操作的审计记录")
@RestController
@RequestMapping("/api/v1/admin/operation-logs")
@RequireAdmin
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    public AdminOperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "分页查询操作日志", description = "支持按操作人、模块、结果、时间范围筛选")
    @GetMapping
    @RequirePermission(PermissionCode.LOG_READ)
    public ApiResponse<PageResponse<OperationLogResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(operationLogService.page(
            keyword, module, status, startTime, endTime, page, pageSize));
    }

    @Operation(summary = "已出现的模块列表", description = "供查询页的模块下拉使用")
    @GetMapping("/modules")
    @RequirePermission(PermissionCode.LOG_READ)
    public ApiResponse<List<String>> modules() {
        return ApiResponse.ok(operationLogService.modules());
    }

    @Operation(summary = "查看当前保留天数")
    @GetMapping("/retention")
    @RequirePermission(PermissionCode.LOG_READ)
    public ApiResponse<Integer> retention() {
        return ApiResponse.ok(operationLogService.retentionDays());
    }

    @Operation(summary = "立即清理过期日志", description = "按配置的保留天数物理删除，正常由每日定时任务执行")
    @DeleteMapping("/expired")
    @RequireRoot
    @OperationLog(module = "操作日志", action = "手动清理过期日志")
    public ApiResponse<Integer> purgeExpired() {
        return ApiResponse.ok(operationLogService.purgeExpired());
    }
}
