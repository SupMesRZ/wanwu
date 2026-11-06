package com.huohuo.scenicaiguide.qa.dto.response;

import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import lombok.Data;

@Data
public class QaAnswerPreviewResponse {

    private String answer;

    private String sourceType;

    private String evidence;

    private boolean safeFallback;

    public static QaAnswerPreviewResponse from(QaGuideAnswerService.AnswerResult result) {
        QaAnswerPreviewResponse response = new QaAnswerPreviewResponse();
        response.setAnswer(result.answer());
        response.setSourceType(result.sourceType());
        response.setEvidence(result.evidence());
        response.setSafeFallback("SAFE_FALLBACK".equals(result.sourceType()));
        return response;
    }
}
