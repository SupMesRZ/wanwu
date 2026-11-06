package com.huohuo.scenicaiguide.scenic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地图工作台草稿实体
 */
@Data
@TableName("scenic_map_draft")
public class ScenicMapDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String name;

    private String status;

    private String payload;

    private LocalDateTime lastSavedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableField(exist = false)
    private Long onlineScenicAreaId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
