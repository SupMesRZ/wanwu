package com.huohuo.scenicaiguide.scenic.dto.response;

import com.huohuo.scenicaiguide.scenic.entity.ScenicTag;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签响应
 */
@Data
public class ScenicTagResponse {

    private Long id;

    private String code;

    private String name;

    private String color;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;

    public static ScenicTagResponse from(ScenicTag tag) {
        ScenicTagResponse response = new ScenicTagResponse();
        response.setId(tag.getId());
        response.setCode(tag.getCode());
        response.setName(tag.getName());
        response.setColor(tag.getColor());
        response.setDescription(tag.getDescription());
        response.setStatus(tag.getStatus());
        response.setCreatedAt(tag.getCreatedAt());
        return response;
    }
}
