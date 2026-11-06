package com.huohuo.scenicaiguide.scenic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签实体
 * <p>
 * 全局共享的分类词表，不挂景区。地图草稿里的景点只存 {@code code}，
 * 名称与颜色在展示时按 code 回填，改词表名不破坏已发布地图
 */
@Data
@TableName("scenic_tag")
public class ScenicTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String color;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
