package com.huohuo.scenicaiguide.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * <p>
 * 只追加不修改，因此没有 updated_at；也不参与全局逻辑删除——过期清理必须真删才能释放磁盘
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /**
     * 业务模块中文名，如"角色权限"
     */
    private String module;

    /**
     * 操作动作中文名，如"新增角色"
     */
    private String action;

    private String httpMethod;

    private String uri;

    /**
     * 请求参数，已脱敏并截断
     */
    private String params;

    private String ip;

    /**
     * SUCCESS / FAILURE
     */
    private String status;

    private String errorMsg;

    private Long durationMs;

    private LocalDateTime createdAt;
}
