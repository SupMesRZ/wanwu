package com.huohuo.scenicaiguide.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAQ 实体
 */
@Data
@TableName("qa_faq")
public class QaFaq {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String question;

    private String answer;

    private String category;

    private String tags;

    private Integer hitCount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
