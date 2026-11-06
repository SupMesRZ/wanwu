package com.huohuo.scenicaiguide.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 讲解词实体
 */
@Data
@TableName("qa_guide_script")
public class QaGuideScript {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String title;

    private String content;

    private String language;

    private Integer priority;

    private Long audioAssetId;

    private Long videoAssetId;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
