package com.huohuo.scenicaiguide.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 景点日统计实体
 */
@Data
@TableName("analytics_spot_daily")
public class AnalyticsSpotDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private LocalDate statDate;

    private String spotId;

    private String spotName;

    private Integer visitorCount;

    private Integer fenceTriggerCount;

    private Integer guidePlayCount;

    private Integer avgStaySeconds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
