package com.huohuo.scenicaiguide.tourist.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导览会话历史消息
 *
 * <p>用于游客端刷新或重新进入会话时恢复对话记录，不含音频数据
 * 音频体积大且有时效性，历史消息只回放文字
 */
@Data
public class GuideHistoryMessageResponse {

    private Long id;

    /** 发送方：TOURIST/AGENT/SYSTEM */
    private String sender;

    /** 消息类型：TEXT/ROUTE/GUIDE/TTS/ERROR */
    private String messageType;

    private String content;

    /** 依据和工具调用摘要 */
    private String evidence;

    /** 数字人动作状态 */
    private String actionState;

    private LocalDateTime createdAt;
}
