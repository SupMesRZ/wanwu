package com.huohuo.scenicaiguide.system.config;

import com.huohuo.scenicaiguide.system.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作日志过期清理任务
 * <p>
 * 审计日志只追加，不清理就会无限增长。每日凌晨按 {@code ops-operation-log-retention-days} 物理删除，
 * 保留天数配成 0 表示永久保留
 * <p>
 * 清理失败只告警不重试：下一次调度会重新覆盖同一批过期数据，没有补偿的必要
 */
@Component
public class OperationLogCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogCleanupTask.class);

    private final OperationLogService operationLogService;

    public OperationLogCleanupTask(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Scheduled(cron = "0 20 3 * * ?")
    public void purge() {
        try {
            operationLogService.purgeExpired();
        } catch (Exception e) {
            LOGGER.warn("操作日志定时清理失败：{}", e.getMessage());
        }
    }
}
