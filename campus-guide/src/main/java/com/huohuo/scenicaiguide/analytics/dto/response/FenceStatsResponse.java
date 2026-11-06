package com.huohuo.scenicaiguide.analytics.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FenceStatsResponse {

    private Integer rank;

    private String name;

    private String spotId;

    private Long triggerCount;

    private Long broadcastCount;

    private String broadcastRate;

    private Long repeatCount;

    private String repeatRate;

    private LocalDateTime latestTriggeredAt;
}
