package com.huohuo.scenicaiguide.qa.dto.response;

import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeDoc;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocResponse {

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

    public static KnowledgeDocResponse from(QaKnowledgeDoc entity) {
        KnowledgeDocResponse response = new KnowledgeDocResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setTitle(entity.getTitle());
        response.setFileName(entity.getFileName());
        response.setFileUrl(entity.getFileUrl());
        response.setDocType(entity.getDocType());
        response.setSummary(entity.getSummary());
        response.setParseStatus(entity.getParseStatus());
        response.setVectorStatus(entity.getVectorStatus());
        response.setChunkCount(entity.getChunkCount());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
