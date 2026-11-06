package com.huohuo.scenicaiguide.tourist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tourist_guide_session")
public class TouristGuideSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionNo;

    private String visitorSubject;

    private Long scenicAreaId;

    private String status;

    private String preferenceJson;

    private String currentSpotId;

    private Long currentRouteId;

    private Integer routeNodeSeq;

    private BigDecimal lastLocationLat;

    private BigDecimal lastLocationLng;

    private LocalDateTime lastLocationAt;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
