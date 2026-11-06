package com.huohuo.scenicaiguide.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运营概览日统计实体
 */
@Data
@TableName("analytics_overview_daily")
public class AnalyticsOverviewDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private LocalDate statDate;

    private Integer visitorCount;

    private Integer sessionCount;

    private Integer activeVisitorCount;

    private Integer messageCount;

    private Integer fenceTriggerCount;

    private Integer routeRequestCount;

    private Integer routeCompleteCount;

    private Integer avgResponseMs;

    private Integer positiveMessageCount;

    private Integer neutralMessageCount;

    private Integer negativeMessageCount;

    private Integer complaintMessageCount;

    private BigDecimal estimatedCost;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
