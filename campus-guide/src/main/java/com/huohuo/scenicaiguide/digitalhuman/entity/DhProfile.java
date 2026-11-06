package com.huohuo.scenicaiguide.digitalhuman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dh_profile")
public class DhProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenicAreaId;

    private String name;

    private String avatarUrl;

    private String personaPrompt;

    private String welcomeText;

    private String provider;

    private String voiceCode;

    private String outputFormat;

    private Integer sampleRate;

    private Integer speechRate;

    private Integer pitchRate;

    private Integer volume;

    private String actionIdleUrl;

    private String actionListeningUrl;

    private String actionThinkingUrl;

    private String actionSpeakingUrl;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
