package com.huohuo.scenicaiguide.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档实体
 */
@Data
@TableName("qa_knowledge_doc")
public class QaKnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String title;

    private String fileName;

    private String fileUrl;

    private String docType;

    private String summary;

    private String parseStatus;

    private String vectorStatus;

    private Integer chunkCount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
