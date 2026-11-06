package com.huohuo.scenicaiguide.digitalhuman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dh_tts_cache")
public class DhTtsCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String textHash;

    private String textPreview;

    private String voiceCode;

    private String outputFormat;

    private Integer sampleRate;

    private String audioUrl;

    private String audioBase64;

    private Integer hitCount;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
