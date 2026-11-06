package com.huohuo.scenicaiguide.tourist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tourist_message")
public class TouristMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String sender;

    private String messageType;

    private String content;

    private String evidenceText;

    private String audioUrl;

    private String actionState;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
