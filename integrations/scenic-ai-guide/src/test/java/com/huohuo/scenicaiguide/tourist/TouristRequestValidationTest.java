package com.huohuo.scenicaiguide.tourist;

import com.huohuo.scenicaiguide.tourist.dto.request.GuideMessageRequest;
import com.huohuo.scenicaiguide.tourist.dto.request.LocationReportRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TouristRequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void guideMessageShouldRejectOversizedNaturalLanguageInput() {
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("x".repeat(1001));

        assertThat(VALIDATOR.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("content");
    }

    @Test
    void locationShouldRejectOutOfRangeCoordinatesAndNegativeMetrics() {
        LocationReportRequest request = new LocationReportRequest();
        request.setLongitude(new BigDecimal("180.1"));
        request.setLatitude(new BigDecimal("-90.1"));
        request.setAccuracyMeter(-1);
        request.setSpeedMeterSecond(new BigDecimal("-0.1"));

        assertThat(VALIDATOR.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("longitude", "latitude", "accuracyMeter", "speedMeterSecond");
    }
}
