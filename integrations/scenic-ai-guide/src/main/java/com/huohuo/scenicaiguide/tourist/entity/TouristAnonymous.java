package com.huohuo.scenicaiguide.tourist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 匿名访客实体
 */
@Data
@TableName("tourist_anonymous")
public class TouristAnonymous {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String status;

    private String banReason;

    private LocalDateTime banUntil;

    private BigDecimal lastLocationLat;

    private BigDecimal lastLocationLng;

    private LocalDateTime lastLocationAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
