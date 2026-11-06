package com.huohuo.scenicaiguide.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档切片实体
 */
@Data
@TableName("qa_knowledge_chunk")
public class QaKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Long scenicAreaId;

    private Integer chunkIndex;

    private String content;

    private String vectorId;

    private Integer tokenCount;

    private Integer hitCount;

    private String vectorStatus;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
