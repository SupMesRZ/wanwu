package com.huohuo.scenicaiguide.tourist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tourist_location_log")
public class TouristLocationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer accuracyMeter;

    private BigDecimal speedMeterSecond;

    private LocalDateTime reportedAt;

    private Integer accepted;

    private String rejectReason;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
