package com.huohuo.scenicaiguide.scenic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务点实体
 */
@Data
@TableName("scenic_facility")
public class ScenicFacility {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String name;

    private String facilityType;

    private String description;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
