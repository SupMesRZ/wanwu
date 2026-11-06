package com.huohuo.scenicaiguide.qa.dto.response;

import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeChunkResponse {

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

    public static KnowledgeChunkResponse from(QaKnowledgeChunk entity) {
        KnowledgeChunkResponse response = new KnowledgeChunkResponse();
        response.setId(entity.getId());
        response.setDocId(entity.getDocId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setChunkIndex(entity.getChunkIndex());
        response.setContent(entity.getContent());
        response.setVectorId(entity.getVectorId());
        response.setTokenCount(entity.getTokenCount());
        response.setHitCount(entity.getHitCount());
        response.setVectorStatus(entity.getVectorStatus());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
