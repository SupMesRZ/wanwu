package com.huohuo.scenicaiguide.tourist.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GuideStreamEvent {

    private String type;

    private String delta;

    private String status;

    private GuideMessageResponse message;

    private String error;

    private Integer sequence;

    private String speechText;

    private String audioBase64;

    private String audioFormat;

    private Boolean cached;

    public static GuideStreamEvent delta(String content) {
        GuideStreamEvent event = new GuideStreamEvent();
        event.setType("delta");
        event.setDelta(content);
        return event;
    }

    public static GuideStreamEvent completed(GuideMessageResponse message) {
        GuideStreamEvent event = new GuideStreamEvent();
        event.setType("completed");
        event.setMessage(message);
        return event;
    }

    public static GuideStreamEvent status(String status) {
        GuideStreamEvent event = new GuideStreamEvent();
        event.setType("status");
        event.setStatus(status);
        return event;
    }

    public static GuideStreamEvent failed(String error) {
        GuideStreamEvent event = new GuideStreamEvent();
        event.setType("error");
        event.setError(error);
        return event;
    }

    public static GuideStreamEvent audio(int sequence,
                                         String speechText,
                                         String audioBase64,
                                         String audioFormat,
                                         boolean cached) {
        GuideStreamEvent event = new GuideStreamEvent();
        event.setType("audio");
        event.setSequence(sequence);
        event.setSpeechText(speechText);
        event.setAudioBase64(audioBase64);
        event.setAudioFormat(audioFormat);
        event.setCached(cached);
        return event;
    }

    public static GuideStreamEvent audioFailed(int sequence, String speechText, String error) {
        GuideStreamEvent event = new GuideStreamEvent();
        event.setType("audio_error");
        event.setSequence(sequence);
        event.setSpeechText(speechText);
        event.setError(error);
        return event;
    }
}
