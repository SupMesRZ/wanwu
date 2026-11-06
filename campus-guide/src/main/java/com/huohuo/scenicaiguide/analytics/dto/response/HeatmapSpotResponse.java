package com.huohuo.scenicaiguide.analytics.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HeatmapSpotResponse {

    private Integer rank;

    private String area;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Long visitors;

    private Integer avgAccuracyMeter;

    private String crowdLevel;

    private LocalDateTime firstReportedAt;

    private LocalDateTime latestReportedAt;
}
