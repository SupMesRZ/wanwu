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
 * AI 调用消耗日统计实体
 */
@Data
@TableName("analytics_ai_usage_daily")
public class AnalyticsAiUsageDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private LocalDate statDate;

    private Integer chatCallCount;

    private Integer ragSearchCount;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    private Integer ttsCharCount;

    private BigDecimal asrDurationSeconds;

    private Integer ttsCacheHitCount;

    private Integer ttsCacheRequestCount;

    private Integer p95FirstTokenMs;

    private BigDecimal estimatedCost;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
