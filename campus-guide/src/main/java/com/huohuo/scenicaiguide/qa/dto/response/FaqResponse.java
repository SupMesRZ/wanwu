package com.huohuo.scenicaiguide.qa.dto.response;

import com.huohuo.scenicaiguide.qa.entity.QaFaq;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaqResponse {

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

    public static FaqResponse from(QaFaq entity) {
        FaqResponse response = new FaqResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setQuestion(entity.getQuestion());
        response.setAnswer(entity.getAnswer());
        response.setCategory(entity.getCategory());
        response.setTags(entity.getTags());
        response.setHitCount(entity.getHitCount());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
