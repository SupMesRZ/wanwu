package com.huohuo.scenicaiguide.tourist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tourist_fence_log")
public class TouristFenceLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String spotId;

    private String spotName;

    private String triggerState;

    private String triggerType;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer accuracyMeter;

    private String detail;

    private String broadcastStatus;

    private LocalDateTime reportedAt;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
