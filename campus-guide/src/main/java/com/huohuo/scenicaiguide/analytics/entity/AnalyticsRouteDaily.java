package com.huohuo.scenicaiguide.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 路线日统计实体
 */
@Data
@TableName("analytics_route_daily")
public class AnalyticsRouteDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private LocalDate statDate;

    private String routeId;

    private String routeName;

    private String fromName;

    private String toName;

    private Integer useCount;

    private Integer completeCount;

    private Integer exitCount;

    private Integer avgDurationMin;

    private Integer heatLevel;

    private String displayPath;

    private Integer displayWidth;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
