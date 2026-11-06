package com.huohuo.scenicaiguide.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("route_ai_route")
public class RouteAiRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long scenicAreaId;

    private Long sourceDraftId;

    private String name;

    private String routeType;

    private String preferenceJson;

    private String nodeJson;

    private String segmentJson;

    private String recommendationReason;

    private Integer estimatedDurationMin;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
