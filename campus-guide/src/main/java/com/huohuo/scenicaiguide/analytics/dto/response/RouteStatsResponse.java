package com.huohuo.scenicaiguide.analytics.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RouteStatsResponse {

    private Integer rank;

    private String name;

    private Long selectedCount;

    private Long completedCount;

    private String completionRate;

    private Integer avgDurationMin;

    private LocalDateTime latestCreatedAt;
}
